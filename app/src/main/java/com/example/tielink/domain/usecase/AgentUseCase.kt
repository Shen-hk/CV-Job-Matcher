package com.example.tielink.domain.usecase

import android.content.Context
import android.util.Log
import com.example.tielink.data.remote.AiProviderManager
import com.example.tielink.data.remote.LlmRequest
import com.example.tielink.data.remote.PromptRegistry
import com.example.tielink.data.remote.StreamEvent
import com.example.tielink.data.remote.dto.MessageFunctionCall
import com.example.tielink.data.remote.dto.Message
import com.example.tielink.data.remote.dto.MessageToolCall
import com.example.tielink.data.repository.AgentContextRepository
import com.example.tielink.data.repository.InterviewRepository
import com.example.tielink.data.repository.ResumeVersionRepository
import com.example.tielink.domain.model.AgentMessage
import com.example.tielink.domain.model.AgentMessageRole
import com.example.tielink.domain.model.AgentOutput
import com.example.tielink.domain.model.GreetingVersion
import com.example.tielink.domain.model.ResumeData
import com.example.tielink.domain.model.ResumeVersion
import com.example.tielink.domain.model.UiCard
import com.example.tielink.util.AgentWorkspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Agent 核心用例
 * 处理意图识别、工具调用、LLM 对话
 */
