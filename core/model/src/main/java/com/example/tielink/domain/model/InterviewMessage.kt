package com.example.tielink.domain.model

data class InterviewMessage(
    val id: Long = 0,
    val sessionId: Long,
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isHint: Boolean = false,
    val isEvaluation: Boolean = false
)

enum class MessageRole {
    USER,
    INTERVIEWER,
    SYSTEM
}
