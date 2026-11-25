package com.example.tielink.data.local.db.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.tielink.domain.model.AgentBackgroundStatus
import com.example.tielink.domain.model.AgentBackgroundTask
import com.example.tielink.domain.model.AgentTeamSnapshot

@Entity(tableName = "agent_background_tasks", indices = [Index(value = ["requestId"], unique = true), Index(value = ["status", "createdAt"])])
data class AgentBackgroundTaskEntity(
    @PrimaryKey val id: String,
    val requestId: String,
    val originHistoryId: Long?,
    val title: String,
    val prompt: String,
    val status: String = "QUEUED",
    val stage: String = "generate",
    val partialText: String = "",
    val document: String = "",
    val html: String = "",
    val error: String = "",
    val epoch: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val readAt: Long = 0L,
    val sourceTaskId: String? = null,
    val supersedesTaskId: String? = null,
    val teamId: String? = null,
    @ColumnInfo(defaultValue = "''") val teamName: String = "",
    @ColumnInfo(defaultValue = "''") val teamBrief: String = "",
    @ColumnInfo(defaultValue = "''") val teamInstructions: String = "",
    @ColumnInfo(defaultValue = "1") val includeHtml: Boolean = true
) {
    fun toModel() = AgentBackgroundTask(
        id, requestId, originHistoryId, title, prompt,
        runCatching { AgentBackgroundStatus.valueOf(status) }.getOrDefault(AgentBackgroundStatus.PAUSED),
        stage, partialText, document, html, error, epoch, createdAt, updatedAt, readAt, sourceTaskId, supersedesTaskId,
        teamId?.let { AgentTeamSnapshot(it, teamName, teamBrief, teamInstructions, includeHtml) }, includeHtml
    )
}
