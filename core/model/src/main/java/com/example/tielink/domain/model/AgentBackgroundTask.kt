package com.example.tielink.domain.model

enum class AgentBackgroundStatus {
    QUEUED, RUNNING, PAUSED, COMPLETED, FAILED, CANCELED, SUPERSEDED;

    val canResume: Boolean get() = this == PAUSED || this == FAILED || this == QUEUED
    val canPause: Boolean get() = this == RUNNING || this == QUEUED
    val isTerminal: Boolean get() = this == COMPLETED || this == CANCELED || this == SUPERSEDED
}

/** A durable, user-initiated document task. Partial streaming text is never a finished draft. */
data class AgentBackgroundTask(
    val id: String,
    val requestId: String,
    val originHistoryId: Long?,
    val title: String,
    val prompt: String,
    val status: AgentBackgroundStatus,
    val stage: String,
    val partialText: String,
    val document: String,
    val html: String,
    val error: String,
    val epoch: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val readAt: Long,
    val sourceTaskId: String? = null,
    val supersedesTaskId: String? = null,
    val team: AgentTeamSnapshot? = null,
    val includeHtml: Boolean = team?.includeHtml != false
) {
    val hasDocument: Boolean get() = document.isNotBlank()
    val hasHtml: Boolean get() = html.isNotBlank()
    val isUnread: Boolean get() = status == AgentBackgroundStatus.COMPLETED && readAt == 0L
}

data class AgentBackgroundOpenRequest(val taskId: String, val nonce: Long = System.nanoTime())
