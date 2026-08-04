package com.example.tielink.domain.model

/** Converts transient chat UI state into a durable, schema-controlled draft. */
object AgentDraftSnapshotFactory {
    fun create(state: AgentChatUiState, historyId: Long?): PersistedAgentChatDraft {
        val persistedMessages = state.messages
            .filter { it.toolLoadingName == null }
            .mapNotNull { message ->
                val cardSnapshot = message.card?.let(UiCardSnapshotCodec::encode)
                if (message.card != null && cardSnapshot == null) return@mapNotNull null
                if (
                    message.content.isBlank() &&
                    message.thinkingContent.isNullOrBlank() &&
                    cardSnapshot == null
                ) {
                    return@mapNotNull null
                }
                PersistedAgentMessage(
                    role = message.role,
                    content = message.content,
                    timestamp = message.timestamp,
                    thinkingContent = message.thinkingContent,
                    card = cardSnapshot
                )
            }
        return PersistedAgentChatDraft(
            historyId = historyId,
            messages = persistedMessages,
            inputText = state.inputText,
            pendingAttachmentName = state.pendingAttachmentName,
            pendingAttachmentText = state.pendingAttachmentText
        )
    }
}
