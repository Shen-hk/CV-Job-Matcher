package com.example.tielink.domain.model

enum class CareerGoalStatus { ACTIVE, PAUSED, ACHIEVED }

enum class CareerPlanStatus { ACTIVE, COMPLETED, SUPERSEDED }

// ACTIVE retains its persisted name and means ready to execute, not running.
enum class CareerTaskStatus { TODO, ACTIVE, RUNNING, WAITING_CONFIRMATION, DONE, BLOCKED, FAILED }

enum class CareerCompletionPolicy { TOOL_RESULT, USER_CONFIRMATION }

enum class CareerExecutionStatus { RUNNING, WAITING_CONFIRMATION, COMMITTING, SUCCEEDED, FAILED, BLOCKED, CANCELLED, REJECTED }

enum class CareerConfirmationKind { RESUME_ADOPTION, TASK_COMPLETION, GOAL_ACHIEVEMENT }

enum class ToolOutcomeStatus { SUCCESS, FAILED, BLOCKED }

/** Fingerprints keep personal text out of the execution identity. */
data class CareerTaskInputs(
    val jdId: Long? = null,
    val jdFingerprint: String = "",
    val resumeVersionId: Long? = null,
    val resumeFingerprint: String = ""
)

data class CareerTaskEvidence(
    val summary: String,
    val artifactId: String,
    val inputs: CareerTaskInputs,
    val artifactFingerprint: String? = null
)

data class ResumeCandidate(
    val version: ResumeVersion,
    val resumeJson: String = ""
)

/** A durable attempt also carries the candidate awaiting a user decision. */
data class CareerExecution(
    val id: String,
    val toolName: String,
    val argumentsSignature: String,
    val inputs: CareerTaskInputs,
    val taskId: String? = null,
    val planVersion: Int? = null,
    val goalId: String? = null,
    val status: CareerExecutionStatus = CareerExecutionStatus.RUNNING,
    val summary: String = "",
    val evidence: CareerTaskEvidence? = null,
    val candidate: ResumeCandidate? = null,
    val confirmationKind: CareerConfirmationKind? = null,
    val resultId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
)

enum class CareerTaskKind {
    MARKET_RESEARCH,
    PROFILE_ASSESSMENT,
    RESUME_STRATEGY,
    INTERVIEW_PRACTICE,
    APPLICATION,
    FOLLOW_UP
}

data class CareerGoal(
    val id: String,
    val title: String,
    val targetRole: String,
    val successCriteria: String,
    val deadlineLabel: String? = null,
    val status: CareerGoalStatus = CareerGoalStatus.ACTIVE,
    val createdAt: Long,
    val updatedAt: Long
)

data class CareerTask(
    val id: String,
    val title: String,
    val description: String,
    val kind: CareerTaskKind,
    val status: CareerTaskStatus = CareerTaskStatus.TODO,
    val priority: Int,
    val dependsOn: List<String> = emptyList(),
    val actionTool: String? = null,
    val blockingReason: String? = null,
    val completionEvidence: String? = null,
    val completedAt: Long? = null,
    val completionPolicy: CareerCompletionPolicy = CareerCompletionPolicy.TOOL_RESULT,
    val resultEvidence: CareerTaskEvidence? = null,
    val executionId: String? = null
)

data class CareerPlan(
    val id: String,
    val goalId: String,
    val version: Int,
    val rationale: String,
    val status: CareerPlanStatus = CareerPlanStatus.ACTIVE,
    val tasks: List<CareerTask>,
    val createdAt: Long,
    val updatedAt: Long
)

data class CareerObservation(
    val id: String,
    val source: String,
    val summary: String,
    val relatedTaskId: String? = null,
    val createdAt: Long
)

data class CareerAgentState(
    val activeGoal: CareerGoal? = null,
    val activePlan: CareerPlan? = null,
    val planHistory: List<CareerPlan> = emptyList(),
    val observations: List<CareerObservation> = emptyList(),
    val updatedAt: Long = 0L,
    val revision: Long = 0L,
    val executions: List<CareerExecution> = emptyList()
) {
    fun nextTask(): CareerTask? {
        if (activeGoal?.status != CareerGoalStatus.ACTIVE) return null
        if (activePlan?.status != CareerPlanStatus.ACTIVE) return null
        return activePlan.tasks.firstOrNull {
            it.status == CareerTaskStatus.ACTIVE
        }
    }
}
