package com.example.tielink.domain.agent

import com.example.tielink.domain.model.CareerAgentState
import com.example.tielink.domain.model.CareerGoal
import com.example.tielink.domain.model.CareerGoalStatus
import com.example.tielink.domain.model.CareerPlan
import com.example.tielink.domain.model.CareerPlanStatus
import com.example.tielink.domain.model.CareerObservation
import com.example.tielink.domain.model.CareerTask
import com.example.tielink.domain.model.CareerTaskKind
import com.example.tielink.domain.model.CareerTaskStatus
import com.example.tielink.domain.model.CareerCompletionPolicy
import com.example.tielink.domain.model.CareerTaskInputs
import com.example.tielink.domain.model.CareerExecutionStatus

object CareerAgentPlanner {
    private const val MAX_OBSERVATIONS = 50
    private const val MAX_PLAN_HISTORY = 5

    fun createGoal(
        currentState: CareerAgentState,
        title: String,
        targetRole: String,
        successCriteria: String,
        deadlineLabel: String?,
        now: Long = System.currentTimeMillis()
    ): CareerAgentState {
        require(currentState.executions.none { it.status == CareerExecutionStatus.COMMITTING }) { "正在保存结果，请稍后建立新目标" }
        val goalId = "goal-$now"
        val goal = CareerGoal(
            id = goalId,
            title = title.trim(),
            targetRole = targetRole.trim(),
            successCriteria = successCriteria.trim(),
            deadlineLabel = deadlineLabel?.trim()?.ifBlank { null },
            createdAt = now,
            updatedAt = now
        )
        val archived = currentState.activePlan?.let {
            currentState.planHistory + it.copy(status = CareerPlanStatus.SUPERSEDED, updatedAt = now)
        } ?: currentState.planHistory

        return CareerAgentState(
            activeGoal = goal,
            activePlan = buildPlan(goal, version = 1, reason = "根据目标建立首轮求职执行计划", now = now),
            planHistory = archived.takeLast(MAX_PLAN_HISTORY),
            observations = currentState.observations,
            executions = currentState.executions,
            updatedAt = now
        )
    }

    fun blockCurrentTask(
        state: CareerAgentState,
        reason: String,
        now: Long = System.currentTimeMillis()
    ): CareerAgentState {
        val task = state.nextTask() ?: return state
        val plan = state.activePlan ?: return state
        val blockedTasks = plan.tasks.map {
            if (it.id == task.id) {
                it.copy(
                    status = CareerTaskStatus.BLOCKED,
                    blockingReason = reason.trim().take(500)
                )
            } else {
                it
            }
        }
        val updated = state.copy(
            activeGoal = state.activeGoal?.copy(updatedAt = now),
            activePlan = plan.copy(tasks = activateNextTask(blockedTasks), updatedAt = now),
            updatedAt = now
        )
        return appendObservation(updated, "blocker", reason, task.id, now)
    }

    fun achieveGoal(
        state: CareerAgentState,
        evidence: String,
        now: Long = System.currentTimeMillis()
    ): CareerAgentState {
        val goal = state.activeGoal ?: return state
        if (goal.status == CareerGoalStatus.ACHIEVED) return state
        val plan = state.activePlan?.let {
            if (it.status == CareerPlanStatus.ACTIVE) {
                it.copy(status = CareerPlanStatus.SUPERSEDED, updatedAt = now)
            } else {
                it
            }
        }
        val updated = state.copy(
            activeGoal = goal.copy(status = CareerGoalStatus.ACHIEVED, updatedAt = now),
            activePlan = plan,
            updatedAt = now
        )
        return appendObservation(updated, "goal_achieved", evidence, relatedTaskId = null, now = now)
    }

