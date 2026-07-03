package com.example.tielink.domain.usecase

import android.util.Log
import com.example.tielink.data.remote.AiProviderManager
import com.example.tielink.data.remote.LlmFunctionDefinition
import com.example.tielink.data.remote.LlmRequest
import com.example.tielink.data.remote.LlmToolCall
import com.example.tielink.data.remote.LlmToolDefinition
import com.example.tielink.data.remote.dto.Message
import com.example.tielink.data.repository.AgentContextRepository
import com.example.tielink.data.repository.InterviewRepository
import com.example.tielink.data.repository.JdLibraryRepository
import com.example.tielink.data.repository.ResumeVersionRepository
import com.example.tielink.data.repository.TrackingRepository
import com.example.tielink.domain.model.AgentContext
import com.example.tielink.domain.model.DynamicCardAction
import com.example.tielink.domain.model.DynamicCardItem
import com.example.tielink.domain.model.DynamicCardSection
import com.example.tielink.domain.model.GreetingVersion
import com.example.tielink.domain.model.UiCard
import com.example.tielink.domain.nlp.NlpEngine
import com.example.tielink.util.AgentWorkspace
import com.squareup.moshi.Moshi
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentToolCoordinator @Inject constructor(
    private val aiProviderManager: AiProviderManager,
    private val agentContextRepository: AgentContextRepository,
    private val resumeVersionRepository: ResumeVersionRepository,
    private val jdLibraryRepository: JdLibraryRepository,
    private val matchScoreDetailUseCase: MatchScoreDetailUseCase,
    private val skillGapAnalyzer: SkillGapAnalyzer,
    private val quantifyAssistant: QuantifyAssistant,
    private val trackingRepository: TrackingRepository,
    private val interviewRepository: InterviewRepository,
    private val moshi: Moshi,
    customTools: Set<@JvmSuppressWildcards AgentTool>
) {
    companion object {
        private const val TAG = "AgentToolCoordinator"
    }

    private val builtInToolNames = setOf(
        "analyze_jd",
        "calculate_match",
        "optimize_resume",
        "show_resume_preview",
        "get_interview_turn",
        "get_latest_application",
        "generate_greeting",
        "render_card"
    )

    private val customToolsByName: Map<String, AgentTool> = customTools
        .also { tools ->
            val names = tools.map { it.definition.function.name }
            require(names.all { it.matches(Regex("[A-Za-z0-9_-]{1,64}")) }) {
                "AgentTool 名称只能包含字母、数字、下划线或连字符，且长度不超过 64：$names"
            }
            require(names.size == names.distinct().size) {
                "发现重名的自定义 AgentTool：$names"
            }
            require(names.none { it in builtInToolNames }) {
                "自定义 AgentTool 不能覆盖内置工具：${names.filter { it in builtInToolNames }}"
            }
        }
        .associateBy { it.definition.function.name }

    private val toolDefinitions: List<LlmToolDefinition> by lazy {
        listOf(
            functionTool(
                name = "analyze_jd",
                description = "分析并保存用户提供的岗位描述（JD）。当用户粘贴或明确要求分析 JD 时调用。",
                properties = mapOf(
                    "text" to stringProperty("完整的岗位描述文本")
                ),
                required = listOf("text")
            ),
            functionTool(
                name = "calculate_match",
                description = "计算当前生效简历与当前 JD 的匹配度，并展示匹配分析卡片。需要已有简历和 JD。",
                properties = emptyMap(),
                required = emptyList()
            ),
            functionTool(
                name = "optimize_resume",
                description = "根据用户指令分析当前简历的可优化内容，并展示修改建议和简历预览卡片。需要已有简历。",
                properties = mapOf(
                    "instruction" to stringProperty("用户希望如何优化简历")
                ),
                required = listOf("instruction")
            ),
            functionTool(
                name = "show_resume_preview",
                description = "展示当前生效简历的预览卡片。需要已有简历。",
                properties = emptyMap(),
                required = emptyList()
            ),
            functionTool(
                name = "get_interview_turn",
                description = "读取当前模拟面试会话的最近一道题，并展示面试题卡片。",
                properties = emptyMap(),
                required = emptyList()
            ),
            functionTool(
                name = "get_latest_application",
                description = "读取最近一条投递记录并展示投递状态卡片。",
                properties = emptyMap(),
                required = emptyList()
            ),
            functionTool(
                name = "generate_greeting",
                description = "根据当前 JD 和简历生成多版招聘平台打招呼话术，并展示话术卡片。",
                properties = emptyMap(),
                required = emptyList()
            ),
            dynamicCardTool()
        ) + customToolsByName.values
            .sortedBy { it.definition.function.name }
            .map { it.definition }
    }

    fun definitions(): List<LlmToolDefinition> = toolDefinitions

    fun descriptionFor(toolName: String): String = when (toolName) {
        "analyze_jd" -> "正在分析并保存岗位信息..."
        "calculate_match" -> "正在分析简历与岗位匹配度..."
        "optimize_resume" -> "正在生成简历优化建议..."
        "show_resume_preview" -> "正在加载简历预览..."
        "get_interview_turn" -> "正在读取面试会话..."
        "get_latest_application" -> "正在读取投递记录..."
        "generate_greeting" -> "正在生成打招呼话术..."
        "render_card" -> "正在组织卡片内容..."
        else -> customToolsByName[toolName]?.progressDescription ?: "正在执行工具..."
    }

    suspend fun execute(call: LlmToolCall, fallbackUserText: String): ToolExecutionResult {
        val arguments = runCatching { JSONObject(call.arguments) }.getOrElse {
            return ToolExecutionResult("工具参数不是有效 JSON：${it.message}")
        }
        customToolsByName[call.name]?.let { tool ->
            return try {
                tool.execute(arguments, fallbackUserText)
            } catch (e: Exception) {
                Log.e(TAG, "自定义工具执行失败: ${call.name}", e)
                ToolExecutionResult("工具 ${call.name} 执行失败：${e.localizedMessage ?: "未知错误"}")
            }
        }
        return try {
            when (call.name) {
                "analyze_jd" -> {
                    val text = arguments.optString("text").ifBlank { fallbackUserText }
                    tryAutoSaveJd(text)
                    ToolExecutionResult("岗位描述已分析；可结合当前简历继续做匹配或优化。")
                }
                "calculate_match" -> {
                    val card = executeMatchTool()
                    if (card == null) {
                        missingContextResult(
                            "缺少当前简历或 JD，暂时无法计算匹配度。",
                            needsResume = resumeVersionRepository.getActive() == null
                        )
                    } else {
                        ToolExecutionResult(
                            content = "匹配分析已完成，结果已经用卡片展示。",
                            cards = listOfNotNull(card, buildResumePreviewCard())
                        )
                    }
                }
                "optimize_resume" -> {
                    if (resumeVersionRepository.getActive() == null) {
                        missingContextResult("缺少当前简历，暂时无法优化。", needsResume = true)
                    } else {
                        val instruction = arguments.optString("instruction").ifBlank { fallbackUserText }
                        val cards = listOfNotNull(executeResumeTool(instruction), buildResumePreviewCard())
                        ToolExecutionResult(
                            content = if (cards.isEmpty()) {
                                "当前简历中没有找到可安全自动修改的内容，请向用户询问具体段落。"
                            } else {
                                "简历优化分析已完成，建议和预览已经用卡片展示。"
                            },
                            cards = cards
                        )
                    }
                }
                "show_resume_preview" -> {
                    val card = buildResumePreviewCard()
                    if (card == null) {
                        missingContextResult("缺少可预览的当前简历。", needsResume = true)
                    } else {
                        ToolExecutionResult("当前简历预览已经展示。", listOf(card))
                    }
                }
                "get_interview_turn" -> executeInterviewTool()?.let {
                    ToolExecutionResult("当前面试题已经展示。", listOf(it))
                } ?: ToolExecutionResult("当前没有进行中的模拟面试会话。")
                "get_latest_application" -> executeTrackingTool()?.let {
                    ToolExecutionResult("最近一条投递记录已经展示。", listOf(it))
                } ?: ToolExecutionResult("当前还没有投递记录。")
                "generate_greeting" -> executePlatformTool()?.let {
                    ToolExecutionResult("打招呼话术已经生成并展示。", listOf(it))
                } ?: ToolExecutionResult("缺少当前 JD，或话术生成失败。")
                "render_card" -> {
                    val card = parseDynamicCard(arguments)
                    ToolExecutionResult("信息已经整理成卡片展示。", listOf(card))
                }
                else -> ToolExecutionResult("未知工具：${call.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "工具执行失败: ${call.name}", e)
            ToolExecutionResult("工具 ${call.name} 执行失败：${e.localizedMessage ?: "未知错误"}")
        }
    }

    private fun missingContextResult(message: String, needsResume: Boolean): ToolExecutionResult {
        val cards = if (needsResume) {
            listOf(
                UiCard.ResumeSourceChoiceCard(
                    title = "需要您的简历",
                    description = "请选择已有简历继续，或者上传一份新的简历"
                )
            )
        } else {
            emptyList()
        }
        return ToolExecutionResult(message, cards)
    }

    private fun functionTool(
        name: String,
        description: String,
        properties: Map<String, Any?>,
        required: List<String>
    ) = LlmToolDefinition(
        function = LlmFunctionDefinition(
            name = name,
            description = description,
            parameters = mapOf(
                "type" to "object",
                "properties" to properties,
                "required" to required,
                "additionalProperties" to false
            )
        )
    )

    private fun stringProperty(description: String): Map<String, Any?> =
        mapOf("type" to "string", "description" to description)

    private fun dynamicCardTool(): LlmToolDefinition = functionTool(
        name = "render_card",
        description = "把适合视觉化的信息组装成安全的动态卡片。仅用于比较、指标、标签或进度等结构化信息；普通回答不要调用。",
        properties = mapOf(
            "title" to stringProperty("卡片标题，最多 80 字"),
            "subtitle" to mapOf("type" to listOf("string", "null"), "description" to "可选副标题"),
            "sections" to mapOf(
                "type" to "array",
                "maxItems" to 6,
                "items" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "type" to mapOf(
                            "type" to "string",
                            "enum" to listOf("text", "metrics", "tags", "progress")
                        ),
                        "title" to mapOf("type" to listOf("string", "null")),
                        "text" to mapOf("type" to listOf("string", "null")),
                        "items" to mapOf(
                            "type" to "array",
                            "maxItems" to 8,
                            "items" to mapOf(
                                "type" to "object",
                                "properties" to mapOf(
                                    "label" to stringProperty("项目名称"),
                                    "value" to stringProperty("展示值"),
                                    "progress" to mapOf(
                                        "type" to listOf("integer", "null"),
                                        "minimum" to 0,
                                        "maximum" to 100
                                    )
                                ),
                                "required" to listOf("label", "value", "progress"),
                                "additionalProperties" to false
                            )
                        )
                    ),
                    "required" to listOf("type", "title", "text", "items"),
                    "additionalProperties" to false
                )
            ),
            "actions" to mapOf(
                "type" to "array",
                "maxItems" to 3,
                "items" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "label" to stringProperty("按钮文字"),
                        "prompt" to stringProperty("点击后作为用户后续请求交给 Agent 的文字")
                    ),
                    "required" to listOf("label", "prompt"),
                    "additionalProperties" to false
                )
            )
        ),
        required = listOf("title", "subtitle", "sections", "actions")
    )

    private fun parseDynamicCard(json: JSONObject): UiCard.DynamicCard {
        val title = json.optString("title").trim().take(80)
        require(title.isNotBlank()) { "卡片标题不能为空" }
        val allowedTypes = setOf("text", "metrics", "tags", "progress")
        val sectionsJson = json.optJSONArray("sections")
        val sections = buildList {
            if (sectionsJson != null) {
                for (index in 0 until minOf(sectionsJson.length(), 6)) {
                    val section = sectionsJson.optJSONObject(index) ?: continue
                    val type = section.optString("type")
                    if (type !in allowedTypes) continue
                    val itemsJson = section.optJSONArray("items")
                    val items = buildList {
                        if (itemsJson != null) {
                            for (itemIndex in 0 until minOf(itemsJson.length(), 8)) {
                                val item = itemsJson.optJSONObject(itemIndex) ?: continue
                                val label = item.optString("label").trim().take(60)
                                val value = item.optString("value").trim().take(120)
                                if (label.isBlank() && value.isBlank()) continue
                                add(
                                    DynamicCardItem(
                                        label = label,
                                        value = value,
                                        progress = if (item.has("progress") && !item.isNull("progress")) {
                                            item.optInt("progress").coerceIn(0, 100)
                                        } else {
                                            null
                                        }
                                    )
                                )
                            }
                        }
                    }
                    add(
                        DynamicCardSection(
                            type = type,
                            title = section.optString("title").trim().take(80).ifBlank { null },
                            text = section.optString("text").trim().take(1200).ifBlank { null },
                            items = items
                        )
                    )
                }
            }
        }
        require(sections.isNotEmpty()) { "卡片至少需要一个有效 section" }

        val actionsJson = json.optJSONArray("actions")
        val actions = buildList {
            if (actionsJson != null) {
                for (index in 0 until minOf(actionsJson.length(), 3)) {
                    val action = actionsJson.optJSONObject(index) ?: continue
                    val label = action.optString("label").trim().take(30)
                    val prompt = action.optString("prompt").trim().take(500)
                    if (label.isNotBlank() && prompt.isNotBlank()) {
                        add(DynamicCardAction(label, prompt))
                    }
                }
            }
        }
        return UiCard.DynamicCard(
            title = title,
            subtitle = json.optString("subtitle").trim().take(160).ifBlank { null },
            sections = sections,
            actions = actions
        )
    }

    suspend fun buildResumePreviewCard(): UiCard.ResumePreviewCard? {
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

    suspend fun tryAutoSaveJd(userText: String) {
        try {
            if (!looksLikeJd(userText)) return

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

    fun shouldAutoSaveJd(userText: String): Boolean = looksLikeJd(userText)

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
            onAccept = {},
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

    @com.squareup.moshi.JsonClass(generateAdapter = false)
    data class JdExtractResultMoshi(
        val company: String = "",
        val position: String = "",
        val salary: String = "",
        val skills: List<String> = emptyList()
    )
}
