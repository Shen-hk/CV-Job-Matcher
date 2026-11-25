package com.example.tielink.domain.usecase

import com.example.tielink.data.repository.*
import com.example.tielink.domain.agent.*
import com.example.tielink.domain.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Serializes attempts; the pure reducer owns task transitions and Room owns business commits. */
@Singleton
class CareerTaskExecutor @Inject constructor(
    private val states: CareerAgentStateRepository,
    private val tools: AgentToolCoordinator,
    private val context: AgentContextRepository,
    private val resumes: ResumeVersionRepository,
    private val tracking: TrackingRepository,
    private val interviews: InterviewRepository
) {
    private val mutex = Mutex()
    private var recovered = false

    suspend fun execute(call: AgentToolCall, userText: String, taskId: String? = null, planVersion: Int? = null): ToolExecutionResult = mutex.withLock {
        recover()
        if (call.name in setOf("complete_current_career_task", "achieve_career_goal")) {
            return@withLock requestConfirmation(call)
        }
        if (call.name in setOf("create_career_goal", "get_career_plan", "check_career_goal", "block_current_career_task", "replan_career_goal", "render_card", "show_resume_preview", "generate_greeting")) {
            return@withLock tools.execute(call, userText, UUID.randomUUID().toString())
        }
        val inputs = currentInputs()
        invalidateStaleConfirmations(inputs)
        val state = states.getState()
        val task = if (taskId != null) {
            require(state.activePlan?.version == planVersion) { "计划已更新，请刷新后执行" }
            requireNotNull(state.activePlan?.tasks?.find { it.id == taskId && it.actionTool == call.name }) { "任务已失效" }
        } else state.nextTask()?.takeIf { it.actionTool == call.name }
            ?: state.activePlan?.takeIf { it.status == CareerPlanStatus.ACTIVE && state.activeGoal?.status == CareerGoalStatus.ACTIVE }
                ?.tasks?.firstOrNull { it.actionTool == call.name && it.status in setOf(
                CareerTaskStatus.FAILED, CareerTaskStatus.BLOCKED, CareerTaskStatus.RUNNING, CareerTaskStatus.WAITING_CONFIRMATION) }
        val arguments = JSONObject(call.arguments)
        val fallbackField = when (call.name) { "analyze_jd" -> "text"; "optimize_resume" -> "instruction"; else -> null }
        if (fallbackField != null && arguments.optString(fallbackField).isBlank()) arguments.put(fallbackField, userText)
        val signature = signature(call.copy(arguments = arguments.toString()))
        val reusable = state.executions.lastOrNull {
            it.toolName == call.name && it.argumentsSignature == signature && it.inputs == inputs && it.taskId == task?.id &&
                it.planVersion == task?.let { state.activePlan?.version } &&
                (it.status == CareerExecutionStatus.WAITING_CONFIRMATION ||
                    (task != null && it.status == CareerExecutionStatus.SUCCEEDED && it.evidence != null))
        }
        if (reusable != null) return@withLock resultFor(reusable)
        if (task != null) require(CareerTaskReducer.canStart(state, task)) { "前置任务尚未完成，或本任务正在执行" }
        val now = System.currentTimeMillis()
        val execution = CareerExecution(
            id = UUID.randomUUID().toString(), toolName = call.name, argumentsSignature = signature, inputs = inputs,
            taskId = task?.id, planVersion = task?.let { state.activePlan?.version }, goalId = task?.let { state.activeGoal?.id },
            createdAt = now, updatedAt = now
        )
        states.updateState { CareerTaskReducer.start(it, execution) }
        try {
            val missing = task?.let { CareerTaskReducer.inputProblem(state, it, inputs) } ?: when (call.name) {
                "analyze_jd" -> if (JSONObject(call.arguments).optString("text").ifBlank { userText }.isBlank()) "需要先选择或导入目标 JD" else null
                "calculate_match", "create_application_from_current_jd" -> when {
                    inputs.jdFingerprint.isBlank() -> "需要先选择或导入目标 JD"
                    inputs.resumeFingerprint.isBlank() -> "需要先选择或上传简历"
                    else -> null
                }
                "optimize_resume" -> when {
                    inputs.resumeFingerprint.isBlank() -> "需要先选择或上传简历"
                    task != null && inputs.jdFingerprint.isBlank() -> "定向简历需要目标 JD"
                    else -> null
                }
                else -> null
            }
            val result = if (missing == null) tools.execute(call, userText, execution.id)
                else ToolExecutionResult(missing, status = ToolOutcomeStatus.BLOCKED,
                    cards = listOf(UiCard.DynamicCard("补充任务资料", sections = listOf(DynamicCardSection("text", text = missing)),
                        actions = listOf(if (missing.contains("重新") || missing.contains("基线"))
                            DynamicCardAction("调整计划", prompt = "根据当前资料重新规划我的求职计划")
                            else if (missing.contains("JD")) DynamicCardAction("选择目标 JD", type = DynamicCardAction.TYPE_OPEN_JD_LIBRARY)
                            else DynamicCardAction("选择简历", type = DynamicCardAction.TYPE_OPEN_RESUME_LIBRARY)))))
            val freshInputs = currentInputs()
            // analyze_jd deliberately changes JD. Other tools must not complete against changed inputs.
            val changed = call.name != "analyze_jd" && freshInputs != inputs
            val finished = execution.copy(
                status = when {
                    changed -> CareerExecutionStatus.FAILED
                    result.status == ToolOutcomeStatus.FAILED -> CareerExecutionStatus.FAILED
                    result.status == ToolOutcomeStatus.BLOCKED -> CareerExecutionStatus.BLOCKED
                    result.candidate != null -> CareerExecutionStatus.WAITING_CONFIRMATION
                    else -> CareerExecutionStatus.SUCCEEDED
                },
                inputs = if (call.name == "analyze_jd") freshInputs else inputs,
                summary = if (changed) "执行期间 JD 或简历发生变化，请重新执行" else result.content,
                candidate = result.candidate.takeUnless { changed },
                confirmationKind = if (result.candidate != null && !changed) CareerConfirmationKind.RESUME_ADOPTION else null,
                evidence = result.artifactId?.takeIf { !changed && result.status == ToolOutcomeStatus.SUCCESS && task?.completionPolicy == CareerCompletionPolicy.TOOL_RESULT }
                    ?.let { CareerTaskEvidence(result.content, it, if (call.name == "analyze_jd") freshInputs else inputs) },
                updatedAt = System.currentTimeMillis()
            )
            val updated = states.updateState { CareerTaskReducer.finish(it, finished) }
            if (changed) return@withLock resultFor(finished)
            val extra = if (finished.status == CareerExecutionStatus.WAITING_CONFIRMATION) resultFor(finished).cards
                else if (task != null && updated.activePlan != null) listOf(tools.buildCareerPlanCard(updated)) else emptyList()
            result.copy(cards = result.cards + extra)
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { states.updateState { CareerTaskReducer.finish(it,
                execution.copy(status = CareerExecutionStatus.CANCELLED, summary = "执行已取消，可重新执行", updatedAt = System.currentTimeMillis())) } }
            throw cancelled
        } catch (error: Exception) {
            val failed = execution.copy(status = CareerExecutionStatus.FAILED, summary = error.message ?: "执行失败", updatedAt = System.currentTimeMillis())
            val updated = states.updateState { CareerTaskReducer.finish(it, failed) }
            ToolExecutionResult(failed.summary, status = ToolOutcomeStatus.FAILED,
                cards = updated.activePlan?.let { listOf(tools.buildCareerPlanCard(updated)) }.orEmpty())
        }
    }

    suspend fun executeTask(taskId: String, planVersion: Int): ToolExecutionResult {
        val state = states.getState()
        require(state.activePlan?.version == planVersion) { "计划已更新" }
        val task = requireNotNull(state.activePlan?.tasks?.find { it.id == taskId }) { "任务已失效" }
        val toolName = requireNotNull(task.actionTool)
        val arguments = JSONObject()
        if (toolName == "analyze_jd") arguments.put("text", context.getAgentContext().currentJdText.orEmpty())
        if (toolName == "optimize_resume") arguments.put("instruction", "围绕当前 JD 和求职目标生成定向简历，不编造经历")
        return execute(AgentToolCall(UUID.randomUUID().toString(), toolName, arguments.toString()), task.description, taskId, planVersion)
    }

    suspend fun confirm(executionId: String): ToolExecutionResult = mutex.withLock {
        recover()
        val state = states.getState()
        val execution = requireNotNull(state.executions.find { it.id == executionId }) { "确认记录不存在" }
        if (execution.status == CareerExecutionStatus.SUCCEEDED) return@withLock resultFor(execution)
        val inputs = currentInputs()
        try {
            CareerTaskReducer.requireValidConfirmation(state, execution, inputs)
        } catch (invalid: IllegalArgumentException) {
            val updated = states.updateState { CareerTaskReducer.finish(it, execution.copy(status = CareerExecutionStatus.REJECTED,
                summary = invalid.message ?: "旧确认已失效", updatedAt = System.currentTimeMillis())) }
            return@withLock ToolExecutionResult(invalid.message ?: "旧确认已失效", status = ToolOutcomeStatus.BLOCKED,
                cards = updated.activePlan?.let { listOf(tools.buildCareerPlanCard(updated)) }.orEmpty())
        }
        if (execution.confirmationKind == CareerConfirmationKind.TASK_COMPLETION) validateManualEvidence(execution)
        val committing = execution.copy(status = CareerExecutionStatus.COMMITTING, updatedAt = System.currentTimeMillis())
        states.updateState { current ->
            CareerTaskReducer.requireValidConfirmation(current, execution, inputs)
            CareerTaskReducer.finish(current, committing)
        }
        var businessCommitted = false
        try {
            // Once the user confirms, reconcile the local transaction even if the screen is closed.
            withContext(NonCancellable) {
                val resultId = if (execution.confirmationKind == CareerConfirmationKind.RESUME_ADOPTION) {
                    resumes.insertAndActivateOnce(execution.id, requireNotNull(execution.candidate).version, execution.inputs)
                } else execution.resultId
                businessCommitted = true
                val proof = if (resultId != null && execution.confirmationKind == CareerConfirmationKind.RESUME_ADOPTION)
                    CareerTaskEvidence("用户采用定向简历，新版本保存成功", "resume:$resultId", execution.inputs,
                        CareerInputIdentity.fingerprint(requireNotNull(execution.candidate).version.cleanedText))
                    else execution.evidence
                val finished = committing.copy(status = CareerExecutionStatus.SUCCEEDED, resultId = resultId,
                    evidence = proof, summary = if (resultId != null && execution.candidate != null) "已采用并保存简历版本" else "用户确认结果已记录",
                    updatedAt = System.currentTimeMillis())
                val updated = states.updateState { current ->
                    val changed = CareerTaskReducer.finish(current, finished)
                    if (execution.confirmationKind == CareerConfirmationKind.GOAL_ACHIEVEMENT)
                        CareerAgentPlanner.achieveGoal(changed, execution.summary) else changed
                }
                resultFor(finished).let { it.copy(cards = it.cards + updated.activePlan?.let { listOf(tools.buildCareerPlanCard(updated)) }.orEmpty()) }
            }
        } catch (error: Exception) {
            if (businessCommitted) {
                // Keep COMMITTING so the next action can reconcile the durable receipt.
                recovered = false
                ToolExecutionResult("结果已提交，计划状态同步中断；重新进入会话后将恢复", status = ToolOutcomeStatus.FAILED)
            } else {
                withContext(NonCancellable) { states.updateState { CareerTaskReducer.finish(it,
                    execution.copy(summary = "保存未完成：${error.message}，可重试确认", updatedAt = System.currentTimeMillis())) } }
                ToolExecutionResult("保存未完成：${error.message}，候选稿仍然保留", status = ToolOutcomeStatus.FAILED, cards = resultFor(execution).cards)
            }
        }
    }

    suspend fun reject(executionId: String): ToolExecutionResult = mutex.withLock {
        recover()
        val state = states.getState()
        val execution = requireNotNull(state.executions.find { it.id == executionId }) { "确认记录不存在" }
        require(execution.status == CareerExecutionStatus.WAITING_CONFIRMATION) { "这次确认已处理" }
        val updated = states.updateState { CareerTaskReducer.finish(it, execution.copy(status = CareerExecutionStatus.REJECTED,
            summary = "用户暂不采用，可重新执行任务", updatedAt = System.currentTimeMillis())) }
        ToolExecutionResult(if (execution.candidate != null) "已保留原简历，未保存候选稿为新版本" else "暂不确认完成，可继续处理任务",
            cards = updated.activePlan?.let { listOf(tools.buildCareerPlanCard(updated)) }.orEmpty())
    }

    suspend fun restorePending(): List<UiCard> = mutex.withLock {
        recover()
        invalidateStaleConfirmations(currentInputs())
        val state = states.getState()
        state.executions.filter { it.status == CareerExecutionStatus.WAITING_CONFIRMATION }.flatMap { resultFor(it).cards } +
            if (state.activePlan?.tasks?.any { it.status == CareerTaskStatus.FAILED || it.status == CareerTaskStatus.BLOCKED } == true)
                listOf(tools.buildCareerPlanCard(state)) else emptyList()
    }

    private suspend fun requestConfirmation(call: AgentToolCall): ToolExecutionResult {
        val state = states.getState()
        val goal = state.activeGoal ?: return ToolExecutionResult("请先建立求职目标", status = ToolOutcomeStatus.BLOCKED)
        val goalConfirmation = call.name == "achieve_career_goal"
        val task = if (goalConfirmation) null else state.nextTask()?.takeIf {
            it.kind in setOf(CareerTaskKind.INTERVIEW_PRACTICE, CareerTaskKind.FOLLOW_UP)
        }
        val existing = state.executions.lastOrNull { it.status == CareerExecutionStatus.WAITING_CONFIRMATION &&
            it.goalId == goal.id && it.planVersion == state.activePlan?.version &&
            (if (goalConfirmation) it.confirmationKind == CareerConfirmationKind.GOAL_ACHIEVEMENT
                else it.confirmationKind == CareerConfirmationKind.TASK_COMPLETION && (task == null || it.taskId == task.id)) }
        if (existing != null) return resultFor(existing)
        if (!goalConfirmation && (task == null || task.kind !in setOf(CareerTaskKind.INTERVIEW_PRACTICE, CareerTaskKind.FOLLOW_UP))) {
            return ToolExecutionResult("请先执行当前任务，业务任务需有实际结果才能完成", status = ToolOutcomeStatus.BLOCKED)
        }
        val summary = JSONObject(call.arguments).optString("evidence").trim()
        if (summary.isBlank()) return ToolExecutionResult("请补充完成结果或复盘结论", status = ToolOutcomeStatus.BLOCKED)
        val inputs = currentInputs()
        val now = System.currentTimeMillis()
        var execution = CareerExecution(UUID.randomUUID().toString(), task?.actionTool ?: call.name, signature(call), inputs,
            taskId = task?.id, planVersion = state.activePlan?.version, goalId = goal.id, summary = summary,
            confirmationKind = if (goalConfirmation) CareerConfirmationKind.GOAL_ACHIEVEMENT else CareerConfirmationKind.TASK_COMPLETION,
            createdAt = now, updatedAt = now)
        if (task?.kind == CareerTaskKind.INTERVIEW_PRACTICE) {
            val session = interviews.getAllSessionsFlow().first().firstOrNull { !it.isActive && it.overallScore != null &&
                it.resumeVersionId == inputs.resumeVersionId && CareerInputIdentity.fingerprint(it.jdRawText.orEmpty()) == inputs.jdFingerprint }
                ?: return ToolExecutionResult("需要先结束目标岗位的模拟面试并生成评估", status = ToolOutcomeStatus.BLOCKED)
            execution = execution.copy(resultId = session.id, evidence = CareerTaskEvidence(summary, "interview:${session.id}", inputs))
        } else if (task?.kind == CareerTaskKind.FOLLOW_UP) {
            val applicationProof = state.activePlan?.tasks?.firstOrNull {
                it.id in task.dependsOn && it.kind == CareerTaskKind.APPLICATION
            }?.resultEvidence
            val recordId = applicationProof?.artifactId?.removePrefix("tracking:")?.toLongOrNull()
            val item = recordId?.let { tracking.getById(it) }?.takeIf {
                CareerInputIdentity.fingerprint(it.jdRawText) == inputs.jdFingerprint &&
                    it.resumeVersionId == applicationProof?.inputs?.resumeVersionId
            } ?: return ToolExecutionResult("需要当前计划保存的岗位及简历版本投递记录", status = ToolOutcomeStatus.BLOCKED)
            execution = execution.copy(resultId = item.id, evidence = CareerTaskEvidence(summary, "review:${item.id}", inputs))
        }
        val pending = execution.copy(status = CareerExecutionStatus.WAITING_CONFIRMATION)
        states.updateState { CareerTaskReducer.finish(CareerTaskReducer.start(it, execution), pending) }
        return resultFor(pending)
    }

    private suspend fun validateManualEvidence(execution: CareerExecution) {
        val proof = requireNotNull(execution.evidence) { "缺少完成证据" }
        if (proof.artifactId.startsWith("interview:")) {
            val session = interviews.getSessionById(requireNotNull(execution.resultId))
            require(session != null && !session.isActive && session.overallScore != null &&
                session.resumeVersionId == execution.inputs.resumeVersionId &&
                CareerInputIdentity.fingerprint(session.jdRawText.orEmpty()) == execution.inputs.jdFingerprint) { "面试评估已变化" }
        } else {
            val item = tracking.getById(requireNotNull(execution.resultId))
            require(item != null && CareerInputIdentity.fingerprint(item.jdRawText) == execution.inputs.jdFingerprint) { "投递记录已删除或变化" }
        }
    }

    private suspend fun currentInputs(): CareerTaskInputs {
        val ctx = context.getAgentContext()
        val resume = resumes.getActive()
        val text = resume?.let { if (it.isPolished) it.cleanedText.ifBlank { it.rawText } else it.rawText.ifBlank { it.cleanedText } }
        return CareerInputIdentity.snapshot(ctx.currentJdId, ctx.currentJdText, resume?.id, text)
    }

    private suspend fun recover() {
        if (recovered) return
        val inputs = currentInputs()
        // A tracking write may have committed just before the process died.
        states.getState().executions.filter { it.status == CareerExecutionStatus.RUNNING && it.toolName == "create_application_from_current_jd" }.forEach { execution ->
            tracking.committedItemId(execution.id)?.let { id ->
                val compatible = inputs == execution.inputs
                states.updateState { CareerTaskReducer.finish(it, execution.copy(status = if (compatible)
                    CareerExecutionStatus.SUCCEEDED else CareerExecutionStatus.FAILED,
                    evidence = CareerTaskEvidence("投递记录保存凭据已恢复", "tracking:$id", execution.inputs),
                    summary = if (compatible) "已恢复保存成功的投递记录" else "旧资料的投递记录已保存，当前资料已变化，请检查并重规划",
                    resultId = id, updatedAt = System.currentTimeMillis())) }
            }
        }
        val state = states.updateState { CareerTaskReducer.recoverInterrupted(it, System.currentTimeMillis()) }
        state.executions.filter { it.status == CareerExecutionStatus.COMMITTING }.forEach { execution ->
            val id = if (execution.candidate != null) resumes.committedVersionId(execution.id) else null
            val outputFingerprint = execution.candidate?.let { CareerInputIdentity.fingerprint(it.version.cleanedText) }
            val compatible = inputs.jdId == execution.inputs.jdId && inputs.jdFingerprint == execution.inputs.jdFingerprint &&
                inputs.resumeVersionId == id && inputs.resumeFingerprint == outputFingerprint
            val finished = if (id != null) execution.copy(status = if (compatible) CareerExecutionStatus.SUCCEEDED else CareerExecutionStatus.FAILED, resultId = id,
                evidence = CareerTaskEvidence("保存凭据已恢复", "resume:$id", execution.inputs,
                    outputFingerprint),
                summary = if (compatible) "已恢复保存成功的简历" else "简历已保存，但当前资料已变化，请检查并重规划", updatedAt = System.currentTimeMillis())
                else execution.copy(status = CareerExecutionStatus.WAITING_CONFIRMATION, summary = "上次保存中断，请重新确认", updatedAt = System.currentTimeMillis())
            states.updateState { CareerTaskReducer.finish(it, finished) }
        }
        recovered = true
    }

    private suspend fun invalidateStaleConfirmations(inputs: CareerTaskInputs) {
        states.updateState { state ->
            var updated = state
            state.executions.filter { it.status == CareerExecutionStatus.WAITING_CONFIRMATION && (it.inputs != inputs ||
                (it.goalId != null && (it.goalId != state.activeGoal?.id || state.activeGoal?.status != CareerGoalStatus.ACTIVE ||
                    it.planVersion != state.activePlan?.version))) }.forEach { execution ->
                updated = CareerTaskReducer.finish(updated, execution.copy(status = CareerExecutionStatus.REJECTED,
                    summary = "JD 或简历已变化，旧结果待重新生成", updatedAt = System.currentTimeMillis()))
            }
            updated
        }
    }

    private fun resultFor(execution: CareerExecution): ToolExecutionResult {
        val actions = if (execution.status == CareerExecutionStatus.WAITING_CONFIRMATION) listOf(
            DynamicCardAction(if (execution.candidate != null) "采用并保存" else "确认完成", type = DynamicCardAction.TYPE_CONFIRM_EXECUTION, executionId = execution.id),
            DynamicCardAction(if (execution.candidate != null) "暂不采用" else "暂不确认", type = DynamicCardAction.TYPE_REJECT_EXECUTION, executionId = execution.id)
        ) else emptyList()
        val cards = mutableListOf<UiCard>()
        val candidate = execution.candidate
        if (candidate != null && execution.status in setOf(CareerExecutionStatus.WAITING_CONFIRMATION, CareerExecutionStatus.SUCCEEDED)) {
            val version = candidate.version
            cards += UiCard.ResumePreviewCard(version.name, execution.resultId ?: 0L, version.cleanedText,
                candidate.resumeJson.takeIf { it.isNotBlank() }?.let(ResumeData::fromJsonString) ?: ResumeData.fromPolishedText(version.cleanedText))
        }
        cards += UiCard.DynamicCard(
            title = if (execution.status == CareerExecutionStatus.WAITING_CONFIRMATION) "请检查并确认结果" else "任务执行结果",
            sections = listOf(DynamicCardSection("text", text = execution.summary)), actions = actions
        )
        return ToolExecutionResult(execution.summary, cards,
            status = when (execution.status) {
                CareerExecutionStatus.FAILED -> ToolOutcomeStatus.FAILED
                CareerExecutionStatus.BLOCKED, CareerExecutionStatus.CANCELLED, CareerExecutionStatus.REJECTED -> ToolOutcomeStatus.BLOCKED
                else -> ToolOutcomeStatus.SUCCESS
            })
    }

    companion object {
        fun signature(call: AgentToolCall): String = ToolCallIdentity.signature(call.name, call.arguments)
    }
}
