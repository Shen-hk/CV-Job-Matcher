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
import com.example.tielink.data.repository.PolishRepository
import com.example.tielink.data.repository.ResumeVersionRepository
import com.example.tielink.data.repository.TrackingRepository
import com.example.tielink.domain.model.AgentContext
import com.example.tielink.domain.model.DynamicCardAction
import com.example.tielink.domain.model.DynamicCardItem
import com.example.tielink.domain.model.DynamicCardSection
import com.example.tielink.domain.model.GreetingVersion
import com.example.tielink.domain.model.PolishResult
import com.example.tielink.domain.model.ResumeData
import com.example.tielink.domain.model.ResumeVersion
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
    private val polishRepository: PolishRepository,
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
        "create_application_from_current_jd",
        "generate_greeting",
        "analyze_boss_opportunities",
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
                name = "create_application_from_current_jd",
                description = "把当前 JD 加入投递记录。只有用户明确要求记录、创建、加入投递时调用。",
                properties = emptyMap(),
                required = emptyList()
            ),
            functionTool(
                name = "generate_greeting",
                description = "根据当前 JD 和简历生成多版招聘平台打招呼话术，并展示话术卡片。",
                properties = emptyMap(),
                required = emptyList()
            ),
            functionTool(
                name = "analyze_boss_opportunities",
                description = "分析 BOSS 直聘导入的岗位池，结合当前简历给出优先投递建议，并把最佳岗位设为当前 JD。",
                properties = emptyMap(),
                required = emptyList()
            ),
            dynamicCardTool()
        ) + customToolsByName.values
            .sortedBy { it.definition.function.name }
            .map { it.definition }
    }

    fun definitions(allowedToolNames: Set<String>? = null): List<LlmToolDefinition> {
        if (allowedToolNames == null) return toolDefinitions
        if (allowedToolNames.isEmpty()) return emptyList()
        return toolDefinitions.filter { it.function.name in allowedToolNames }
    }

    fun descriptionFor(toolName: String): String = when (toolName) {
        "analyze_jd" -> "正在分析并保存岗位信息..."
        "calculate_match" -> "正在分析简历与岗位匹配度..."
        "optimize_resume" -> "正在生成简历优化建议..."
        "show_resume_preview" -> "正在加载简历预览..."
        "get_interview_turn" -> "正在读取面试会话..."
        "get_latest_application" -> "正在读取投递记录..."
        "create_application_from_current_jd" -> "正在创建投递记录..."
        "generate_greeting" -> "正在生成打招呼话术..."
        "analyze_boss_opportunities" -> "正在分析 BOSS 岗位池..."
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
                        val polishedCard = optimizeResumeAndSave(instruction)
                        val cards = listOfNotNull(polishedCard ?: executeResumeTool(instruction))
                        ToolExecutionResult(
                            content = if (cards.isEmpty()) {
                                "当前简历中没有找到可安全自动修改的内容，请向用户询问具体段落。"
                            } else if (polishedCard != null) {
                                "简历已完成 AI 润色，并保存到简历库。现在可以直接查看 HTML 预览。"
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
                "create_application_from_current_jd" -> {
                    createApplicationFromCurrentJd()
                }
                "generate_greeting" -> executePlatformTool()?.let {
                    ToolExecutionResult("打招呼话术已经生成并展示。", listOf(it))
                } ?: ToolExecutionResult("缺少当前 JD，或话术生成失败。")
                "analyze_boss_opportunities" -> {
                    executeBossOpportunityTool()
                }
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

    private data class OpportunityRank(
        val jd: com.example.tielink.data.local.db.entity.JdLibraryEntity,
        val score: Int,
        val matchedSkills: List<String>,
        val reason: String
    )

    private fun dynamicCardTool(): LlmToolDefinition = functionTool(
        name = "render_card",
        description = "把适合视觉化的信息组装成安全的动态卡片。适用于比较、指标、标签、进度、步骤流、时间线、表格、看板、决策分支等结构化信息；普通回答不要调用。",
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
                            "enum" to listOf(
                                "text",
                                "metrics",
                                "tags",
                                "progress",
                                "timeline",
                                "steps",
                                "table",
                                "kanban",
                                "decision"
                            )
                        ),
                        "title" to mapOf("type" to listOf("string", "null")),
                        "text" to mapOf("type" to listOf("string", "null")),
                        "columns" to mapOf(
                            "type" to "array",
                            "maxItems" to 5,
                            "items" to stringProperty("表格列名，仅 table 类型使用。")
                        ),
                        "items" to mapOf(
                            "type" to "array",
                            "maxItems" to 8,
                            "items" to mapOf(
                                "type" to "object",
                                "properties" to mapOf(
                                    "label" to stringProperty("项目名称"),
                                    "value" to stringProperty("展示值"),
                                    "cells" to mapOf(
                                        "type" to "array",
                                        "maxItems" to 5,
                                        "items" to stringProperty("表格单元格内容，仅 table 类型使用。")
                                    ),
                                    "description" to mapOf(
                                        "type" to listOf("string", "null"),
                                        "description" to "补充说明，适合步骤说明、时间线细节、指标解释、决策依据。"
                                    ),
                                    "status" to mapOf(
                                        "type" to listOf("string", "null"),
                                        "enum" to listOf("todo", "active", "done", "warning", null),
                                        "description" to "可选状态，timeline、steps、kanban、decision 均可使用。"
                                    ),
                                    "progress" to mapOf(
                                        "type" to listOf("integer", "null"),
                                        "minimum" to 0,
                                        "maximum" to 100
                                    )
                                ),
                                "required" to listOf("label", "value", "cells", "description", "status", "progress"),
                                "additionalProperties" to false
                            )
                        )
                    ),
                    "required" to listOf("type", "title", "text", "columns", "items"),
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
                        "type" to mapOf(
                            "type" to "string",
                            "enum" to DynamicCardAction.SUPPORTED_TYPES.toList(),
                            "description" to "按钮动作类型。prompt 表示继续向 Agent 发送 prompt；其余类型表示安全的界面动作。"
                        ),
                        "prompt" to mapOf(
                            "type" to listOf("string", "null"),
                            "description" to "当 type=prompt 时必填；点击后作为用户后续请求交给 Agent 的文字。"
                        )
                    ),
                    "required" to listOf("label", "type"),
                    "additionalProperties" to false
                )
            )
        ),
        required = listOf("title", "subtitle", "sections", "actions")
    )

    private fun parseDynamicCard(json: JSONObject): UiCard.DynamicCard {
        val title = json.optString("title").trim().take(80)
        require(title.isNotBlank()) { "卡片标题不能为空" }
        val allowedTypes = setOf(
            "text",
            "metrics",
            "tags",
            "progress",
            "timeline",
            "steps",
            "table",
            "kanban",
            "decision"
        )
        val allowedStatuses = setOf("todo", "active", "done", "warning")
        val sectionsJson = json.optJSONArray("sections")
        val sections = buildList {
            if (sectionsJson != null) {
                for (index in 0 until minOf(sectionsJson.length(), 6)) {
                    val section = sectionsJson.optJSONObject(index) ?: continue
                    val type = section.optString("type")
                    if (type !in allowedTypes) continue
                    val columnsJson = section.optJSONArray("columns")
                    val columns = buildList {
                        if (columnsJson != null) {
                            for (columnIndex in 0 until minOf(columnsJson.length(), 5)) {
                                val column = columnsJson.optString(columnIndex).trim().take(40)
                                if (column.isNotBlank()) add(column)
                            }
                        }
                    }
                    val itemsJson = section.optJSONArray("items")
                    val items = buildList {
                        if (itemsJson != null) {
                            for (itemIndex in 0 until minOf(itemsJson.length(), 8)) {
                                val item = itemsJson.optJSONObject(itemIndex) ?: continue
                                val label = item.optString("label").trim().take(60)
                                val value = item.optString("value").trim().take(120)
                                val cellsJson = item.optJSONArray("cells")
                                val cells = buildList {
                                    if (cellsJson != null) {
                                        for (cellIndex in 0 until minOf(cellsJson.length(), 5)) {
                                            add(cellsJson.optString(cellIndex).trim().take(120))
                                        }
                                    }
                                }
                                if (label.isBlank() && value.isBlank()) continue
                                add(
                                    DynamicCardItem(
                                        label = label,
                                        value = value,
                                        cells = cells,
                                        description = item.optString("description").trim().take(180).ifBlank { null },
                                        status = item.optString("status")
                                            .trim()
                                            .lowercase()
                                            .takeIf { it in allowedStatuses },
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
                            columns = columns,
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
                    val type = action.optString("type")
                        .trim()
                        .lowercase()
                        .ifBlank { DynamicCardAction.TYPE_PROMPT }
                    val prompt = if (action.has("prompt") && !action.isNull("prompt")) {
                        action.optString("prompt").trim().take(500)
                    } else {
                        ""
                    }
                    if (label.isBlank() || type !in DynamicCardAction.SUPPORTED_TYPES) {
                        continue
                    }
                    if (type == DynamicCardAction.TYPE_PROMPT && prompt.isBlank()) {
                        continue
                    }
                    add(DynamicCardAction(label = label, prompt = prompt, type = type))
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
        val rawText = if (resume.isPolished) {
            resume.cleanedText.ifBlank { resume.rawText }
        } else {
            resume.rawText
        }
        if (rawText.isBlank()) return null
        val resumeData = if (resume.isPolished) {
            com.example.tielink.domain.model.ResumeData.fromPolishedText(rawText)
        } else {
            null
        }
        return UiCard.ResumePreviewCard(
            versionName = resume.name,
            versionId = resume.id,
            previewText = rawText.take(600),
            resumeData = resumeData
        )
    }

    private suspend fun optimizeResumeAndSave(instruction: String): UiCard.ResumePreviewCard? {
        val activeResume = resumeVersionRepository.getActive() ?: return null
        val sourceText = if (activeResume.isPolished) {
            activeResume.cleanedText.ifBlank { activeResume.rawText }
        } else {
            activeResume.rawText.ifBlank { activeResume.cleanedText }
        }.trim()
        if (sourceText.isBlank()) return null

        val context = agentContextRepository.getAgentContext()
        val jdOrGoal = context.currentJdText
            ?.takeIf { it.isNotBlank() }
            ?: buildGenericOptimizationGoal(instruction)

        val rawOutput = polishRepository.polishResume(
            jdText = jdOrGoal,
            resumeText = sourceText,
            fullPolish = true
        ).getOrElse { error ->
            Log.e(TAG, "AI 润色失败: ${error.message}", error)
            return null
        }

        val polishResult = PolishResult.fromLlmOutput(rawOutput)
        val polishedText = polishResult.polishedResume.trim()
        if (polishedText.isBlank()) return null

        val resumeData = polishResult.resumeJson
            .takeIf { it.isNotBlank() }
            ?.let(ResumeData::fromJsonString)
            ?: ResumeData.fromPolishedText(polishedText)

        val versionId = resumeVersionRepository.insertAndActivate(
            ResumeVersion(
                name = buildPolishedVersionName(activeResume.name),
                rawText = activeResume.rawText.ifBlank { sourceText },
                cleanedText = polishedText,
                matchScore = polishResult.matchAnalysis.score.toFloat(),
                tags = (activeResume.tags + listOf("AI润色")).distinct(),
                jdMatchedWith = context.currentJdCompany.orEmpty(),
                originalFilePath = activeResume.originalFilePath,
                originalMimeType = activeResume.originalMimeType,
                isPolished = true
            )
        )

        return UiCard.ResumePreviewCard(
            versionName = buildPolishedVersionName(activeResume.name),
            versionId = versionId,
            previewText = polishedText.take(600),
            resumeData = resumeData
        )
    }

    private fun buildGenericOptimizationGoal(instruction: String): String {
        val normalizedInstruction = instruction.trim().ifBlank { "请通用优化这份简历" }
        return """
            请基于以下要求对简历做通用优化，不编造经历，保留真实信息：
            1. 强化表达与量化成果
            2. 优化结构与可读性
            3. 补齐常见招聘筛选关键词
            4. 额外要求：$normalizedInstruction
        """.trimIndent()
    }

    private fun buildPolishedVersionName(baseName: String): String {
        val normalizedBase = baseName.trim().ifBlank { "我的简历" }
        return if (normalizedBase.contains("AI润色")) {
            normalizedBase
        } else {
            "$normalizedBase · AI润色"
        }
    }

    suspend fun tryAutoSaveJd(userText: String): Boolean {
        return try {
            if (!looksLikeJd(userText)) return false

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
            val result = adapter.fromJson(json) ?: return false

            if (result.position.isNotBlank()) {
                val savedId = jdLibraryRepository.saveFromAi(
                    companyName = result.company,
                    positionName = result.position,
                    rawText = userText,
                    structuredJson = json,
                    skills = result.skills,
                    salary = result.salary
                )
                agentContextRepository.updateAgentContext(
                    currentJdId = savedId,
                    currentJdText = userText,
                    currentJdCompany = result.company
                )
                Log.d(TAG, "已自动保存 JD: ${result.company} ${result.position}")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "自动保存 JD 失败: ${e.message}")
            false
        }
    }

    fun shouldAutoSaveJd(userText: String): Boolean = looksLikeJd(userText)

    private suspend fun executeBossOpportunityTool(): ToolExecutionResult {
        val allJds = jdLibraryRepository.getAll()
        if (allJds.isEmpty()) {
            val card = UiCard.DynamicCard(
                title = "BOSS 机会池还没有岗位",
                subtitle = "先导入岗位，再做匹配排序和投递建议",
                sections = listOf(
                    DynamicCardSection(
                        type = "steps",
                        items = listOf(
                            DynamicCardItem(
                                label = "打开 JD 库",
                                value = "待执行",
                                description = "使用 BOSS 一键导入，或者手动粘贴岗位描述。",
                                status = "active"
                            ),
                            DynamicCardItem(
                                label = "选择当前简历",
                                value = "待执行",
                                description = "有简历后会按匹配度给岗位排序。",
                                status = "todo"
                            )
                        )
                    )
                ),
                actions = listOf(
                    DynamicCardAction(
                        label = "打开 JD 库",
                        type = DynamicCardAction.TYPE_OPEN_JD_LIBRARY
                    ),
                    DynamicCardAction(
                        label = "选择简历",
                        type = DynamicCardAction.TYPE_OPEN_RESUME_LIBRARY
                    )
                )
            )
            return ToolExecutionResult("当前没有可分析的岗位。", listOf(card))
        }

        val bossJds = allJds.filter { it.sourceType == "boss_auto" }
        val candidates = (bossJds.ifEmpty { allJds }).take(30)
        val resume = resumeVersionRepository.getActive()
        val resumeText = resume?.rawText?.ifBlank { resume.cleanedText }.orEmpty()
        val hasResume = resumeText.isNotBlank()
        val ranks = candidates
            .map { jd -> rankOpportunity(jd, resumeText) }
            .sortedByDescending { it.score }
        val top = ranks.first()

        agentContextRepository.updateAgentContext(
            currentJdId = top.jd.id,
            currentJdText = top.jd.rawText,
            currentJdCompany = top.jd.companyName
        )

        val title = if (bossJds.isNotEmpty()) "BOSS 机会分析" else "岗位机会分析"
        val subtitle = if (hasResume) {
            "已结合当前简历排序，最佳岗位已设为当前 JD"
        } else {
            "未选择简历，先按岗位信息完整度做初筛"
        }
        val card = UiCard.DynamicCard(
            title = title,
            subtitle = subtitle,
            sections = listOf(
                DynamicCardSection(
                    type = "metrics",
                    items = listOf(
                        DynamicCardItem("岗位数", candidates.size.toString()),
                        DynamicCardItem("BOSS 导入", bossJds.size.toString()),
                        DynamicCardItem("当前简历", resume?.name ?: "未选择")
                    )
                ),
                DynamicCardSection(
                    type = "table",
                    title = "优先投递 Top 5",
                    columns = listOf("公司", "岗位", "分数", "依据"),
                    items = ranks.take(5).map { rank ->
                        DynamicCardItem(
                            label = rank.companyLabel(),
                            value = rank.positionLabel(),
                            cells = listOf(
                                rank.companyLabel(),
                                rank.positionLabel(),
                                "${rank.score}",
                                rank.reason
                            )
                        )
                    }
                ),
                DynamicCardSection(
                    type = "decision",
                    title = "建议先推进",
                    items = listOf(
                        DynamicCardItem(
                            label = "${top.companyLabel()} · ${top.positionLabel()}",
                            value = "${top.score}",
                            description = buildString {
                                append(top.reason)
                                if (top.matchedSkills.isNotEmpty()) {
                                    append("；命中：")
                                    append(top.matchedSkills.take(5).joinToString("、"))
                                }
                            },
                            status = "active"
                        )
                    )
                ),
                DynamicCardSection(
                    type = "steps",
                    title = "下一步",
                    items = listOf(
                        DynamicCardItem(
                            label = "生成 BOSS 打招呼话术",
                            value = "建议",
                            description = "基于已选中的最佳岗位和当前简历生成 3 版开场白。",
                            status = "active"
                        ),
                        DynamicCardItem(
                            label = "创建投递记录",
                            value = "建议",
                            description = "发出话术后，把公司和岗位加入投递看板，后续追踪状态。",
                            status = "todo"
                        )
                    )
                )
            ),
            actions = listOf(
                DynamicCardAction(
                    label = "生成话术",
                    type = DynamicCardAction.TYPE_PROMPT,
                    prompt = "基于当前最佳 BOSS 岗位，生成打招呼话术"
                ),
                DynamicCardAction(
                    label = "创建投递",
                    type = DynamicCardAction.TYPE_PROMPT,
                    prompt = "把当前最佳岗位加入投递记录"
                ),
                DynamicCardAction(
                    label = "看 JD 库",
                    type = DynamicCardAction.TYPE_OPEN_JD_LIBRARY
                )
            )
        )

        return ToolExecutionResult(
            content = "BOSS 机会池已分析，最佳岗位已设为当前 JD。",
            cards = listOf(card)
        )
    }

    private fun rankOpportunity(
        jd: com.example.tielink.data.local.db.entity.JdLibraryEntity,
        resumeText: String
    ): OpportunityRank {
        val skills = splitSkills(jd.skills)
        val hasResume = resumeText.isNotBlank()
        val matchedSkills = if (hasResume) {
            skills.filter { resumeText.contains(it, ignoreCase = true) }
        } else {
            emptyList()
        }
        val score = if (hasResume) {
            val semantic = (NlpEngine.matchScore(jd.rawText, resumeText) * 100).toInt()
            val skillScore = if (skills.isEmpty()) {
                50
            } else {
                (matchedSkills.size * 100 / skills.size).coerceIn(0, 100)
            }
            (semantic * 0.7 + skillScore * 0.3).toInt().coerceIn(0, 100)
        } else {
            val fieldScore = listOf(
                jd.companyName.isNotBlank(),
                jd.positionName.isNotBlank(),
                jd.salary.isNotBlank(),
                skills.isNotEmpty(),
                jd.rawText.length >= 200
            ).count { it } * 16
            (20 + fieldScore).coerceIn(0, 100)
        }

        val reason = when {
            hasResume && matchedSkills.size >= 3 -> "技能重合较多"
            hasResume && score >= 60 -> "文本匹配较好"
            hasResume -> "可作为备选，需要补强关键词"
            jd.salary.isNotBlank() && skills.isNotEmpty() -> "信息完整，可优先判断"
            jd.sourceType == "boss_auto" -> "来自 BOSS 导入，等待简历匹配"
            else -> "岗位信息可继续补全"
        }
        return OpportunityRank(jd, score, matchedSkills, reason)
    }

    private fun splitSkills(skills: String): List<String> =
        skills.split(",", "，", "、", "/", "|")
            .map { it.trim() }
            .filter { it.length >= 2 }
            .distinct()

    private fun OpportunityRank.companyLabel(): String =
        jd.companyName.ifBlank { "未识别公司" }

    private fun OpportunityRank.positionLabel(): String =
        jd.positionName.ifBlank {
            jd.rawText.lineSequence().firstOrNull { it.trim().length in 2..32 }?.trim()
                ?: "未识别岗位"
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

    private suspend fun createApplicationFromCurrentJd(): ToolExecutionResult {
        val ctx = agentContextRepository.getAgentContext()
        val jd = ctx.currentJdId?.let { jdLibraryRepository.getById(it) }
        val jdText = jd?.rawText ?: ctx.currentJdText
        if (jdText.isNullOrBlank()) {
            return ToolExecutionResult("缺少当前 JD，暂时无法创建投递记录。")
        }

        val company = jd?.companyName?.ifBlank { ctx.currentJdCompany.orEmpty() }
            ?: ctx.currentJdCompany.orEmpty()
        val position = jd?.positionName?.ifBlank { extractPositionHint(jdText) }
            ?: extractPositionHint(jdText)
        val normalizedCompany = company.ifBlank { "目标公司" }
        val normalizedPosition = position.ifBlank { "目标岗位" }
        val existing = trackingRepository.getAll().firstOrNull {
            it.companyName == normalizedCompany && it.positionName == normalizedPosition
        }
        val item = existing ?: run {
            val resume = resumeVersionRepository.getActive()
            val id = trackingRepository.insert(
                com.example.tielink.data.repository.TrackingItem(
                    companyName = normalizedCompany,
                    positionName = normalizedPosition,
                    status = "已投",
                    resumeVersionId = resume?.id,
                    jdRawText = jdText,
                    notes = "由 BOSS 机会分析助手创建"
                )
            )
            trackingRepository.getById(id)
        }

        val saved = item ?: return ToolExecutionResult("投递记录创建失败，请稍后重试。")
        return ToolExecutionResult(
            content = if (existing == null) {
                "已把当前岗位加入投递记录。"
            } else {
                "该岗位已在投递记录中，已展示现有记录。"
            },
            cards = listOf(
                UiCard.TrackingCard(
                    company = saved.companyName,
                    status = saved.status,
                    applicationId = saved.id
                )
            )
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

    private fun extractPositionHint(jdText: String): String =
        jdText.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.length in 2..32 && !it.contains("职位描述") && !it.contains("岗位职责") }
            .orEmpty()

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
