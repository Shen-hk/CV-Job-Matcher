package com.example.tielink.domain.usecase

import android.util.Log
import com.example.tielink.domain.agent.AgentChatGateway
import com.example.tielink.domain.agent.AgentChatMessage
import com.example.tielink.domain.agent.AgentChatRequest
import com.example.tielink.domain.agent.AgentFunctionDefinition
import com.example.tielink.domain.agent.AgentToolCall
import com.example.tielink.domain.agent.AgentToolDefinition
import com.example.tielink.data.repository.AgentContextRepository
import com.example.tielink.data.repository.CareerAgentStateRepository
import com.example.tielink.data.repository.InterviewRepository
import com.example.tielink.data.repository.JdLibraryRepository
import com.example.tielink.data.repository.PolishRepository
import com.example.tielink.data.repository.ResumeVersionRepository
import com.example.tielink.data.repository.TrackingRepository
import com.example.tielink.domain.model.AgentContext
import com.example.tielink.domain.agent.CareerAgentPlanner
import com.example.tielink.domain.agent.CareerAgentDecision
import com.example.tielink.domain.agent.CareerAgentHealth
import com.example.tielink.domain.agent.CareerAgentSupervisor
import com.example.tielink.domain.model.CareerAgentState
import com.example.tielink.domain.model.CareerTaskStatus
import com.example.tielink.domain.model.DynamicCardAction
import com.example.tielink.domain.model.DynamicCardItem
import com.example.tielink.domain.model.DynamicCardSection
import com.example.tielink.domain.model.GreetingVersion
import com.example.tielink.domain.model.PolishResult
import com.example.tielink.domain.model.ResumeData
import com.example.tielink.domain.model.ResumeVersion
import com.example.tielink.domain.model.UiCard
import com.example.tielink.domain.nlp.NlpEngine
import com.example.tielink.feature.agent.util.AgentWorkspace
import com.squareup.moshi.Moshi
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentToolCoordinator @Inject constructor(
    private val agentChatGateway: AgentChatGateway,
    private val agentContextRepository: AgentContextRepository,
    private val careerAgentStateRepository: CareerAgentStateRepository,
    private val resumeVersionRepository: ResumeVersionRepository,
    private val jdLibraryRepository: JdLibraryRepository,
    private val polishRepository: PolishRepository,
    private val matchScoreDetailUseCase: MatchScoreDetailUseCase,
    private val skillGapAnalyzer: SkillGapAnalyzer,
    private val quantifyAssistant: QuantifyAssistant,
    private val trackingRepository: TrackingRepository,
    private val interviewRepository: InterviewRepository,
    private val opportunityAnalyzer: OpportunityAnalyzer,
    private val moshi: Moshi,
    private val toolRegistry: AgentToolRegistry
) {
    companion object {
        private const val TAG = "AgentToolCoordinator"
        private val CAREER_PROGRESS_TOOLS = setOf(
            "analyze_boss_opportunities",
            "calculate_match",
            "optimize_resume",
            "create_application_from_current_jd",
            "get_latest_application"
        )
    }

    private val toolDefinitions: List<AgentToolDefinition> by lazy {
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
            functionTool(
                name = "create_career_goal",
                description = "创建一个需要持续推进的长期求职目标，并生成首轮可执行计划。用户明确表达目标岗位、期限或拿 Offer 目标时调用。",
                properties = mapOf(
                    "title" to stringProperty("目标标题，例如：两个月内拿到 Android 开发 Offer"),
                    "target_role" to stringProperty("目标岗位或职业方向"),
                    "success_criteria" to stringProperty("可验证的成功标准"),
                    "deadline" to stringProperty("用户表达的期限；没有期限时传空字符串")
                ),
                required = listOf("title", "target_role", "success_criteria", "deadline")
            ),
            functionTool(
                name = "get_career_plan",
                description = "读取当前长期求职目标、计划进度和下一步行动，并展示计划卡片。",
                properties = emptyMap(),
                required = emptyList()
            ),
            functionTool(
                name = "check_career_goal",
                description = "由 Agent Supervisor 检查目标健康度、阻塞项和下一步决策。用户询问状态、风险、是否卡住或是否达成时调用。",
                properties = emptyMap(),
                required = emptyList()
            ),
            functionTool(
                name = "complete_current_career_task",
                description = "当用户明确表示当前计划任务已经完成时，记录完成证据并推进到下一步。",
                properties = mapOf(
                    "evidence" to stringProperty("用户提供的完成结果或证据摘要")
                ),
                required = listOf("evidence")
            ),
            functionTool(
                name = "block_current_career_task",
                description = "当用户明确表示当前任务无法继续或缺少条件时，记录阻塞原因并尝试切换到其他可执行任务。",
                properties = mapOf(
                    "reason" to stringProperty("当前任务无法继续的具体原因")
                ),
                required = listOf("reason")
            ),
            functionTool(
                name = "replan_career_goal",
                description = "根据新方向、延期、失败结果或用户要求重新生成当前求职计划。",
                properties = mapOf(
                    "reason" to stringProperty("需要重新规划的原因和新约束")
                ),
                required = listOf("reason")
            ),
            functionTool(
                name = "achieve_career_goal",
                description = "仅当用户明确确认成功标准已经实现，例如已经拿到并接受 Offer 时，将长期目标标记为达成。",
                properties = mapOf(
                    "evidence" to stringProperty("目标达成的结果或证据")
                ),
                required = listOf("evidence")
            ),
            dynamicCardTool()
        ).let(toolRegistry::appendCustomDefinitions)
    }

    fun definitions(allowedToolNames: Set<String>? = null): List<AgentToolDefinition> {
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
        "create_career_goal" -> "正在建立长期目标和执行计划..."
        "get_career_plan" -> "正在读取目标进度和下一步..."
        "check_career_goal" -> "正在检查计划健康度和阻塞项..."
        "complete_current_career_task" -> "正在记录结果并推进计划..."
        "block_current_career_task" -> "正在记录阻塞并寻找可执行任务..."
        "replan_career_goal" -> "正在根据新情况调整计划..."
        "achieve_career_goal" -> "正在确认目标达成结果..."
        "render_card" -> "正在组织卡片内容..."
        else -> toolRegistry.definitionFor(toolName)?.progressDescription ?: "正在执行工具..."
    }

    suspend fun execute(call: AgentToolCall, fallbackUserText: String): ToolExecutionResult {
        val arguments = runCatching { JSONObject(call.arguments) }.getOrElse {
            return ToolExecutionResult("工具参数不是有效 JSON：${it.message}")
        }
        toolRegistry.definitionFor(call.name)?.let { tool ->
            return try {
                tool.execute(arguments, fallbackUserText)
            } catch (e: Exception) {
                Log.e(TAG, "自定义工具执行失败: ${call.name}", e)
                ToolExecutionResult("工具 ${call.name} 执行失败：${e.localizedMessage ?: "未知错误"}")
            }
        }
        val result = try {
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
                "create_career_goal" -> createCareerGoal(arguments)
                "get_career_plan" -> getCareerPlan()
                "check_career_goal" -> checkCareerGoal()
                "complete_current_career_task" -> completeCurrentCareerTask(arguments)
                "block_current_career_task" -> blockCurrentCareerTask(arguments)
                "replan_career_goal" -> replanCareerGoal(arguments)
                "achieve_career_goal" -> achieveCareerGoal(arguments)
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
        return observeCareerProgress(call.name, result)
    }

    private suspend fun createCareerGoal(arguments: JSONObject): ToolExecutionResult {
        val targetRole = arguments.optString("target_role").trim()
        if (targetRole.isBlank()) return ToolExecutionResult("请先明确目标岗位或职业方向。")
        val title = arguments.optString("title").ifBlank { "获得 $targetRole 机会" }
        val successCriteria = arguments.optString("success_criteria").ifBlank { "获得 $targetRole Offer" }
        val current = careerAgentStateRepository.getState()
        val updated = CareerAgentPlanner.createGoal(
            currentState = current,
            title = title,
            targetRole = targetRole,
            successCriteria = successCriteria,
            deadlineLabel = arguments.optString("deadline").ifBlank { null }
        )
        careerAgentStateRepository.saveState(updated)
        return ToolExecutionResult(
            content = "长期求职目标已建立，首轮计划已经生成。请从当前高优先级任务开始。",
            cards = listOf(buildCareerPlanCard(updated))
        )
    }

    private suspend fun getCareerPlan(): ToolExecutionResult {
        val state = careerAgentStateRepository.getState()
        if (state.activeGoal == null || state.activePlan == null) {
            return ToolExecutionResult("当前还没有长期求职目标。请先告诉我目标岗位、期限和成功标准。")
        }
        return ToolExecutionResult(
            content = "已读取当前目标和执行进度。",
            cards = listOf(buildCareerPlanCard(state))
        )
    }

    private suspend fun checkCareerGoal(): ToolExecutionResult {
        val state = careerAgentStateRepository.getState()
        if (state.activeGoal == null || state.activePlan == null) {
            return ToolExecutionResult("当前还没有长期求职目标，无法执行状态检查。")
        }
        val assessment = CareerAgentSupervisor.assess(state)
        return ToolExecutionResult(
            content = "Agent 检查完成：${assessment.headline}。${assessment.reasons.joinToString("；")}",
            cards = listOf(buildCareerPlanCard(state))
        )
    }

    private suspend fun completeCurrentCareerTask(arguments: JSONObject): ToolExecutionResult {
        val current = careerAgentStateRepository.getState()
        if (current.activePlan == null) return ToolExecutionResult("当前没有可以推进的求职计划。")
        val evidence = arguments.optString("evidence").ifBlank { "用户确认当前任务已完成" }
        val updated = CareerAgentPlanner.completeCurrentTask(current, evidence)
        if (updated == current) return ToolExecutionResult("当前计划没有处于进行中的任务，请检查或重新规划。")
        careerAgentStateRepository.saveState(updated)
        return ToolExecutionResult(
            content = "完成结果已记录，计划已经推进到下一项任务。",
            cards = listOf(buildCareerPlanCard(updated))
        )
    }

    private suspend fun blockCurrentCareerTask(arguments: JSONObject): ToolExecutionResult {
        val current = careerAgentStateRepository.getState()
        if (current.activePlan == null) return ToolExecutionResult("当前没有可以标记阻塞的求职计划。")
        val reason = arguments.optString("reason").trim()
        if (reason.isBlank()) return ToolExecutionResult("请说明当前任务无法继续的原因。")
        val updated = CareerAgentPlanner.blockCurrentTask(current, reason)
        if (updated == current) return ToolExecutionResult("当前没有处于进行中的任务，建议先检查计划状态。")
        careerAgentStateRepository.saveState(updated)
        val assessment = CareerAgentSupervisor.assess(updated)
        val nextTask = assessment.nextTask
        return ToolExecutionResult(
            content = if (nextTask != null) {
                "阻塞原因已记录，计划已切换到可并行推进的任务：${nextTask.title}。"
            } else {
                "阻塞原因已记录，当前没有其他可执行任务，需要重新规划。"
            },
            cards = listOf(buildCareerPlanCard(updated))
        )
    }

    private suspend fun replanCareerGoal(arguments: JSONObject): ToolExecutionResult {
        val current = careerAgentStateRepository.getState()
        if (current.activeGoal == null) return ToolExecutionResult("当前没有长期求职目标，无法重新规划。")
        val reason = arguments.optString("reason").ifBlank { "用户要求调整计划" }
        val updated = CareerAgentPlanner.replan(current, reason)
        careerAgentStateRepository.saveState(updated)
        return ToolExecutionResult(
            content = "原计划已归档，并根据新情况生成了第 ${updated.activePlan?.version} 版计划。",
            cards = listOf(buildCareerPlanCard(updated))
        )
    }

    private suspend fun achieveCareerGoal(arguments: JSONObject): ToolExecutionResult {
        val current = careerAgentStateRepository.getState()
        if (current.activeGoal == null) return ToolExecutionResult("当前没有可以标记达成的长期目标。")
        val evidence = arguments.optString("evidence").trim()
        if (evidence.isBlank()) return ToolExecutionResult("请提供目标已经达成的结果或证据。")
        val updated = CareerAgentPlanner.achieveGoal(current, evidence)
        if (updated == current) return ToolExecutionResult("当前目标已经是达成状态。")
        careerAgentStateRepository.saveState(updated)
        return ToolExecutionResult(
            content = "长期目标已标记为达成，结果已经写入 Agent 观察记录。",
            cards = listOf(buildCareerPlanCard(updated))
        )
    }

    private suspend fun observeCareerProgress(toolName: String, result: ToolExecutionResult): ToolExecutionResult {
        if (toolName !in CAREER_PROGRESS_TOOLS || result.content.containsFailureSignal()) return result
        val current = careerAgentStateRepository.getState()
        if (current.activePlan == null) return result
        val updated = CareerAgentPlanner.observeToolResult(current, toolName, result.content)
        if (updated != current) careerAgentStateRepository.saveState(updated)
        return result
    }

    private fun String.containsFailureSignal(): Boolean =
        listOf("缺少", "失败", "无法", "没有可", "还没有", "未知工具").any(::contains)

    private fun buildCareerPlanCard(state: CareerAgentState): UiCard.DynamicCard {
        val goal = requireNotNull(state.activeGoal)
        val plan = requireNotNull(state.activePlan)
        val assessment = CareerAgentSupervisor.assess(state)
        val completed = plan.tasks.count { it.status == CareerTaskStatus.DONE }
        val progress = if (plan.tasks.isEmpty()) 0 else completed * 100 / plan.tasks.size
        val next = state.nextTask()
        return UiCard.DynamicCard(
            title = goal.title,
            subtitle = "${goal.targetRole} · 计划 v${plan.version}",
            sections = listOf(
                DynamicCardSection(
                    type = "metrics",
                    items = listOf(
                        DynamicCardItem("计划进度", "$completed/${plan.tasks.size}", progress = progress),
                        DynamicCardItem("Agent 判断", assessment.health.displayName()),
                        DynamicCardItem("成功标准", goal.successCriteria),
                        DynamicCardItem("目标期限", goal.deadlineLabel ?: "未设置")
                    )
                ),
                DynamicCardSection(
                    type = "text",
                    title = "Agent 检查点",
                    text = buildString {
                        append(assessment.headline)
                        assessment.reasons.take(3).forEach { append("\n· $it") }
                    }
                ),
                DynamicCardSection(
                    type = "steps",
                    title = "执行计划",
                    items = plan.tasks.map { task ->
                        DynamicCardItem(
                            label = task.title,
                            value = when (task.status) {
                                CareerTaskStatus.DONE -> "已完成"
                                CareerTaskStatus.ACTIVE -> "进行中"
                                CareerTaskStatus.BLOCKED -> "受阻"
                                CareerTaskStatus.TODO -> "待开始"
                            },
                            description = task.blockingReason?.let { "${task.description}\n阻塞：$it" }
                                ?: task.description,
                            status = when (task.status) {
                                CareerTaskStatus.DONE -> "done"
                                CareerTaskStatus.ACTIVE -> "active"
                                CareerTaskStatus.BLOCKED -> "warning"
                                CareerTaskStatus.TODO -> "todo"
                            }
                        )
                    }
                ),
                DynamicCardSection(
                    type = "text",
                    title = "下一步",
                    text = next?.let { "${it.title}：${it.description}" }
                        ?: when (assessment.decision) {
                            CareerAgentDecision.REVIEW_OUTCOME -> "复盘本轮结果，并确认目标是否已经达成。"
                            CareerAgentDecision.REPLAN -> "处理阻塞条件，或根据现状生成新版计划。"
                            CareerAgentDecision.STOP -> "目标已达成，当前 Agent 执行循环已经结束。"
                            else -> "当前没有可执行的下一任务。"
                        }
                )
            ),
            actions = buildList {
                next?.let {
                        add(DynamicCardAction(label = "执行下一步", prompt = "继续执行当前求职计划的下一步：${it.title}"))
                }
                if (assessment.decision == CareerAgentDecision.REVIEW_OUTCOME) {
                    add(DynamicCardAction(label = "检查目标", prompt = "检查我的长期求职目标是否已经达成"))
                }
                if (assessment.decision != CareerAgentDecision.STOP) {
                    add(DynamicCardAction(label = "调整计划", prompt = "根据当前结果调整我的求职计划"))
                }
            }
        )
    }

    private fun CareerAgentHealth.displayName(): String = when (this) {
        CareerAgentHealth.EMPTY -> "未建立"
        CareerAgentHealth.ON_TRACK -> "正常推进"
        CareerAgentHealth.NEEDS_ATTENTION -> "需要关注"
        CareerAgentHealth.BLOCKED -> "已阻塞"
        CareerAgentHealth.CYCLE_COMPLETE -> "本轮完成"
        CareerAgentHealth.GOAL_ACHIEVED -> "目标达成"
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
    ) = AgentToolDefinition(
        function = AgentFunctionDefinition(
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

    private fun dynamicCardTool(): AgentToolDefinition = functionTool(
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
            val request = AgentChatRequest(
                messages = listOf(
                    AgentChatMessage("system", prompt),
                    AgentChatMessage("user", "请提取: ${userText.take(2000)}")
                ),
                temperature = 0.3,
                maxTokens = 300
            )
            val response = agentChatGateway.complete(request)
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
            .map { jd -> opportunityAnalyzer.rank(jd, resumeText) }
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
                            label = rank.companyLabel,
                            value = rank.positionLabel,
                            cells = listOf(
                                rank.companyLabel,
                                rank.positionLabel,
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
                            label = "${top.companyLabel} · ${top.positionLabel}",
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

        val request = AgentChatRequest(
            messages = listOf(
                AgentChatMessage("system", systemPrompt),
                AgentChatMessage("user", userPrompt)
            ),
            temperature = 0.8,
            maxTokens = 800
        )
        val response = agentChatGateway.complete(request)
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
