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
    val thinkingBuffer: String = "",
    /** Shown below the welcome message; cleared after first user message */
    val suggestedPrompts: List<String> = emptyList()
)