@Singleton
class AgentUseCase @Inject constructor(
    private val aiProviderManager: AiProviderManager,
    private val promptRegistry: PromptRegistry,
    private val agentContextRepository: AgentContextRepository,
    private val resumeVersionRepository: ResumeVersionRepository,
    private val agentToolCoordinator: AgentToolCoordinator,
    private val interviewRepository: InterviewRepository
) {
    companion object {
        private const val TAG = "AgentUseCase"
        private const val MAX_CONTEXT_MESSAGES = 20
        private const val MAX_TOOL_ROUNDS = 6
        private const val MAX_TOOL_CALLS_PER_ROUND = 4
        // Local preview switch for manually rendering all supported cards.
        private const val DEBUG_SHOW_ALL_CARDS = false
    }

    private data class AgentTurnPolicy(
        val allowedToolNames: Set<String>,
        val reason: String
    ) {
        fun isAllowed(toolName: String): Boolean = toolName in allowedToolNames
    }

    /**
     * 处理用户输入，返回 AgentOutput 流
     */
    fun process(
        userText: String,
        conversationHistory: List<com.example.tielink.domain.model.AgentMessage>,
        appContext: Context
    ): Flow<AgentOutput> = flow {
        if (DEBUG_SHOW_ALL_CARDS) {
            emitDebugCards()
            return@flow
        }
        val turnPolicy = withContext(Dispatchers.IO) { buildTurnPolicy(userText, conversationHistory) }
        val effectiveTurnPolicy = withContext(Dispatchers.IO) {
            preloadCurrentJdIfNeeded(userText, turnPolicy)
        }
        val systemPrompt = withContext(Dispatchers.IO) { buildSystemPrompt(appContext, effectiveTurnPolicy) }
        val messages = buildMessages(systemPrompt, conversationHistory, userText).toMutableList()
        val tools = agentToolCoordinator.definitions(effectiveTurnPolicy.allowedToolNames)
        val seenCalls = mutableSetOf<String>()
        var finalText = ""

        try {
            if (tools.isEmpty()) {
                emitStreamingTextResponse(
                    request = LlmRequest(
                        messages = messages,
                        temperature = 0.7,
                        maxTokens = 4096
                    ),
                    userText = userText,
                    appContext = appContext,
                    detail = "正在直接回答，不需要调用工具"
                )
                return@flow
            }

            for (round in 0 until MAX_TOOL_ROUNDS) {
                emit(
                    AgentOutput.ProcessPhase(
                        stage = com.example.tielink.domain.model.AgentProcessStage.THINKING,
                        title = "思考中",
                        detail = if (round == 0) "正在理解请求并选择可用工具" else "正在结合工具结果继续处理",
                        canCancel = true
                    )
                )

                val response = aiProviderManager.chatWithFallback(
                    LlmRequest(
                        messages = messages,
                        temperature = if (round == 0) 0.45 else 0.35,
                        maxTokens = 4096,
                        tools = tools,
                        toolChoice = if (tools.isEmpty()) "none" else "auto"
                    )
                )

                if (response.toolCalls.isEmpty()) {
                    finalText = response.content.trim()
                    if (finalText.isNotBlank()) {
                        emit(
                            AgentOutput.ProcessPhase(
                                stage = com.example.tielink.domain.model.AgentProcessStage.TEXT_GENERATION,
                                title = "文本生成中",
                                detail = "正在整理最终回复",
                                canCancel = true
                            )
                        )
                        emit(AgentOutput.StreamText(finalText))
                    }
                    break
                }

                val calls = response.toolCalls.take(MAX_TOOL_CALLS_PER_ROUND)
                val assistantToolCalls = calls.map { call ->
                    MessageToolCall(
                        id = call.id,
                        function = MessageFunctionCall(
                            name = call.name,
                            arguments = call.arguments
                        )
                    )
                }
                messages += Message(
                    role = "assistant",
                    content = response.content.ifBlank { null },
                    toolCalls = assistantToolCalls
                )

                for (call in calls) {
                    if (!effectiveTurnPolicy.isAllowed(call.name)) {
                        messages += Message(
                            role = "tool",
                            content = "本轮用户请求不适合调用 ${call.name}。请不要弹出卡片或执行无关工具，改用简洁文本直接回答。",
                            toolCallId = call.id,
                            name = call.name
                        )
                        continue
                    }

                    val signature = "${call.name}:${call.arguments}"
                    if (!seenCalls.add(signature)) {
                        messages += Message(
                            role = "tool",
                            content = "拒绝重复执行完全相同的工具调用，请根据已有结果继续回答。",
                            toolCallId = call.id,
                            name = call.name
                        )
                        continue
                    }

                    val description = agentToolCoordinator.descriptionFor(call.name)
                    emit(AgentOutput.ToolStart(call.name, description))
                    emit(
                        AgentOutput.ProcessPhase(
                            stage = if (call.name == "render_card") {
                                com.example.tielink.domain.model.AgentProcessStage.TEXT_GENERATION
                            } else {
                                com.example.tielink.domain.model.AgentProcessStage.RETRIEVING
                            },
                            title = if (call.name == "render_card") "卡片生成中" else "工具执行中",
                            detail = description,
                            sourceLabel = call.name,
                            canCancel = true
                        )
                    )

                    val result = withContext(Dispatchers.IO) {
                        agentToolCoordinator.execute(call, userText)
                    }
                    result.cards.forEach { emit(AgentOutput.ToolResult(it)) }
                    messages += Message(
                        role = "tool",
                        content = result.content,
                        toolCallId = call.id,
                        name = call.name
                    )
                }

                if (round == MAX_TOOL_ROUNDS - 1) {
                    finalText = "已经完成可执行的工具步骤；为避免循环，我先停在这里。"
                    emit(AgentOutput.StreamText(finalText))
                }
            }

            if (finalText.isNotBlank()) {
                withContext(Dispatchers.IO) {
                    handleMemoryExtraction(userText, finalText, appContext)
                }
            }
            emit(AgentOutput.Done)
        } catch (e: Exception) {
            Log.w(TAG, "原生工具调用不可用，回退到普通流式聊天: ${e.message}")
            try {
                emitStreamingTextResponse(
                    LlmRequest(
                        messages = buildMessages(systemPrompt, conversationHistory, userText),
                        temperature = 0.7,
                        maxTokens = 4096
                    ),
                    userText,
                    appContext,
                    detail = "当前模型不支持工具协议，已切换为普通回答"
                )
            } catch (fallbackError: Exception) {
                Log.e(TAG, "LLM 调用异常", fallbackError)
                emit(
                    AgentOutput.Error(
                        fallbackError.localizedMessage ?: "AI 服务不可用，请检查 API Key 设置"
                    )
                )
            }
        }
    }

    private suspend fun FlowCollector<AgentOutput>.emitStreamingTextResponse(
        request: LlmRequest,
        userText: String,
        appContext: Context,
        detail: String
    ) {
        val textBuilder = StringBuilder()
        emit(
            AgentOutput.ProcessPhase(
                stage = com.example.tielink.domain.model.AgentProcessStage.TEXT_GENERATION,
                title = "文本生成中",
                detail = detail,
                canCancel = true
            )
        )
        aiProviderManager.chatStream(request).collect { event ->
            when (event) {
                is StreamEvent.Start -> Unit
                is StreamEvent.Thinking -> emit(AgentOutput.Thinking(event.text))
                is StreamEvent.Content -> {
                    textBuilder.append(event.text)
                    emit(AgentOutput.StreamText(event.text))
                }
                is StreamEvent.Done -> {
                    withContext(Dispatchers.IO) {
                        handleMemoryExtraction(userText, textBuilder.toString(), appContext)
                    }
                    emit(AgentOutput.Done)
                }
                is StreamEvent.Error -> emit(AgentOutput.Error(event.message))
            }
        }
    }

    private suspend fun preloadCurrentJdIfNeeded(
        userText: String,
        turnPolicy: AgentTurnPolicy
    ): AgentTurnPolicy {
        if ("analyze_jd" !in turnPolicy.allowedToolNames) return turnPolicy
        if (!agentToolCoordinator.shouldAutoSaveJd(userText)) return turnPolicy

        val saved = agentToolCoordinator.tryAutoSaveJd(userText)
        if (!saved) return turnPolicy

        return turnPolicy.copy(
            allowedToolNames = turnPolicy.allowedToolNames - "analyze_jd",
            reason = "${turnPolicy.reason} 已预先保存当前 JD。"
        )
    }

    /**
     * 构建系统提示词
     */
    private suspend fun buildSystemPrompt(appContext: Context, turnPolicy: AgentTurnPolicy): String {
        val config = promptRegistry.get("agent_chat")
        val basePrompt = config.system

        val sb = StringBuilder(basePrompt)
        sb.append(
            """

            【工具使用规则】
            - 你可以自主选择并调用本轮提供的工具；没有提供的工具视为本轮不允许调用。
            - 只有用户明确需要应用数据、执行动作、或结构化可视化时才调用工具；解释概念、回答机制、普通建议时直接文本回复。
            - 工具结果返回后，结合结果继续回答；不要编造未返回的数据。
            - calculate_match、optimize_resume 等业务工具已经自带专用卡片，不要再用 render_card 重复包装。
            - 仅当用户要求可视化/操作面板/流程/计划/比较/决策，或结构化信息明显比纯文字更清楚时，才调用 render_card。
            - render_card 只能生成协议允许的数据组件，不能生成代码、HTML 或脚本。
            - dynamic card 的 section.type 支持：text、metrics、tags、progress、timeline、steps、table、kanban、decision。
            - dynamic card 的 action.type 支持：prompt、open_jd_library、open_resume_library、upload_resume、open_tracking、open_resume_optimize、open_settings。
            - 当用户的问题需要“下一步去哪”“缺少什么资料”“给我一个操作面板”“把方案可视化”时，可以使用 render_card，并给出 1-3 个最合适的 actions。
            - 多方案比较优先用 table；任务状态优先用 kanban；选择建议优先用 decision；时间顺序优先用 timeline；执行计划优先用 steps。
            - 缺少简历、JD、面试会话等前置条件时，说明缺少什么并引导用户补充，不要反复调用同一工具。

            【本轮工具边界】
            - 允许工具：${turnPolicy.allowedToolNames.ifEmpty { setOf("无") }.joinToString("、")}
            - 判定原因：${turnPolicy.reason}
            """.trimIndent()
        )

        val agentContext = agentContextRepository.getAgentContext()
        agentContext.currentJdText?.let { sb.append("\n\n【当前岗位】\n${it.take(500)}") }
        agentContext.currentJdCompany?.let { sb.append("\n公司: $it") }

        val resume = resumeVersionRepository.getActive()
        resume?.let {
            sb.append("\n\n【当前简历】${it.name}")
            agentContextRepository.updateAgentContext(currentResumeVersionId = it.id)
            buildResumeSnapshot(it)?.let { snapshot ->
                sb.append("\n")
                sb.append(snapshot)
            }
        }

        // 文件 I/O 必须在 IO 线程上执行（调用方已 withContext(IO)，这里直接调用即可）
        runCatching { AgentWorkspace.buildResumeContext(appContext) }.getOrNull()
            ?.takeIf { it.length > 50 }
            ?.let { sb.append("\n\n【用户简历记忆】\n${it.take(1000)}") }

        runCatching { AgentWorkspace.buildJdContext(appContext) }.getOrNull()
            ?.takeIf { it.length > 50 }
            ?.let { sb.append("\n\n【用户岗位偏好】\n${it.take(500)}") }

        runCatching { AgentWorkspace.buildInterviewContext(appContext) }.getOrNull()
            ?.takeIf { it.length > 50 }
            ?.let { sb.append("\n\n【用户面试记忆】\n${it.take(500)}") }

        return sb.toString()
    }

    private fun buildResumeSnapshot(resume: ResumeVersion): String? {
        val resumeText = resume.rawText.ifBlank { resume.cleanedText }.trim()
        if (resumeText.isBlank()) return null

        val parsed = ResumeData.fromPolishedText(resumeText)
        val summaryLines = buildList {
            parsed.name.takeIf { it.isNotBlank() }?.let { add("姓名：$it") }
            parsed.targetPosition.takeIf { it.isNotBlank() }?.let { add("目标岗位：$it") }
            parsed.summary.takeIf { it.isNotBlank() }?.let { add("摘要：${it.take(120)}") }
            parsed.skills
                .filter { it.isNotBlank() }
                .distinct()
                .take(12)
                .takeIf { it.isNotEmpty() }
                ?.let { add("技能：${it.joinToString("、")}") }
            parsed.experiences
                .take(2)
                .mapNotNull { exp ->
                    val company = exp.company.ifBlank { null } ?: return@mapNotNull null
                    listOf(exp.title, company, exp.period)
                        .filter { it.isNotBlank() }
                        .joinToString(" | ")
                }
                .takeIf { it.isNotEmpty() }
                ?.let { add("经历：${it.joinToString("；")}") }
        }

        val excerpt = resumeText
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(12)
            .joinToString("\n")
            .take(1600)

        return buildString {
            if (summaryLines.isNotEmpty()) {
                appendLine("【当前简历信息】")
                appendLine(summaryLines.joinToString("\n"))
            }
            appendLine("【当前简历原文摘录】")
            append(excerpt)
        }.trim()
    }

    /**
     * 构建消息列表
     */
    private fun buildMessages(
        systemPrompt: String,
        conversationHistory: List<com.example.tielink.domain.model.AgentMessage>,
        userText: String
    ): List<Message> {
        val messages = mutableListOf(Message("system", systemPrompt))

        val normalizedUserText = userText.trim()
        val historyWithoutCurrentTurn = conversationHistory
            .dropLastWhile { msg ->
                msg.role == com.example.tielink.domain.model.AgentMessageRole.AGENT &&
                    msg.isStreaming &&
                    msg.content.isBlank() &&
                    msg.thinkingContent.isNullOrBlank() &&
                    msg.card == null
            }
            .dropLastWhile { msg ->
                msg.role == com.example.tielink.domain.model.AgentMessageRole.USER &&
                    msg.content.trim() == normalizedUserText
            }
        historyWithoutCurrentTurn
            .filter { it.role != com.example.tielink.domain.model.AgentMessageRole.SYSTEM }
            .takeLast(MAX_CONTEXT_MESSAGES)
            .forEach { msg ->
                val content = msg.content.ifBlank {
                    msg.card?.let(::summarizeCardForContext).orEmpty()
                }.take(1200)
                if (content.isBlank()) return@forEach
                messages.add(Message(
                    role = when (msg.role) {
                        com.example.tielink.domain.model.AgentMessageRole.USER -> "user"
                        com.example.tielink.domain.model.AgentMessageRole.AGENT -> "assistant"
                        com.example.tielink.domain.model.AgentMessageRole.SYSTEM -> "system"
                    },
                    content = content
                ))
            }

        // 添加当前用户消息
        messages.add(Message("user", userText))

        return messages
    }

    private suspend fun buildTurnPolicy(
        userText: String,
        conversationHistory: List<AgentMessage>
    ): AgentTurnPolicy {
        val text = userText.lowercase().trim()
        val activeResume = resumeVersionRepository.getActive()
        val ctx = agentContextRepository.getAgentContext()
        val hasJd = !ctx.currentJdText.isNullOrBlank()
        val hasActiveInterview = runCatching { interviewRepository.getActiveSession() != null }.getOrDefault(false)

        val asksExplanation = text.containsAny(
            "什么意思", "是什么", "为什么", "解释", "说明一下", "怎么理解",
            "现在是", "区别", "原理", "机制", "链路", "流程是什么"
        )
        val asksVisual = text.containsAny(
            "卡片", "可视化", "面板", "表格", "看板", "时间线", "步骤",
            "流程", "工作流", "决策", "对比", "计划", "路线图", "下一步", "安排"
        )
        val asksJd = text.containsAny("分析jd", "jd分析", "看jd", "岗位分析", "岗位核心要求", "职位要求") ||
            agentToolCoordinator.shouldAutoSaveJd(userText)
        val asksMatch = text.containsAny("匹配度", "匹配分析", "匹配一下", "匹配看看", "打多少分", "能打几分", "胜率", "差距")
        val asksResumeEdit = text.containsAny("优化简历", "润色简历", "改简历", "修改简历", "简历优化", "量化", "star")
        val asksResumePreview = text.containsAny("预览简历", "简历预览", "看看简历", "显示简历")
        val asksInterview = text.containsAny("模拟面试", "练面试", "面试题", "面试练习", "准备面试", "问我")
        val asksBossOpportunity = text.containsAny(
            "boss机会", "boss 岗位", "boss岗位", "boss分析", "boss 机会",
            "机会池", "岗位池", "哪些岗位值得投", "优先投", "投哪个", "投哪些",
            "岗位推荐", "分析师助理", "机会分析"
        )
        val asksTracking = text.containsAny("投递", "申请状态", "投递记录", "投递进度", "跟进", "看板")
        val asksCreateApplication = text.containsAny(
            "加入投递", "创建投递", "新建投递", "记录投递", "添加投递",
            "加入投递记录", "放进投递", "记到投递", "记录这个岗位"
        )
        val asksGreeting = text.containsAny("打招呼", "话术", "开场白", "私信", "怎么聊") ||
            (text.contains("boss") && text.containsAny("招呼", "话术", "开场", "私信", "沟通"))
        val isFollowUpToCard = conversationHistory.takeLast(4).any { it.card != null } &&
            text.containsAny("继续", "展开", "换一个", "再来", "详细", "采用", "撤回")

        val allowed = linkedSetOf<String>()
        if (!asksExplanation || asksJd) {
            if (asksJd) allowed += "analyze_jd"
            if (asksMatch) allowed += "calculate_match"
            if (asksResumeEdit) allowed += "optimize_resume"
            if (asksResumePreview) allowed += "show_resume_preview"
            if (asksInterview && hasActiveInterview) allowed += "get_interview_turn"
            if (asksBossOpportunity) allowed += "analyze_boss_opportunities"
            if (asksCreateApplication) allowed += "create_application_from_current_jd"
            if (asksTracking && !asksCreateApplication) allowed += "get_latest_application"
            if (asksGreeting) allowed += "generate_greeting"
        }

        if (
            asksVisual ||
            isFollowUpToCard ||
            (allowed.isEmpty() && text.containsAny("怎么做", "怎么办", "帮我规划", "建议我", "我该"))
        ) {
            allowed += "render_card"
        }

        if (asksMatch && activeResume == null) {
            allowed += "calculate_match"
        }
        if (asksMatch && !hasJd && !asksVisual) {
            allowed.remove("render_card")
        }

        val reason = when {
            allowed.isEmpty() -> "当前更适合文本回答，避免弹出无关卡片。"
            asksVisual -> "用户明确要求可视化或操作面板。"
            asksMatch || asksResumeEdit || asksJd || asksInterview || asksBossOpportunity || asksTracking || asksGreeting ->
                "用户请求命中具体求职任务。"
            isFollowUpToCard -> "用户在延续上一张卡片。"
            else -> "用户需要规划型回答。"
        }

        return AgentTurnPolicy(allowedToolNames = allowed, reason = reason)
    }

    private fun String.containsAny(vararg keywords: String): Boolean =
        keywords.any { contains(it) }

    private fun summarizeCardForContext(card: UiCard): String {
        return when (card) {
            is UiCard.MatchCard ->
                "[卡片: 匹配度 ${card.overallScore}，缺失技能 ${card.missingSkills.take(4).joinToString("、")}]"
            is UiCard.ResumeDiffCard ->
                "[卡片: 简历优化建议 ${card.section}，状态 ${card.status}]"
            is UiCard.ResumePreviewCard ->
                "[卡片: 简历预览 ${card.versionName}]"
            is UiCard.EvalCard ->
                "[卡片: 面试评估 ${card.overallScore}]"
            is UiCard.TrackingCard ->
                "[卡片: 投递记录 ${card.company} ${card.status}]"
            is UiCard.GreetingCard ->
                "[卡片: 打招呼话术 ${card.companyName} ${card.position}]"
            is UiCard.InterviewTurnCard ->
                "[卡片: 面试第 ${card.questionNumber}/${card.totalQuestions} 题]"
            is UiCard.UploadPromptCard ->
                "[卡片: 上传提示 ${card.title}]"
            is UiCard.ResumeSourceChoiceCard ->
                "[卡片: 简历来源选择 ${card.title}]"
            is UiCard.DynamicCard ->
                "[卡片: ${card.title}，结构 ${card.sections.joinToString("、") { it.type }}]"
        }
    }

    /**
     * 处理记忆提取
     */
    private fun handleMemoryExtraction(userText: String, agentReply: String, appContext: Context) {
        // 从用户输入提取记忆
        val userMemory = AgentWorkspace.extractExplicitMemory(userText)
        if (userMemory != null) {
            val lower = userText.lowercase()
            val saved = when {
                lower.contains("简历") || lower.contains("经历") || lower.contains("技能") -> {
                    AgentWorkspace.appendResumeMemory(appContext, userMemory)
                }
                lower.contains("岗位") || lower.contains("职位") || lower.contains("偏好") || lower.contains("行业") -> {
                    AgentWorkspace.appendJdMemory(appContext, userMemory)
                }
                lower.contains("面试") || lower.contains("薄弱") || lower.contains("擅长") -> {
                    AgentWorkspace.appendInterviewMemory(appContext, userMemory)
                }
                else -> AgentWorkspace.appendResumeMemory(appContext, userMemory)
            }
            if (saved) {
                Log.d(TAG, "已保存用户记忆: $userMemory")
            }
        }
    }

    // ── 调试用：弹出所有卡片类型 ───────────────────────────────────────────────

    private suspend fun FlowCollector<AgentOutput>.emitDebugCards() {
        emit(AgentOutput.StreamText("🔧 **调试模式** — 依次展示所有卡片类型\n\n"))

        // MatchCard
        emit(AgentOutput.ToolStart("calculate_match", "正在分析简历与岗位匹配度..."))
        emit(AgentOutput.ToolResult(UiCard.MatchCard(
            overallScore = 78,
            keywordScore = 82,
            skillScore = 75,
            experienceScore = 80,
            educationScore = 70,
            missingSkills = listOf("Kotlin Coroutines", "Jetpack Compose", "CI/CD"),
            highlights = listOf("5年Android经验", "硕士学历匹配", "团队管理加分")
        )))

        // ResumeDiffCard
        emit(AgentOutput.ToolStart("optimize_resume", "正在扫描简历可优化点..."))
        emit(AgentOutput.ToolResult(UiCard.ResumeDiffCard(
            section = "工作经验",
            before = "负责公司内部系统的开发与维护工作",
            after = "主导3个核心业务系统的架构设计与开发，支撑日均100万+请求，系统可用性提升至99.9%",
            onAccept = {},
            onRollback = {}
        )))

        // ResumePreviewCard — 附带 ResumeData 以渲染 HTML
        val debugResumeData = com.example.tielink.domain.model.ResumeData(
            name = "张三",
            targetPosition = "高级Android开发工程师",
            contact = "138-0000-0000 | zhangsan@example.com",
            summary = "5年Android开发经验，熟练掌握Kotlin、Jetpack Compose，主导过多个大型App架构设计。",
            experiences = listOf(
                com.example.tielink.domain.model.ResumeData.Experience(
                    company = "字节跳动",
                    title = "Android高级工程师",
                    period = "2021.07 — 至今",
                    description = "负责抖音Android端性能优化，启动速度提升40%；主导Compose迁移项目，覆盖30+页面"
                )
            ),
            education = listOf(
                com.example.tielink.domain.model.ResumeData.Education(
                    school = "北京大学",
                    degree = "计算机科学 硕士",
                    period = "2018 — 2021"
                )
            ),
            skills = listOf("Kotlin", "Jetpack Compose", "Android", "MVVM", "Coroutines", "Room", "Hilt")
        )
        emit(AgentOutput.ToolResult(UiCard.ResumePreviewCard(
            versionName = "技术岗主版本",
            versionId = 1L,
            previewText = "张三 | Android开发工程师\n北京 | 138-0000-0000",
            resumeData = debugResumeData
        )))

        // EvalCard
        emit(AgentOutput.ToolStart("get_interview_turn", "正在生成面试评估..."))
        emit(AgentOutput.ToolResult(UiCard.EvalCard(
            overallScore = 85,
            dimensions = mapOf("技术深度" to 88, "表达清晰度" to 82, "项目经验" to 90, "问题解决" to 80),
            keyMoments = listOf("STAR法则运用流畅", "量化数据充分", "反问环节表现积极")
        )))

        // TrackingCard
        emit(AgentOutput.ToolStart("get_latest_application", "正在读取投递记录..."))
        emit(AgentOutput.ToolResult(UiCard.TrackingCard(
            company = "字节跳动",
            status = "面试",
            applicationId = 42L
        )))

        // GreetingCard
        emit(AgentOutput.ToolStart("generate_greeting", "正在生成打招呼话术..."))
        emit(AgentOutput.ToolResult(UiCard.GreetingCard(
            companyName = "字节跳动",
            position = "高级Android开发工程师",
            greetings = listOf(
                GreetingVersion("简洁版",
                    "您好！我有5年Android开发经验，熟练掌握Kotlin和Jetpack Compose，对贵司岗位非常感兴趣，期待与您交流。",
                    listOf("Kotlin", "Jetpack Compose")),
                GreetingVersion("详细版",
                    "尊敬的面试官，您好！我目前在某大厂担任Android技术负责人，主导过多个亿级DAU产品的架构设计。看到贵司招聘，与我的技术方向高度匹配，希望有机会详谈。",
                    listOf("架构设计", "性能优化", "团队管理")),
                GreetingVersion("亮点突出版",
                    "您好！抖音性能优化 & Compose大规模落地经验，启动速度优化40%实战案例，求一次交流机会！",
                    listOf("性能优化", "Compose", "实战经验"))
            )
        )))

        // InterviewTurnCard
        emit(AgentOutput.ToolResult(UiCard.InterviewTurnCard(
            questionNumber = 3,
            totalQuestions = 10,
            question = "请介绍一下你在 Android 性能优化方面的具体实践，以及你是如何衡量优化效果的？",
            feedback = "上一题回答质量：结构清晰，STAR法则运用恰当，建议补充更多量化数据。"
        )))

        // UploadPromptCard
        emit(AgentOutput.ToolResult(UiCard.UploadPromptCard(
            title = "需要您的简历",
            description = "请上传简历文件，支持 PDF、Word 或纯文本格式"
        )))

        emit(AgentOutput.StreamText("\n\n✅ 全部 8 种卡片已展示完毕。"))
        emit(AgentOutput.Done)
    }
}
