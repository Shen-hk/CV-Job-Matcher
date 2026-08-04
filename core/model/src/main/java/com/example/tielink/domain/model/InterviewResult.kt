package com.example.tielink.domain.model

data class InterviewResult(
    val overallScore: Float = 0f,
    val dimensionScores: List<DimensionScore> = emptyList(),
    val improvements: List<String> = emptyList(),
    val highlights: List<String> = emptyList(),
    val keyMoments: List<KeyMoment> = emptyList(),
    val recommendedResumeEdits: List<String> = emptyList()
)

data class DimensionScore(
    val name: String,
    val score: Float,
    val comment: String = ""
)

data class KeyMoment(
    val messageIndex: Int,
    val type: MomentType,
    val description: String,
    val suggestion: String = ""
)

enum class MomentType {
    HIGHLIGHT,
    MISTAKE,
    IMPROVE
}