    fun replan(
        state: CareerAgentState,
        reason: String,
        now: Long = System.currentTimeMillis(),
        targetRole: String? = null,
        deadlineLabel: String? = null,
        successCriteria: String? = null,
        inputs: CareerTaskInputs? = null
    ): CareerAgentState {
        val goal = state.activeGoal ?: return state
        require(goal.status == CareerGoalStatus.ACTIVE) { "目标已结束，请建立新目标" }
        require(state.executions.none { it.status == CareerExecutionStatus.COMMITTING }) { "正在保存结果，请稍后调整计划" }
        val updatedGoal = goal.copy(
            targetRole = targetRole?.trim()?.takeIf { it.isNotBlank() } ?: goal.targetRole,
            deadlineLabel = deadlineLabel?.trim()?.takeIf { it.isNotBlank() } ?: goal.deadlineLabel,
            successCriteria = successCriteria?.trim()?.takeIf { it.isNotBlank() } ?: goal.successCriteria,
            updatedAt = now
        )
        val oldPlan = state.activePlan
        val nextVersion = (oldPlan?.version ?: 0) + 1
        val history = if (oldPlan == null) {
            state.planHistory
        } else {
            state.planHistory + oldPlan.copy(status = CareerPlanStatus.SUPERSEDED, updatedAt = now)
        }
        val observation = CareerObservation(
            id = "observation-$now",
            source = "replan",
            summary = reason.trim(),
            createdAt = now
        )
        val fresh = buildPlan(updatedGoal, nextVersion, "重新规划：${reason.trim()}", now)
        val adopted = oldPlan?.tasks?.find { it.kind == CareerTaskKind.RESUME_STRATEGY && it.status == CareerTaskStatus.DONE }?.resultEvidence
        val adoptedStillCurrent = adopted != null && (inputs == null ||
            (adopted.inputs.jdFingerprint == inputs.jdFingerprint && adopted.artifactId == "resume:${inputs.resumeVersionId}" &&
                (adopted.artifactFingerprint == null || adopted.artifactFingerprint == inputs.resumeFingerprint)))
        val retained = fresh.tasks.map { task ->
            val previous = oldPlan?.tasks?.find { it.kind == task.kind && it.status == CareerTaskStatus.DONE }
            val proof = previous?.resultEvidence
            val sameRole = updatedGoal.targetRole == goal.targetRole
            val compatible = previous != null && proof != null && sameRole && when (task.kind) {
                CareerTaskKind.MARKET_RESEARCH -> inputs == null || proof.inputs.jdFingerprint == inputs.jdFingerprint
                // A baseline remains valid history when its confirmed result is the active resume.
                CareerTaskKind.PROFILE_ASSESSMENT -> inputs == null || proof.inputs == inputs ||
                    (adoptedStillCurrent && adopted?.inputs == proof.inputs)
                CareerTaskKind.RESUME_STRATEGY -> adoptedStillCurrent
                CareerTaskKind.INTERVIEW_PRACTICE -> inputs == null || proof.inputs == inputs
                CareerTaskKind.APPLICATION, CareerTaskKind.FOLLOW_UP -> inputs == null || proof.inputs.jdFingerprint == inputs.jdFingerprint
            }
            if (compatible) task.copy(status = CareerTaskStatus.DONE, completionEvidence = previous?.completionEvidence,
                resultEvidence = proof, completedAt = previous?.completedAt) else task.copy(status = CareerTaskStatus.TODO)
        }
        // Retained artifacts cannot bypass a newly invalidated prerequisite.
        val valid = retained.toMutableList()
        valid.indices.forEach { index ->
            val completed = valid.filter { it.status == CareerTaskStatus.DONE }.mapTo(mutableSetOf()) { it.id }
            val task = valid[index]
            if (task.status == CareerTaskStatus.DONE && !completed.containsAll(task.dependsOn))
                valid[index] = task.copy(status = CareerTaskStatus.TODO, resultEvidence = null, completionEvidence = null, completedAt = null)
        }
        return state.copy(
            activeGoal = updatedGoal,
            activePlan = fresh.copy(tasks = activateNextTask(valid),
                status = if (valid.all { it.status == CareerTaskStatus.DONE }) CareerPlanStatus.COMPLETED else CareerPlanStatus.ACTIVE),
            executions = state.executions.map { if (it.status == CareerExecutionStatus.WAITING_CONFIRMATION)
                it.copy(status = CareerExecutionStatus.REJECTED, summary = "计划已更新，旧确认已失效", updatedAt = now) else it },
            planHistory = history.takeLast(MAX_PLAN_HISTORY),
            observations = (state.observations + observation).takeLast(MAX_OBSERVATIONS),
            updatedAt = now
        )
    }

