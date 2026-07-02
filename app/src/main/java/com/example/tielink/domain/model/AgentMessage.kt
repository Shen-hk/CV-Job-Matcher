package com.example.tielink.domain.model

import java.util.concurrent.atomic.AtomicLong

private val idCounter = AtomicLong(System.currentTimeMillis())

enum class AgentMessageRole { USER, AGENT, SYSTEM }

data class AgentMessage(
    val id: Long = idCounter.incrementAndGet(),
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

enum class AgentProcessStage {
    IDLE,
    THINKING,
    RETRIEVING,
    DRAWING,
    TEXT_GENERATION,
    INTERRUPTED
}

data class AgentProcessState(
    val stage: AgentProcessStage = AgentProcessStage.IDLE,
    val title: String = "",
    val detail: String = "",
    val sourceLabel: String? = null,
    val sourceBreakdown: List<String> = emptyList(),
    val canCancel: Boolean = false
) {
    val isActive: Boolean
        get() = stage != AgentProcessStage.IDLE
}

data class AgentChatUiState(
    val messages: List<AgentMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val isStreaming: Boolean = false,
    val error: String? = null,
    val contextBar: ContextBarState = ContextBarState(),
    val processState: AgentProcessState = AgentProcessState(),
    val pendingAttachmentName: String? = null,
    val pendingAttachmentText: String? = null,
    val isParsingFile: Boolean = false,
    val thinkingBuffer: String = "",
    /** Shown below the welcome message; cleared after first user message */
    val suggestedPrompts: List<String> = emptyList()
)

data class PersistedAgentMessage(
    val role: AgentMessageRole,
    val content: String,
    val timestamp: Long,
    val thinkingContent: String? = null
)

data class PersistedAgentChatDraft(
    val messages: List<PersistedAgentMessage> = emptyList(),
    val inputText: String = "",
    val pendingAttachmentName: String? = null,
    val pendingAttachmentText: String? = null,
    val lastSavedAt: Long = System.currentTimeMillis()
)
