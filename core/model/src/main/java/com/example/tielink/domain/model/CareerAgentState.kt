package com.example.tielink.domain.model

enum class CareerGoalStatus { ACTIVE, PAUSED, ACHIEVED }

enum class CareerPlanStatus { ACTIVE, COMPLETED, SUPERSEDED }

enum class CareerTaskStatus { TODO, ACTIVE, DONE, BLOCKED }

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
    val completedAt: Long? = null
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
    val updatedAt: Long = 0L
) {
    fun nextTask(): CareerTask? {
        if (activeGoal?.status != CareerGoalStatus.ACTIVE) return null
        if (activePlan?.status != CareerPlanStatus.ACTIVE) return null
        return activePlan.tasks.firstOrNull {
            it.status == CareerTaskStatus.ACTIVE
        }
    }
}
