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
            updatedAt = now
        )
    }

    fun completeCurrentTask(
        state: CareerAgentState,
        evidence: String,
        now: Long = System.currentTimeMillis()
    ): CareerAgentState {
        val task = state.nextTask() ?: return state
        return completeTask(state, task, source = "user", evidence = evidence, now = now)
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

    fun observeToolResult(
        state: CareerAgentState,
        toolName: String,
        summary: String,
        now: Long = System.currentTimeMillis()
    ): CareerAgentState {
        if (
            state.activeGoal?.status != CareerGoalStatus.ACTIVE ||
            state.activePlan?.status != CareerPlanStatus.ACTIVE
        ) {
            return appendObservation(state, toolName, summary, relatedTaskId = null, now = now)
        }
        val task = state.activePlan?.tasks?.firstOrNull {
            it.status != CareerTaskStatus.DONE && it.actionTool == toolName
        }
        return if (task == null) {
            appendObservation(state, toolName, summary, relatedTaskId = null, now = now)
        } else {
            completeTask(state, task, source = toolName, evidence = summary, now = now)
        }
    }

    fun replan(
        state: CareerAgentState,
        reason: String,
        now: Long = System.currentTimeMillis()
    ): CareerAgentState {
        val goal = state.activeGoal ?: return state
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
        return state.copy(
            activeGoal = goal.copy(updatedAt = now),
            activePlan = buildPlan(goal, nextVersion, "重新规划：${reason.trim()}", now),
            planHistory = history.takeLast(MAX_PLAN_HISTORY),
            observations = (state.observations + observation).takeLast(MAX_OBSERVATIONS),
            updatedAt = now
        )
    }

    private fun completeTask(
        state: CareerAgentState,
        task: CareerTask,
        source: String,
        evidence: String,
        now: Long
    ): CareerAgentState {
        val plan = state.activePlan ?: return state
        val completedTasks = plan.tasks.map {
            if (it.id == task.id) {
                it.copy(
                    status = CareerTaskStatus.DONE,
                    blockingReason = null,
                    completionEvidence = evidence.trim().take(500),
                    completedAt = now
                )
            } else {
                it
            }
        }
        val readyTasks = activateNextTask(completedTasks)
        val planStatus = if (readyTasks.all { it.status == CareerTaskStatus.DONE }) {
            CareerPlanStatus.COMPLETED
        } else {
            CareerPlanStatus.ACTIVE
        }
        val updated = state.copy(
            activeGoal = state.activeGoal?.copy(updatedAt = now),
            activePlan = plan.copy(status = planStatus, tasks = readyTasks, updatedAt = now),
            updatedAt = now
        )
        return appendObservation(updated, source, evidence, task.id, now)
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
                actionTool = "analyze_boss_opportunities"
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
                actionTool = "optimize_resume"
            ),
            CareerTask(
                id = interview,
                title = "完成一次针对性面试训练",
                description = "基于目标岗位进行模拟面试并记录薄弱项。",
                kind = CareerTaskKind.INTERVIEW_PRACTICE,
                priority = 4,
                dependsOn = listOf(baseline),
                actionTool = "get_interview_turn"
            ),
            CareerTask(
                id = application,
                title = "执行首轮有效投递",
                description = "选择优先岗位和对应简历版本，建立可复盘的投递记录。",
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
                actionTool = "get_latest_application"
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
