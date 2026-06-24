package com.example.cv_jobmatcher.domain.model

enum class AgentMessageRole { USER, AGENT, SYSTEM }

data class AgentMessage(
    val id: Long = 0,
    val role: AgentMessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false
)

data class ContextBarState(
    val jdTitle: String? = null,
    val jdCompany: String? = null,
    val resumeVersionName: String? = null,
    val resumeVersionId: Long? = null
)

data class AgentChatUiState(
    val messages: List<AgentMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val isStreaming: Boolean = false,
    val error: String? = null,
    val contextBar: ContextBarState = ContextBarState()
)
