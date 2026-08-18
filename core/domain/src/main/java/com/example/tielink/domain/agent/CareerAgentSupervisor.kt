package com.example.tielink.domain.agent

import com.example.tielink.domain.model.CareerAgentState
import com.example.tielink.domain.model.CareerGoalStatus
import com.example.tielink.domain.model.CareerPlanStatus
import com.example.tielink.domain.model.CareerTask
import com.example.tielink.domain.model.CareerTaskStatus

enum class CareerAgentHealth {
    EMPTY,
    ON_TRACK,
    NEEDS_ATTENTION,
    BLOCKED,
    CYCLE_COMPLETE,
    GOAL_ACHIEVED
}

enum class CareerAgentDecision {
    CREATE_GOAL,
    EXECUTE_NEXT,
    REPLAN,
    REVIEW_OUTCOME,
    STOP
}

data class CareerAgentAssessment(
    val health: CareerAgentHealth,
    val decision: CareerAgentDecision,
    val headline: String,
    val reasons: List<String>,
    val nextTask: CareerTask? = null
)

object CareerAgentSupervisor {
    fun assess(state: CareerAgentState): CareerAgentAssessment {
        val goal = state.activeGoal
        val plan = state.activePlan
        if (goal == null || plan == null) {
            return CareerAgentAssessment(
                health = CareerAgentHealth.EMPTY,
                decision = CareerAgentDecision.CREATE_GOAL,
                headline = "尚未建立长期目标",
                reasons = listOf("需要先明确目标岗位、期限和成功标准")
            )
        }
        if (goal.status == CareerGoalStatus.ACHIEVED) {
            return CareerAgentAssessment(
                health = CareerAgentHealth.GOAL_ACHIEVED,
                decision = CareerAgentDecision.STOP,
                headline = "长期目标已经达成",
                reasons = state.observations.lastOrNull { it.source == "goal_achieved" }
                    ?.let { listOf(it.summary) }
                    ?: listOf(goal.successCriteria)
            )
        }
        if (plan.status == CareerPlanStatus.COMPLETED) {
            return CareerAgentAssessment(
                health = CareerAgentHealth.CYCLE_COMPLETE,
                decision = CareerAgentDecision.REVIEW_OUTCOME,
                headline = "本轮执行计划已经完成",
                reasons = listOf("需要对照成功标准复盘结果，再决定达成目标或开启下一轮")
            )
        }

        val blockedTasks = plan.tasks.filter { it.status == CareerTaskStatus.BLOCKED }
        val nextTask = state.nextTask()
        if (nextTask != null) {
            val hasRisk = blockedTasks.isNotEmpty()
            return CareerAgentAssessment(
                health = if (hasRisk) CareerAgentHealth.NEEDS_ATTENTION else CareerAgentHealth.ON_TRACK,
                decision = CareerAgentDecision.EXECUTE_NEXT,
                headline = if (hasRisk) "计划可继续，但存在阻塞项" else "计划正在正常推进",
                reasons = buildList {
                    add("下一步：${nextTask.title}")
                    blockedTasks.forEach { add("${it.title}：${it.blockingReason ?: "等待处理"}") }
                },
                nextTask = nextTask
            )
        }
        if (blockedTasks.isNotEmpty()) {
            return CareerAgentAssessment(
                health = CareerAgentHealth.BLOCKED,
                decision = CareerAgentDecision.REPLAN,
                headline = "当前没有可继续执行的任务",
                reasons = blockedTasks.map { "${it.title}：${it.blockingReason ?: "等待处理"}" }
            )
        }
        return CareerAgentAssessment(
            health = CareerAgentHealth.NEEDS_ATTENTION,
            decision = CareerAgentDecision.REPLAN,
            headline = "计划状态需要修复",
            reasons = listOf("计划未完成，但没有处于进行中的任务")
        )
    }
}
