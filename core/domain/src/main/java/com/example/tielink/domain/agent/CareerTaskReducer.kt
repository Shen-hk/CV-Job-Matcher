package com.example.tielink.domain.agent

import com.example.tielink.domain.model.*

/** All task transitions go through task identity, plan version and dependency checks. */
object CareerTaskReducer {
    fun canStart(state: CareerAgentState, task: CareerTask): Boolean {
        val plan = state.activePlan ?: return false
        if (state.activeGoal?.status != CareerGoalStatus.ACTIVE || plan.status != CareerPlanStatus.ACTIVE) return false
        val done = plan.tasks.filter { it.status == CareerTaskStatus.DONE }.mapTo(mutableSetOf()) { it.id }
        return task.status in setOf(CareerTaskStatus.ACTIVE, CareerTaskStatus.FAILED, CareerTaskStatus.BLOCKED) &&
            done.containsAll(task.dependsOn)
    }

    fun start(state: CareerAgentState, execution: CareerExecution): CareerAgentState {
        require(state.executions.none { it.id == execution.id }) { "执行记录已经存在" }
        val task = execution.taskId?.let { id ->
            val plan = requireNotNull(state.activePlan)
            require(plan.version == execution.planVersion && plan.goalId == execution.goalId) { "计划已更新" }
            requireNotNull(plan.tasks.find { it.id == id }) { "任务已失效" }.also {
                require(it.actionTool == execution.toolName) { "工具与任务不匹配" }
                require(canStart(state, it)) { "任务依赖尚未满足或已有执行正在进行" }
            }
        }
        val plan = state.activePlan?.let { plan ->
            plan.copy(tasks = plan.tasks.map {
                if (it.id == task?.id) it.copy(status = CareerTaskStatus.RUNNING, executionId = execution.id, blockingReason = null) else it
            }, updatedAt = execution.updatedAt)
        }
        val pending = state.executions.filter { it.status in setOf(CareerExecutionStatus.RUNNING, CareerExecutionStatus.WAITING_CONFIRMATION, CareerExecutionStatus.COMMITTING) }
        val terminal = state.executions.filter { it !in pending }.takeLast(50)
        return state.copy(activePlan = plan, executions = (pending + terminal).sortedBy { it.createdAt } + execution, updatedAt = execution.updatedAt)
    }

    fun inputProblem(state: CareerAgentState, task: CareerTask, inputs: CareerTaskInputs): String? {
        val tasks = state.activePlan?.tasks.orEmpty()
        val proofs = task.dependsOn.mapNotNull { id -> tasks.find { it.id == id }?.resultEvidence }
        if (proofs.isEmpty()) return null
        if (proofs.any { it.inputs.jdFingerprint != inputs.jdFingerprint }) return "前置任务使用的是旧 JD，请重新规划并计算匹配"
        if (task.kind == CareerTaskKind.RESUME_STRATEGY && proofs.any { it.inputs.resumeVersionId != inputs.resumeVersionId || it.inputs.resumeFingerprint != inputs.resumeFingerprint })
            return "简历已经变化，请重新建立匹配基线"
        if (task.kind == CareerTaskKind.APPLICATION && proofs.none { it.artifactId == "resume:${inputs.resumeVersionId}" &&
                (it.artifactFingerprint == null || it.artifactFingerprint == inputs.resumeFingerprint) })
            return "当前简历不是已采用的定向版本，请选择该版本或重新规划"
        return null
    }

