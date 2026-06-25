package com.example.tielink.domain.model

/**
 * 面试对话中的一条消息。
 */
data class InterviewMessage(
    val id: Long = 0,
    val sessionId: Long,
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isHint: Boolean = false,       // 是否为提示消息（非直接答案）
    val isEvaluation: Boolean = false   // 是否为评估/反馈消息
)

enum class MessageRole {
    USER,        // 求职者
    INTERVIEWER, // 面试官
    SYSTEM       // 系统通知（提示、换题、时间提醒等）
}
