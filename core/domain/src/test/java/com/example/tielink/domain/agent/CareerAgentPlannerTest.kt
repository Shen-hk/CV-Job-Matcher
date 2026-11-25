package com.example.tielink.domain.agent

import com.example.tielink.domain.model.CareerAgentState
import com.example.tielink.domain.model.CareerGoalStatus
import com.example.tielink.domain.model.CareerPlanStatus
import com.example.tielink.domain.model.CareerTaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CareerAgentPlannerTest {
    private fun observe(state: CareerAgentState, toolName: String, summary: String, now: Long): CareerAgentState {
        val task = requireNotNull(state.nextTask())
        require(task.actionTool == toolName)
        val inputs = com.example.tielink.domain.model.CareerTaskInputs(1, "jd", 2, "resume")
        val execution = com.example.tielink.domain.model.CareerExecution("run-$now", toolName, "{}", inputs,
            taskId = task.id, planVersion = state.activePlan?.version, goalId = state.activeGoal?.id,
            createdAt = now, updatedAt = now)
        var started = CareerTaskReducer.start(state, execution)
        if (task.completionPolicy == com.example.tielink.domain.model.CareerCompletionPolicy.USER_CONFIRMATION) {
            started = CareerTaskReducer.finish(started, execution.copy(status = com.example.tielink.domain.model.CareerExecutionStatus.WAITING_CONFIRMATION))
            started = CareerTaskReducer.finish(started, execution.copy(status = com.example.tielink.domain.model.CareerExecutionStatus.COMMITTING))
        }
        return CareerTaskReducer.finish(started, execution.copy(
            summary = summary,
            status = com.example.tielink.domain.model.CareerExecutionStatus.SUCCEEDED,
            evidence = com.example.tielink.domain.model.CareerTaskEvidence(summary, "artifact-$now", inputs)))
    }

    @Test
    fun createGoalBuildsAnActivePlanWithOneNextAction() {
        val state = CareerAgentPlanner.createGoal(
            currentState = CareerAgentState(),
            title = "两个月拿到 Android Offer",
            targetRole = "Android 开发工程师",
            successCriteria = "获得 Offer",
            deadlineLabel = "两个月内",
            now = 100L
        )

        assertEquals("Android 开发工程师", state.activeGoal?.targetRole)
        assertEquals(6, state.activePlan?.tasks?.size)
        assertEquals(1, state.activePlan?.tasks?.count { it.status == CareerTaskStatus.ACTIVE })
        assertEquals("analyze_jd", state.nextTask()?.actionTool)
    }

    @Test
    fun toolObservationCompletesMatchingTaskAndAdvancesPlan() {
        val initial = CareerAgentPlanner.createGoal(
            CareerAgentState(), "求职", "Android", "获得 Offer", null, now = 100L
        )

        val updated = observe(
            initial,
            toolName = "analyze_jd",
            summary = "已分析岗位池",
            now = 200L
        )

        assertEquals(CareerTaskStatus.DONE, updated.activePlan?.tasks?.first()?.status)
        assertEquals("calculate_match", updated.nextTask()?.actionTool)
        assertEquals("已分析岗位池", updated.observations.last().summary)
    }

    @Test
    fun replanArchivesOldPlanAndIncrementsVersion() {
        val initial = CareerAgentPlanner.createGoal(
            CareerAgentState(), "求职", "Android", "获得 Offer", null, now = 100L
        )
        val replanned = CareerAgentPlanner.replan(initial, "目标改为 AI 应用开发", now = 300L)

        assertEquals(2, replanned.activePlan?.version)
        assertEquals(CareerPlanStatus.SUPERSEDED, replanned.planHistory.last().status)
        assertNotEquals(initial.activePlan?.id, replanned.activePlan?.id)
        assertTrue(replanned.observations.last().summary.contains("AI 应用开发"))
    }

    @Test
    fun completeWorkflowClosesThePlanAfterEveryStageHasEvidence() {
        var state = CareerAgentPlanner.createGoal(
            CareerAgentState(), "求职", "Android", "获得 Offer", null, now = 100L
        )
        state = observe(state, "analyze_jd", "岗位样本已分析", 200L)
        state = observe(state, "calculate_match", "匹配基线已建立", 300L)
        state = observe(state, "optimize_resume", "定向简历已生成", 400L)

        assertEquals("get_interview_turn", state.nextTask()?.actionTool)
        state = observe(state, "get_interview_turn", "已完成模拟面试并记录薄弱项", 500L)
        state = observe(
            state,
            "create_application_from_current_jd",
            "首轮投递已记录",
            600L
        )
        state = observe(state, "get_latest_application", "投递结果已复盘", 700L)

        assertEquals(CareerPlanStatus.COMPLETED, state.activePlan?.status)
        assertEquals(null, state.nextTask())
        assertEquals(6, state.activePlan?.tasks?.count { it.status == CareerTaskStatus.DONE })
        assertEquals(CareerGoalStatus.ACTIVE, state.activeGoal?.status)
        assertEquals(CareerAgentHealth.CYCLE_COMPLETE, CareerAgentSupervisor.assess(state).health)
        assertEquals(CareerAgentDecision.REVIEW_OUTCOME, CareerAgentSupervisor.assess(state).decision)
    }

    @Test
    fun blockingOneTaskSwitchesToAnAvailableParallelTask() {
        var state = CareerAgentPlanner.createGoal(
            CareerAgentState(), "求职", "Android", "获得 Offer", null, now = 100L
        )
        state = observe(state, "analyze_jd", "岗位已分析", 200L)
        state = observe(state, "calculate_match", "基线已完成", 300L)

        assertEquals("optimize_resume", state.nextTask()?.actionTool)
        state = CareerAgentPlanner.blockCurrentTask(state, "还缺少一段项目经历", 400L)

        assertEquals("get_interview_turn", state.nextTask()?.actionTool)
        assertEquals(
            "还缺少一段项目经历",
            state.activePlan?.tasks?.first { it.status == CareerTaskStatus.BLOCKED }?.blockingReason
        )
        assertEquals(CareerAgentHealth.NEEDS_ATTENTION, CareerAgentSupervisor.assess(state).health)
    }

    @Test
    fun supervisorRequestsReplanWhenTheOnlyRunnableTaskIsBlocked() {
        val initial = CareerAgentPlanner.createGoal(
            CareerAgentState(), "求职", "Android", "获得 Offer", null, now = 100L
        )
        val blocked = CareerAgentPlanner.blockCurrentTask(initial, "岗位池为空", 200L)
        val assessment = CareerAgentSupervisor.assess(blocked)

        assertEquals(CareerAgentHealth.BLOCKED, assessment.health)
        assertEquals(CareerAgentDecision.REPLAN, assessment.decision)
        assertEquals(null, assessment.nextTask)
    }

    @Test
    fun achievingGoalStopsFurtherExecution() {
        val initial = CareerAgentPlanner.createGoal(
            CareerAgentState(), "求职", "Android", "获得 Offer", null, now = 100L
        )
        val achieved = CareerAgentPlanner.achieveGoal(initial, "已收到并接受 Offer", 200L)
        val assessment = CareerAgentSupervisor.assess(achieved)

        assertEquals(CareerGoalStatus.ACHIEVED, achieved.activeGoal?.status)
        assertEquals(null, achieved.nextTask())
        assertEquals(CareerAgentHealth.GOAL_ACHIEVED, assessment.health)
        assertEquals(CareerAgentDecision.STOP, assessment.decision)
    }
}
