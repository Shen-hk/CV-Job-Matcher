package com.example.tielink.domain.usecase

import android.content.Context
import android.util.Log
import com.example.tielink.data.remote.AiProviderManager
import com.example.tielink.data.remote.LlmRequest
import com.example.tielink.data.remote.PromptRegistry
import com.example.tielink.data.remote.dto.Message
import com.example.tielink.data.repository.AgentContextRepository
import com.example.tielink.data.repository.InterviewRepository
import com.example.tielink.data.repository.JdLibraryRepository
import com.example.tielink.data.repository.ResumeVersionRepository
import com.example.tielink.data.repository.TrackingRepository
import com.example.tielink.domain.model.AgentContext
import com.example.tielink.domain.model.AgentIntent
import com.example.tielink.domain.model.AgentOutput
import com.example.tielink.domain.model.GreetingVersion
import com.example.tielink.domain.model.UiCard
import com.example.tielink.domain.nlp.IntentClassifier
import com.example.tielink.domain.nlp.NlpEngine
import com.example.tielink.util.AgentWorkspace
import com.squareup.moshi.Moshi
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
    private val jdLibraryRepository: JdLibraryRepository,
    private val matchScoreDetailUseCase: MatchScoreDetailUseCase,
    private val skillGapAnalyzer: SkillGapAnalyzer,
    private val quantifyAssistant: QuantifyAssistant,
    private val trackingRepository: TrackingRepository,
    private val interviewRepository: InterviewRepository,
    private val moshi: Moshi
) {
    companion object {
        private const val TAG = "AgentUseCase"
        private const val MAX_CONTEXT_MESSAGES = 20
        // TODO: 调试开关，测试完记得改回 false
        private const val DEBUG_SHOW_ALL_CARDS = false
    }
    /**
     * 处理用户输入，返回 AgentOutput 流
     */
    fun process(
        userText: String,
        conversationHistory: List<com.example.tielink.domain.model.AgentMessage>,
        appContext: Context
    ): Flow<AgentOutput> = flow {
        // ── 调试模式：依次弹出所有卡片类型，方便 UI 验证 ──────────────────────
        if (DEBUG_SHOW_ALL_CARDS) {
            emitDebugCards()
            return@flow
        }
        // ──────────────────────────────────────────────────────────────────────

        // 1. 意图识别
        val intent = IntentClassifier.classify(userText)
        Log.d(TAG, "识别意图: ${intent.type}, 工具调用: ${intent.toolCall}")

        // 1a. 自动保存 JD：检测到长文本包含岗位关键词时，后台提取并写入 JD 库
        if (intent.type == com.example.tielink.domain.model.IntentType.JD_ANALYZE
            || looksLikeJd(userText)
        ) {
            withContext(Dispatchers.IO) { tryAutoSaveJd(userText) }
        }

        // 2. 如果需要澄清，发出澄清请求
        if (intent.clarificationNeeded) {
            emit(AgentOutput.ClarificationRequest(
                question = intent.clarificationPrompt ?: "请确认你的意图",
                options = listOf("是", "否", "取消")
            ))
            return@flow
        }

        // 3. 如果有工具调用，先执行工具
        if (intent.toolCall != null) {
            val toolName = intent.toolCall.toolName

            // 3a. 前置条件检查 —— 缺简历时直接给上传卡，绝不进入 ToolStart/loading
            //     （区分「缺前置条件」与「有前置条件但工具无产出」两种情况）
            val needsResume = toolName in listOf("resume_tool", "match_tool")
            if (needsResume && resumeVersionRepository.getActive() == null) {
                val (title, desc) = when (toolName) {
                    "resume_tool" -> "需要您的简历" to "请上传简历文件，支持 PDF、Word 或纯文本格式"
                    else -> "需要简历才能分析匹配度" to "请上传简历文件，支持 PDF、Word 或纯文本格式"
                }
                emit(AgentOutput.ToolResult(UiCard.UploadPromptCard(title = title, description = desc, toolName = toolName)))
                emit(AgentOutput.Done)
                return@flow
            }

            val toolDesc = when (toolName) {
                "match_tool" -> "正在分析简历与岗位匹配度..."
                "resume_tool" -> "简历正在优化中..."
                "interview_tool" -> "正在加载面试会话..."
                "tracking_tool" -> "正在读取投递记录..."
                "platform_tool" -> "正在生成打招呼话术..."
                else -> "正在调用 ${intent.toolCall.function}..."
            }
            emit(AgentOutput.ToolStart(toolName = toolName, description = toolDesc))

            val toolResult = executeTool(toolName, userText, appContext)

            // 3b. resume_tool：简历已存在，无论是否生成 diff 建议，都展示简历预览卡
            if (toolName == "resume_tool") {
                if (toolResult != null) emit(AgentOutput.ToolResult(toolResult))
                val previewCard = buildResumePreviewCard()
                if (previewCard != null) {
                    emit(AgentOutput.ToolResult(previewCard))
                } else if (toolResult == null) {
                    // 理论上简历存在就能构建预览卡；兜底防止 loading 气泡残留
                    emit(AgentOutput.ToolCancelled(toolName))
                    emit(AgentOutput.StreamText("简历已就绪，但暂时没有可自动优化的内容，你可以告诉我想优化哪一部分。\n\n"))
                }
                if (toolResult != null || previewCard != null) {
                    emit(AgentOutput.Done)
                    return@flow
                }
            } else if (toolResult != null) {
                emit(AgentOutput.ToolResult(toolResult))
                // match_tool 完成后追加简历预览卡
                if (toolName == "match_tool") {
                    buildResumePreviewCard()?.let { emit(AgentOutput.ToolResult(it)) }
                }
                emit(AgentOutput.Done)
                return@flow
            }

            // 3c. 工具有前置条件但无产出（如 match 缺 JD、面试无会话）——
            //     移除 loading 气泡，给一段引导文字后交给 LLM 继续对话
            emit(AgentOutput.ToolCancelled(toolName))
            val hint = when (toolName) {
                "match_tool" -> "（还没有设置目标岗位 JD，无法计算匹配度。你可以把岗位描述发给我，我来帮你分析）"
                "interview_tool" -> "（需要先在模拟面试页面开启会话才能加载面试卡片）"
                "tracking_tool" -> "（暂无投递记录，请先在投递追踪页面添加记录）"
                "platform_tool" -> "（需要先设置目标岗位 JD 才能生成打招呼话术卡片，以下是文字建议）"
                else -> null
            }
            if (hint != null) emit(AgentOutput.StreamText(hint + "\n\n"))
        }

        // 4. 构建系统提示词
        val systemPrompt = withContext(Dispatchers.IO) { buildSystemPrompt(appContext) }

        // 5. 构建 LLM 请求
        val messages = buildMessages(systemPrompt, conversationHistory, userText)
        val request = LlmRequest(
            messages = messages,
            temperature = 0.7,
            maxTokens = 4096
        )

        // 6. 流式调用 LLM（整块放入 try-catch）
        try {
            val streamFlow = aiProviderManager.chatStream(request)
            val textBuilder = StringBuilder()

            streamFlow.collect { event ->
                when (event) {
                    is com.example.tielink.data.remote.StreamEvent.Start -> { /* no-op */ }
                    is com.example.tielink.data.remote.StreamEvent.Thinking -> {
                        emit(AgentOutput.Thinking(event.text))
                    }
                    is com.example.tielink.data.remote.StreamEvent.Content -> {
                        textBuilder.append(event.text)
                        emit(AgentOutput.StreamText(event.text))
                    }
                    is com.example.tielink.data.remote.StreamEvent.Done -> {
                        withContext(Dispatchers.IO) {
                            handleMemoryExtraction(userText, textBuilder.toString(), appContext)
                        }
                        emit(AgentOutput.Done)
                    }
                    is com.example.tielink.data.remote.StreamEvent.Error -> {
                        emit(AgentOutput.Error(event.message))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "LLM 调用异常", e)
            emit(AgentOutput.Error(e.localizedMessage ?: "AI 服务不可用，请检查 API Key 设置"))
        }
    }

    /**
     * 构建系统提示词
     */
    private suspend fun buildSystemPrompt(appContext: Context): String {
        val config = promptRegistry.get("agent_chat")
        val basePrompt = config.system

        val sb = StringBuilder(basePrompt)

        val agentContext = agentContextRepository.getAgentContext()
        agentContext.currentJdText?.let { sb.append("\n\n【当前岗位】\n${it.take(500)}") }
        agentContext.currentJdCompany?.let { sb.append("\n公司: $it") }

        val resume = resumeVersionRepository.getActive()
        resume?.let {
            sb.append("\n\n【当前简历】${it.name}")
            agentContextRepository.updateAgentContext(currentResumeVersionId = it.id)
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

    /**
     * 构建消息列表
     */
    private fun buildMessages(
        systemPrompt: String,
        conversationHistory: List<com.example.tielink.domain.model.AgentMessage>,
        userText: String
    ): List<Message> {
        val messages = mutableListOf(Message("system", systemPrompt))

        // 添加历史消息（跳过最后一条，因为它就是当前用户消息，会在末尾显式添加）
        val historyWithoutLast = conversationHistory.dropLastWhile {
            it.role == com.example.tielink.domain.model.AgentMessageRole.USER
        }
        historyWithoutLast
            .filter { it.role != com.example.tielink.domain.model.AgentMessageRole.SYSTEM }
            .takeLast(MAX_CONTEXT_MESSAGES)
            .forEach { msg ->
                messages.add(Message(
                    role = when (msg.role) {
                        com.example.tielink.domain.model.AgentMessageRole.USER -> "user"
                        com.example.tielink.domain.model.AgentMessageRole.AGENT -> "assistant"
                        com.example.tielink.domain.model.AgentMessageRole.SYSTEM -> "system"
                    },
                    content = msg.content
                ))
            }

        // 添加当前用户消息
        messages.add(Message("user", userText))

        return messages
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

    // ── Tool execution ─────────────────────────────────────────────────────────

    private suspend fun executeTool(toolName: String, userText: String, appContext: Context): UiCard? {
        return try {
            when (toolName) {
                "match_tool" -> executeMatchTool()
                "resume_tool" -> executeResumeTool(userText)
                "interview_tool" -> executeInterviewTool()
                "tracking_tool" -> executeTrackingTool()
                "platform_tool" -> executePlatformTool()
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "工具执行失败: $toolName", e)
            null
        }
    }

    private suspend fun executeMatchTool(): UiCard? {
        val ctx = agentContextRepository.getAgentContext()
        val jdText = ctx.currentJdText ?: return null
        val resume = resumeVersionRepository.getActive() ?: return null
        val resumeText = resume.rawText.ifBlank { resume.cleanedText }
        if (resumeText.isBlank()) return null

        val jdKeywords = NlpEngine.extractKeywords(jdText, topN = 25, referenceText = resumeText)
        val resumeLower = resumeText.lowercase()
        val missing = jdKeywords.filter { !resumeLower.contains(it.lowercase()) }
        val matched = jdKeywords - missing.toSet()

        val detail = matchScoreDetailUseCase.compute(
            jdText = jdText,
            resumeText = resumeText,
            jdKeywords = jdKeywords,
            missingKeywords = missing
        )
        val gaps = skillGapAnalyzer.analyze(jdText, resumeText, jdKeywords)
        val overallScore = (
            detail.keywordCoverage * 0.30f +
            detail.skillFit * 0.30f +
            detail.experienceRelevance * 0.25f +
            detail.educationMatch * 0.15f
        ).times(100).toInt().coerceIn(0, 100)

        return UiCard.MatchCard(
            overallScore = overallScore,
            keywordScore = (detail.keywordCoverage * 100).toInt(),
            experienceScore = (detail.experienceRelevance * 100).toInt(),
            educationScore = (detail.educationMatch * 100).toInt(),
            skillScore = (detail.skillFit * 100).toInt(),
            missingSkills = gaps.take(8).map { it.skill },
            highlights = matched.take(6)
        )
    }

    // 从已保存的 resume 版本构建预览卡（含 HTML 渲染数据）
    private suspend fun buildResumePreviewCard(): UiCard.ResumePreviewCard? {
        val resume = resumeVersionRepository.getActive() ?: return null
        val rawText = resume.rawText.ifBlank { resume.cleanedText }
        if (rawText.isBlank()) return null
        val resumeData = com.example.tielink.domain.model.ResumeData.fromPolishedText(rawText)
        return UiCard.ResumePreviewCard(
            versionName = resume.name,
            versionId = resume.id,
            previewText = rawText.take(600),
            resumeData = resumeData
        )
    }

    private suspend fun executeResumeTool(userText: String): UiCard? {
        val resume = resumeVersionRepository.getActive() ?: return null
        val resumeText = resume.rawText.ifBlank { resume.cleanedText }
        if (resumeText.isBlank()) return null

        val suggestions = quantifyAssistant.analyzeAndSuggest(resumeText)
        val best = suggestions.maxByOrNull { it.confidence } ?: return null

        return UiCard.ResumeDiffCard(
            section = "经历描述",
            before = best.original,
            after = best.quantified,
            onAccept = {},   // callbacks injected by ViewModel (A-L3)
            onRollback = {}
        )
    }

    private suspend fun executeInterviewTool(): UiCard? {
        val session = interviewRepository.getActiveSession() ?: return null
        val messages = interviewRepository.getMessages(session.id)
        val lastQuestion = messages.lastOrNull { it.role.name == "ASSISTANT" }?.content
            ?: return null

        return UiCard.InterviewTurnCard(
            questionNumber = session.questionCount,
            totalQuestions = 10,
            question = lastQuestion,
            feedback = null
        )
    }

    private suspend fun executeTrackingTool(): UiCard? {
        val items = trackingRepository.getAll()
        val latest = items.maxByOrNull { it.updatedAt } ?: return null
        return UiCard.TrackingCard(
            company = latest.companyName,
            status = latest.status,
            applicationId = latest.id
        )
    }

    private suspend fun executePlatformTool(): UiCard? {
        val ctx = agentContextRepository.getAgentContext()
        val jdText = ctx.currentJdText ?: return null
        val company = ctx.currentJdCompany ?: ""
        val resume = resumeVersionRepository.getActive()
        val resumeSummary = resume?.rawText?.take(600) ?: ""

        val positionHint = jdText.lines().firstOrNull { it.length in 4..30 }?.trim() ?: "该职位"

        val systemPrompt = """你是一位 HR 专家，帮求职者撰写 Boss直聘打招呼话术。
请根据岗位信息和简历亮点，输出 JSON 格式的三个版本，格式严格如下：
{"versions":[{"style":"简洁版","content":"...","skills":["技能A"]},{"style":"详细版","content":"...","skills":["技能A","技能B"]},{"style":"亮点突出版","content":"...","skills":["技能A","技能B","技能C"]}]}
要求：语言自然不生硬，每版不超过120字，突出与岗位相关的具体亮点。"""

        val userPrompt = buildString {
            append("岗位：$positionHint")
            if (company.isNotBlank()) append("（${company}）")
            appendLine()
            append("JD摘要：${jdText.take(400)}")
            if (resumeSummary.isNotBlank()) {
                appendLine()
                append("简历亮点：${resumeSummary}")
            }
        }

        val request = LlmRequest(
            messages = listOf(
                Message("system", systemPrompt),
                Message("user", userPrompt)
            ),
            temperature = 0.8,
            maxTokens = 800
        )
        val response = aiProviderManager.chatWithFallback(request)
        val greetings = parseGreetingResponse(response.content)
        if (greetings.isEmpty()) return null

        return UiCard.GreetingCard(
            companyName = company.ifBlank { "目标公司" },
            position = positionHint,
            greetings = greetings
        )
    }

    private fun parseGreetingResponse(json: String): List<GreetingVersion> {
        return try {
            val cleaned = json.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            // Find the versions array manually (avoid extra Moshi type gymnastics)
            val regex = Regex(""""style"\s*:\s*"([^"]+)"\s*,\s*"content"\s*:\s*"([^"]+)"\s*,\s*"skills"\s*:\s*\[([^\]]*)]""")
            regex.findAll(cleaned).map { match ->
                val style = match.groupValues[1]
                val content = match.groupValues[2]
                val skills = match.groupValues[3]
                    .split(",")
                    .map { it.trim().removeSurrounding("\"") }
                    .filter { it.isNotBlank() }
                GreetingVersion(style = style, content = content, highlightedSkills = skills)
            }.toList()
        } catch (e: Exception) {
            Log.w(TAG, "解析 greeting 响应失败", e)
            emptyList()
        }
    }

    // ── JD 自动保存 ─────────────────────────────────────────────────────────

    /**
     * 判断文本是否像 JD：长度足够 + 包含岗位关键词。
     */
    private fun looksLikeJd(text: String): Boolean {
        if (text.length < 60) return false
        val jdKeywords = listOf(
            "岗位", "职责", "要求", "任职", "招聘", "学历", "经验", "薪资",
            "本科", "硕士", "负责", "团队", "开发", "设计", "产品", "项目",
            "职位", "工作", "技能", "能力", "熟悉", "掌握", "了解"
        )
        val hitCount = jdKeywords.count { text.contains(it) }
        return hitCount >= 4
    }

    /**
     * 尝试从用户输入中提取 JD 并保存到 JD 库。
     * 后台执行，不阻塞对话流。
     */
    private suspend fun tryAutoSaveJd(userText: String) {
        try {
            // 用 AI 提取关键信息
            val prompt = """你是一位招聘专家。请从以下文本中提取岗位信息，只返回JSON：
{"company":"公司名（如未提及则为空字符串）","position":"职位名称","salary":"薪资范围（如20k-40k，未提及则为空字符串）","skills":["技能1","技能2","技能3"]}"""
            val request = LlmRequest(
                messages = listOf(
                    Message("system", prompt),
                    Message("user", "请提取: ${userText.take(2000)}")
                ),
                temperature = 0.3,
                maxTokens = 300
            )
            val response = aiProviderManager.chatWithFallback(request)
            val json = response.content
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()

            val adapter = moshi.adapter(JdExtractResultMoshi::class.java)
            val result = adapter.fromJson(json) ?: return

            if (result.position.isNotBlank()) {
                jdLibraryRepository.saveFromAi(
                    companyName = result.company,
                    positionName = result.position,
                    rawText = userText,
                    structuredJson = json,
                    skills = result.skills,
                    salary = result.salary
                )
                Log.d(TAG, "已自动保存 JD: ${result.company} ${result.position}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "自动保存 JD 失败: ${e.message}")
        }
    }

    @com.squareup.moshi.JsonClass(generateAdapter = false)
    data class JdExtractResultMoshi(
        val company: String = "",
        val position: String = "",
        val salary: String = "",
        val skills: List<String> = emptyList()
    )

    // ── 调试用：弹出所有卡片类型 ───────────────────────────────────────────────

    private suspend fun FlowCollector<AgentOutput>.emitDebugCards() {
        emit(AgentOutput.StreamText("🔧 **调试模式** — 依次展示所有卡片类型\n\n"))

        // MatchCard
        emit(AgentOutput.ToolStart("match_tool", "正在分析简历与岗位匹配度..."))
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
        emit(AgentOutput.ToolStart("resume_tool", "正在扫描简历可优化点..."))
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
        emit(AgentOutput.ToolStart("interview_tool", "正在生成面试评估..."))
        emit(AgentOutput.ToolResult(UiCard.EvalCard(
            overallScore = 85,
            dimensions = mapOf("技术深度" to 88, "表达清晰度" to 82, "项目经验" to 90, "问题解决" to 80),
            keyMoments = listOf("STAR法则运用流畅", "量化数据充分", "反问环节表现积极")
        )))

        // TrackingCard
        emit(AgentOutput.ToolStart("tracking_tool", "正在读取投递记录..."))
        emit(AgentOutput.ToolResult(UiCard.TrackingCard(
            company = "字节跳动",
            status = "面试",
            applicationId = 42L
        )))

        // GreetingCard
        emit(AgentOutput.ToolStart("platform_tool", "正在生成打招呼话术..."))
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