    private fun appendObservation(
        state: CareerAgentState,
        source: String,
        summary: String,
        relatedTaskId: String?,
        now: Long
    ): CareerAgentState {
        val observation = CareerObservation(
            id = "observation-$now-${state.observations.size}",
            source = source,
            summary = summary.trim().take(500),
            relatedTaskId = relatedTaskId,
            createdAt = now
        )
        return state.copy(
            observations = (state.observations + observation).takeLast(MAX_OBSERVATIONS),
            updatedAt = now
        )
    }

    private fun buildPlan(goal: CareerGoal, version: Int, reason: String, now: Long): CareerPlan {
        val prefix = "${goal.id}-v$version"
        val market = "$prefix-market"
        val baseline = "$prefix-baseline"
        val resume = "$prefix-resume"
        val interview = "$prefix-interview"
        val application = "$prefix-application"
        val followUp = "$prefix-follow-up"
        val tasks = listOf(
            CareerTask(
                id = market,
                title = "建立目标岗位样本",
                description = "收集并分析 ${goal.targetRole} 的代表性岗位，确认真实要求。",
                kind = CareerTaskKind.MARKET_RESEARCH,
                status = CareerTaskStatus.ACTIVE,
                priority = 1,
                actionTool = "analyze_jd"
            ),
            CareerTask(
                id = baseline,
                title = "评估当前能力基线",
                description = "用当前简历和目标 JD 建立可解释的匹配基线。",
                kind = CareerTaskKind.PROFILE_ASSESSMENT,
                priority = 2,
                dependsOn = listOf(market),
                actionTool = "calculate_match"
            ),
            CareerTask(
                id = resume,
                title = "形成简历策略版本",
                description = "围绕目标岗位补强证据，并保存一版可追踪的定向简历。",
                kind = CareerTaskKind.RESUME_STRATEGY,
                priority = 3,
                dependsOn = listOf(baseline),
                actionTool = "optimize_resume",
                completionPolicy = CareerCompletionPolicy.USER_CONFIRMATION
            ),
            CareerTask(
                id = interview,
                title = "完成一次针对性面试训练",
                description = "基于目标岗位进行模拟面试并记录薄弱项。",
                kind = CareerTaskKind.INTERVIEW_PRACTICE,
                priority = 4,
                dependsOn = listOf(baseline),
                actionTool = "get_interview_turn",
                completionPolicy = CareerCompletionPolicy.USER_CONFIRMATION
            ),
            CareerTask(
                id = application,
                title = "保存首轮投递记录",
                description = "选择岗位和简历版本保存记录；实际投递状态由用户提供。",
                kind = CareerTaskKind.APPLICATION,
                priority = 5,
                dependsOn = listOf(resume),
                actionTool = "create_application_from_current_jd"
            ),
            CareerTask(
                id = followUp,
                title = "复盘结果并决定下一轮动作",
                description = "检查投递进展，根据过筛和面试结果调整策略。",
                kind = CareerTaskKind.FOLLOW_UP,
                priority = 6,
                dependsOn = listOf(application),
                actionTool = "get_latest_application",
                completionPolicy = CareerCompletionPolicy.USER_CONFIRMATION
            )
        )
        return CareerPlan(
            id = "plan-$prefix",
            goalId = goal.id,
            version = version,
            rationale = reason,
            tasks = tasks,
            createdAt = now,
            updatedAt = now
        )
    }

    private fun activateNextTask(tasks: List<CareerTask>): List<CareerTask> {
        if (tasks.any { it.status == CareerTaskStatus.ACTIVE }) return tasks
        val completedIds = tasks.filter { it.status == CareerTaskStatus.DONE }.mapTo(mutableSetOf()) { it.id }
        val next = tasks
            .filter { it.status == CareerTaskStatus.TODO && completedIds.containsAll(it.dependsOn) }
            .minByOrNull { it.priority }
            ?: return tasks
        return tasks.map { if (it.id == next.id) it.copy(status = CareerTaskStatus.ACTIVE) else it }
    }
}
