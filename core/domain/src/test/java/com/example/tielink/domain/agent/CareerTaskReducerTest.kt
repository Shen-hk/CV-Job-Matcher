package com.example.tielink.domain.agent

import com.example.tielink.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class CareerTaskReducerTest {
    private val inputs = CareerTaskInputs(1, "jd", 2, "resume")
    private fun initial() = CareerAgentPlanner.createGoal(CareerAgentState(), "求职", "Android", "获得 Offer", "两个月", 100)
    private fun execution(state: CareerAgentState, id: String = "run"): CareerExecution {
        val task = requireNotNull(state.nextTask())
        return CareerExecution(id, requireNotNull(task.actionTool), "{}", inputs, task.id,
            state.activePlan?.version, state.activeGoal?.id, createdAt = 200, updatedAt = 200)
    }
    private fun success(state: CareerAgentState, id: String): CareerAgentState {
        val run = execution(state, id)
        return CareerTaskReducer.finish(CareerTaskReducer.start(state, run), run.copy(
            status = CareerExecutionStatus.SUCCEEDED, evidence = CareerTaskEvidence("实际结果", "artifact:$id", inputs)))
    }

    @Test fun dependenciesCannotBeBypassedByCallingALaterTool() {
        val state = initial()
        val task = state.activePlan!!.tasks[1]
        val run = execution(state).copy(taskId = task.id, toolName = task.actionTool!!)
        assertThrows(IllegalArgumentException::class.java) { CareerTaskReducer.start(state, run) }
        assertEquals(CareerTaskStatus.TODO, state.activePlan!!.tasks[1].status)
    }

    @Test fun failuresKeepEvidenceEmptyAndDoNotAdvanceDependentTasks() {
        val state = initial()
        val run = execution(state)
        val failed = CareerTaskReducer.finish(CareerTaskReducer.start(state, run), run.copy(status = CareerExecutionStatus.FAILED, summary = "网络失败"))
        assertEquals(CareerTaskStatus.FAILED, failed.activePlan!!.tasks[0].status)
        assertEquals(CareerTaskStatus.TODO, failed.activePlan!!.tasks[1].status)
        assertNull(failed.activePlan!!.tasks[0].completionEvidence)
    }

    @Test fun informationalReadCannotCompleteATask() {
        val state = initial()
        val run = execution(state)
        val finished = CareerTaskReducer.finish(CareerTaskReducer.start(state, run), run.copy(status = CareerExecutionStatus.SUCCEEDED, summary = "已读取"))
        assertEquals(CareerTaskStatus.ACTIVE, finished.nextTask()?.status)
        assertEquals(0, finished.activePlan!!.tasks.count { it.status == CareerTaskStatus.DONE })
    }

    @Test fun candidateNeedsAConfirmedCommitBeforeTaskCompletion() {
        val state = success(success(initial(), "jd"), "match")
        val run = execution(state)
        val started = CareerTaskReducer.start(state, run)
        val proof = CareerTaskEvidence("候选稿已生成", "resume:3", inputs)
        val unconfirmed = CareerTaskReducer.finish(started, run.copy(status = CareerExecutionStatus.SUCCEEDED, evidence = proof))
        assertEquals(CareerTaskStatus.ACTIVE, unconfirmed.nextTask()?.status)
        val waiting = CareerTaskReducer.finish(started, run.copy(status = CareerExecutionStatus.WAITING_CONFIRMATION))
        assertEquals(CareerTaskStatus.WAITING_CONFIRMATION, waiting.activePlan!!.tasks[2].status)
        val committing = CareerTaskReducer.finish(waiting, run.copy(status = CareerExecutionStatus.COMMITTING))
        val done = CareerTaskReducer.finish(committing, run.copy(status = CareerExecutionStatus.SUCCEEDED, evidence = proof))
        assertEquals(CareerTaskStatus.DONE, done.activePlan!!.tasks[2].status)
    }

    @Test fun repeatedResultCannotAdvanceAnotherTask() {
        val state = initial()
        val run = execution(state)
        val result = run.copy(status = CareerExecutionStatus.SUCCEEDED, evidence = CareerTaskEvidence("JD 已保存", "jd:1", inputs))
        val done = CareerTaskReducer.finish(CareerTaskReducer.start(state, run), result)
        assertEquals(done, CareerTaskReducer.finish(done, result))
        assertEquals("calculate_match", done.nextTask()?.actionTool)
    }

    @Test fun changedInputsRejectOldConfirmation() {
        val state = success(success(initial(), "jd"), "match")
        val run = execution(state)
        val pending = run.copy(status = CareerExecutionStatus.WAITING_CONFIRMATION)
        val waiting = CareerTaskReducer.finish(CareerTaskReducer.start(state, run), pending)
        assertThrows(IllegalArgumentException::class.java) {
            CareerTaskReducer.requireValidConfirmation(waiting, pending, inputs.copy(jdFingerprint = "another JD"))
        }
    }

    @Test fun replanAppliesNewDirectionAndInvalidatesPreviousConfirmations() {
        val state = success(success(initial(), "jd"), "match")
        val run = execution(state)
        val pending = run.copy(status = CareerExecutionStatus.WAITING_CONFIRMATION)
        val waiting = CareerTaskReducer.finish(CareerTaskReducer.start(state, run), pending)
        val updated = CareerAgentPlanner.replan(waiting, "转向后端", 300, targetRole = "Java 后端", deadlineLabel = "三个月", inputs = inputs)
        assertEquals("Java 后端", updated.activeGoal!!.targetRole)
        assertEquals("三个月", updated.activeGoal!!.deadlineLabel)
        assertEquals(0, updated.activePlan!!.tasks.count { it.status == CareerTaskStatus.DONE })
        assertEquals(CareerExecutionStatus.REJECTED, updated.executions.last().status)
        assertThrows(IllegalArgumentException::class.java) { CareerTaskReducer.requireValidConfirmation(updated, pending, inputs) }
    }

    @Test fun postponingKeepsCompatibleCompletedArtifacts() {
        val state = success(success(initial(), "jd"), "match")
        val updated = CareerAgentPlanner.replan(state, "延期", 300, deadlineLabel = "三个月", inputs = inputs)
        assertEquals(2, updated.activePlan!!.tasks.count { it.status == CareerTaskStatus.DONE })
        assertEquals("optimize_resume", updated.nextTask()?.actionTool)
    }

    @Test fun rejectionPreservesTheSourceAndMakesTaskRunnableAgain() {
        val state = success(success(initial(), "jd"), "match")
        val run = execution(state)
        val waiting = CareerTaskReducer.finish(CareerTaskReducer.start(state, run), run.copy(status = CareerExecutionStatus.WAITING_CONFIRMATION))
        val rejected = CareerTaskReducer.finish(waiting, run.copy(status = CareerExecutionStatus.REJECTED))
        assertEquals(CareerTaskStatus.ACTIVE, rejected.nextTask()?.status)
        assertEquals(2, rejected.activePlan!!.tasks.count { it.status == CareerTaskStatus.DONE })
    }

    @Test fun interruptedExecutionRecoversAsRetryableFailure() {
        val state = initial()
        val started = CareerTaskReducer.start(state, execution(state))
        val recovered = CareerTaskReducer.recoverInterrupted(started, 500)
        assertEquals(CareerTaskStatus.FAILED, recovered.activePlan!!.tasks[0].status)
        assertTrue(CareerTaskReducer.canStart(recovered, recovered.activePlan!!.tasks[0]))
        assertEquals(recovered, CareerTaskReducer.recoverInterrupted(recovered, 600))
    }

    @Test fun wrongInputEvidenceCannotCompleteATask() {
        val state = initial()
        val run = execution(state)
        val done = CareerTaskReducer.finish(CareerTaskReducer.start(state, run), run.copy(status = CareerExecutionStatus.SUCCEEDED,
            evidence = CareerTaskEvidence("旧结果", "jd:1", inputs.copy(jdFingerprint = "old"))))
        assertEquals(0, done.activePlan!!.tasks.count { it.status == CareerTaskStatus.DONE })
    }

    private fun adoptedState(): CareerAgentState {
        val baseline = success(success(initial(), "jd"), "match")
        val run = execution(baseline, "adopt")
        val waiting = CareerTaskReducer.finish(CareerTaskReducer.start(baseline, run),
            run.copy(status = CareerExecutionStatus.WAITING_CONFIRMATION))
        val committing = CareerTaskReducer.finish(waiting, run.copy(status = CareerExecutionStatus.COMMITTING))
        return CareerTaskReducer.finish(committing, run.copy(status = CareerExecutionStatus.SUCCEEDED,
            evidence = CareerTaskEvidence("采用新版本", "resume:3", inputs, "polished")))
    }

    @Test fun postponingAfterAdoptionRetainsBaselineAndAdoptedVersion() {
        val current = inputs.copy(resumeVersionId = 3, resumeFingerprint = "polished")
        val updated = CareerAgentPlanner.replan(adoptedState(), "延长期限", 500,
            deadlineLabel = "四个月", inputs = current)
        assertEquals(3, updated.activePlan!!.tasks.count { it.status == CareerTaskStatus.DONE })
        assertEquals("get_interview_turn", updated.nextTask()?.actionTool)
        assertEquals("resume:3", updated.activePlan!!.tasks[2].resultEvidence?.artifactId)
    }

    @Test fun editingAdoptedVersionInvalidatesDependentArtifacts() {
        val edited = inputs.copy(resumeVersionId = 3, resumeFingerprint = "edited")
        val updated = CareerAgentPlanner.replan(adoptedState(), "内容已修改", 500, inputs = edited)
        assertEquals(1, updated.activePlan!!.tasks.count { it.status == CareerTaskStatus.DONE })
        assertEquals("calculate_match", updated.nextTask()?.actionTool)
        assertNotNull(CareerTaskReducer.inputProblem(adoptedState(), adoptedState().activePlan!!.tasks[4], edited))
    }

    @Test fun changingJdInvalidatesAdoptedVersionAndItsPrerequisites() {
        val updated = CareerAgentPlanner.replan(adoptedState(), "换岗位", 500,
            inputs = inputs.copy(jdId = 4, jdFingerprint = "new-jd", resumeVersionId = 3, resumeFingerprint = "polished"))
        assertEquals(0, updated.activePlan!!.tasks.count { it.status == CareerTaskStatus.DONE })
        assertEquals("analyze_jd", updated.nextTask()?.actionTool)
    }

    @Test fun staleExecutionResultDoesNotAdvanceTheNewPlan() {
        val run = execution(initial())
        val started = CareerTaskReducer.start(initial(), run)
        val replanned = CareerAgentPlanner.replan(started, "调整方向", 400, targetRole = "后端", inputs = inputs)
        val result = CareerTaskReducer.finish(replanned, run.copy(status = CareerExecutionStatus.SUCCEEDED,
            evidence = CareerTaskEvidence("旧执行结果", "jd:1", inputs)))
        assertEquals(replanned.activePlan, result.activePlan)
        assertEquals(0, result.activePlan!!.tasks.count { it.status == CareerTaskStatus.DONE })
    }

    @Test fun waitingForResumeAdoptionAllowsIndependentInterviewWork() {
        val baseline = success(success(initial(), "jd"), "match")
        val run = execution(baseline)
        val waiting = CareerTaskReducer.finish(CareerTaskReducer.start(baseline, run),
            run.copy(status = CareerExecutionStatus.WAITING_CONFIRMATION))
        assertEquals("get_interview_turn", waiting.nextTask()?.actionTool)
        assertFalse(CareerTaskReducer.canStart(waiting, waiting.activePlan!!.tasks[4]))
    }
}
