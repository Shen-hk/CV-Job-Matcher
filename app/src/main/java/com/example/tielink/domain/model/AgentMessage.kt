package com.example.tielink.domain.model

enum class AgentMessageRole { USER, AGENT, SYSTEM }

data class AgentMessage(
    val id: Long = System.currentTimeMillis(),
    val role: AgentMessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    /** Non-null when this message is a tool-loading placeholder */
    val toolLoadingName: String? = null,
    /** Collapsed reasoning/thinking text from extended-thinking models */
    val thinkingContent: String? = null,
    /** Non-null when this message wraps a rich UI card from a tool result */
    val card: UiCard? = null
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
    val contextBar: ContextBarState = ContextBarState(),
    val pendingAttachmentName: String? = null,
    val pendingAttachmentText: String? = null,
    val isParsingFile: Boolean = false,
    /** Accumulates thinking text while streaming; flushed into the final message on Done */
    val thinkingBuffer: String = ""
)
