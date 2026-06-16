package com.example.cv_jobmatcher.domain.model

/**
 * 面试结束后的综合评估结果。
 */
data class InterviewResult(
    val overallScore: Float = 0f,           // 0-100 综合评分
    val dimensionScores: List<DimensionScore> = emptyList(),
    val improvements: List<String> = emptyList(),
    val highlights: List<String> = emptyList(),  // 亮点
    val keyMoments: List<KeyMoment> = emptyList(), // 关键片段标记
    val recommendedResumeEdits: List<String> = emptyList() // AI建议的简历修改点
)

data class DimensionScore(
    val name: String,        // 如"表达清晰度"
    val score: Float,        // 0-100
    val comment: String = "" // 评语
)

data class KeyMoment(
    val messageIndex: Int,   // 消息序号
    val type: MomentType,    // 亮点/失误
    val description: String,
    val suggestion: String = ""
)

enum class MomentType {
    HIGHLIGHT,  // 亮点
    MISTAKE,    // 失误
    IMPROVE     // 可改进
}
