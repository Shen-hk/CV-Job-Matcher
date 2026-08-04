package com.example.tielink.domain.model

data class AgentIntent(
    val type: IntentType,
    val toolCall: ToolCall? = null,
    val clarificationNeeded: Boolean = false,
    val clarificationPrompt: String? = null
)

enum class IntentType {
    JD_ANALYZE,
    RESUME_EDIT,
    MATCH,
    INTERVIEW,
    TRACKING,
    PLATFORM,
    DEBRIEF,
    CHAT
}

data class ToolCall(
    val toolName: String,
    val function: String,
    val params: Map<String, Any>
)
