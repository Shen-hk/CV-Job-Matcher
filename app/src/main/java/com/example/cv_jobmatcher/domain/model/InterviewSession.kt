package com.example.cv_jobmatcher.domain.model

/**
 * 一次模拟面试会话。
 */
data class InterviewSession(
    val id: Long = 0,
    val personaType: InterviewPersona = InterviewPersona.MILD_TECH,
    val jdRawText: String = "",
    val resumeVersionId: Long? = null,
    val resumeText: String = "",
    val isActive: Boolean = true,
    val questionCount: Int = 0,
    val overallScore: Float? = null,
    val dimensionScores: Map<String, Float> = emptyMap(),
    val improvements: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

enum class InterviewPersona(val displayName: String, val description: String) {
    MILD_TECH("温和技术官", "友好引导式提问，关注技术深度与项目经验"),
    PRESSURE("压力面考官", "高压追问连击，测试抗压能力与临场反应"),
    FOREIGN_HR("外企HR", "Behavioral面试风格，STAR法则导向"),
    STATE_STRUCTURED("国企结构化", "标准化结构化面试，综合素质评估"),
    CUSTOM("自定义", "用户自定义面试偏好");
}