    fun finish(state: CareerAgentState, execution: CareerExecution): CareerAgentState {
        val previous = state.executions.find { it.id == execution.id } ?: return state
        if (previous == execution) return state
        if (previous.status !in setOf(CareerExecutionStatus.RUNNING, CareerExecutionStatus.COMMITTING, CareerExecutionStatus.WAITING_CONFIRMATION)) return state
        require(previous.taskId == execution.taskId && previous.planVersion == execution.planVersion && previous.goalId == execution.goalId &&
            previous.toolName == execution.toolName) { "执行结果与原任务不匹配" }
        val observation = CareerObservation("${execution.id}-${execution.status}", execution.toolName,
            execution.summary.take(500), execution.taskId, execution.updatedAt)
        var updated = state.copy(executions = state.executions.map { if (it.id == execution.id) execution else it },
            observations = (state.observations.filter { it.id != observation.id } + observation).takeLast(50), updatedAt = execution.updatedAt)
        val plan = state.activePlan ?: return updated
        if (execution.taskId == null || plan.version != execution.planVersion || plan.goalId != execution.goalId) return updated
        val task = plan.tasks.find { it.id == execution.taskId && it.executionId == execution.id } ?: return updated
        val status = when (execution.status) {
            CareerExecutionStatus.WAITING_CONFIRMATION, CareerExecutionStatus.COMMITTING -> CareerTaskStatus.WAITING_CONFIRMATION
            CareerExecutionStatus.SUCCEEDED -> if (execution.evidence?.let { it.artifactId.isNotBlank() && it.inputs == execution.inputs } == true &&
                (task.completionPolicy == CareerCompletionPolicy.TOOL_RESULT || previous.status == CareerExecutionStatus.COMMITTING))
                CareerTaskStatus.DONE else CareerTaskStatus.ACTIVE
            CareerExecutionStatus.FAILED -> CareerTaskStatus.FAILED
            CareerExecutionStatus.BLOCKED -> CareerTaskStatus.BLOCKED
            CareerExecutionStatus.CANCELLED, CareerExecutionStatus.REJECTED -> CareerTaskStatus.ACTIVE
            CareerExecutionStatus.RUNNING -> CareerTaskStatus.RUNNING
        }
        val changed = task.copy(
            status = status,
            blockingReason = execution.summary.takeIf { status == CareerTaskStatus.BLOCKED || status == CareerTaskStatus.FAILED },
            resultEvidence = execution.evidence.takeIf { status == CareerTaskStatus.DONE },
            completionEvidence = execution.evidence?.summary.takeIf { status == CareerTaskStatus.DONE },
            completedAt = execution.updatedAt.takeIf { status == CareerTaskStatus.DONE }
        )
        val tasks = activateReady(plan.tasks.map { if (it.id == task.id) changed else it })
        updated = updated.copy(activePlan = plan.copy(
            tasks = tasks,
            status = if (tasks.all { it.status == CareerTaskStatus.DONE }) CareerPlanStatus.COMPLETED else CareerPlanStatus.ACTIVE,
            updatedAt = execution.updatedAt
        ))
        return updated
    }

    fun activateReady(tasks: List<CareerTask>): List<CareerTask> {
        if (tasks.any { it.status in setOf(CareerTaskStatus.ACTIVE, CareerTaskStatus.RUNNING) }) return tasks
        val done = tasks.filter { it.status == CareerTaskStatus.DONE }.mapTo(mutableSetOf()) { it.id }
        val next = tasks.filter { it.status == CareerTaskStatus.TODO && done.containsAll(it.dependsOn) }.minByOrNull { it.priority }
        return tasks.map { if (it.id == next?.id) it.copy(status = CareerTaskStatus.ACTIVE) else it }
    }

    fun requireValidConfirmation(state: CareerAgentState, execution: CareerExecution, inputs: CareerTaskInputs) {
        require(state.executions.any { it.id == execution.id && it.status == execution.status }) { "确认记录已变化" }
        require(execution.status in setOf(CareerExecutionStatus.WAITING_CONFIRMATION, CareerExecutionStatus.COMMITTING)) { "这次确认已经处理或失效" }
        require(execution.inputs == inputs) { "JD 或简历已变化，请重新生成或检查结果" }
        if (execution.goalId != null) {
            require(state.activeGoal?.id == execution.goalId && state.activeGoal?.status == CareerGoalStatus.ACTIVE) { "求职目标已变化" }
            require(state.activePlan?.version == execution.planVersion) { "计划已更新，请使用新版计划" }
        }
        execution.taskId?.let { id ->
            require(state.activePlan?.tasks?.any { it.id == id && it.executionId == execution.id && it.status == CareerTaskStatus.WAITING_CONFIRMATION } == true) { "任务已变化" }
        }
    }

    /** Never retry a write automatically after process death. COMMITTING is reconciled against its Room receipt. */
    fun recoverInterrupted(state: CareerAgentState, now: Long): CareerAgentState {
        var recovered = state
        state.executions.filter { it.status == CareerExecutionStatus.RUNNING }.forEach {
            recovered = finish(recovered, it.copy(status = CareerExecutionStatus.FAILED, summary = "上次执行中断，可手动重试", updatedAt = now))
        }
        return recovered
    }
}
