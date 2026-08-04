package com.example.tielink.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 面试会话中的单条消息。
 */
@Entity(
    tableName = "interview_messages",
    foreignKeys = [
        ForeignKey(
            entity = InterviewSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("session_id")]
)
data class InterviewMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "session_id")
    val sessionId: Long,

    @ColumnInfo(name = "role")
    val role: String, // USER / INTERVIEWER / SYSTEM

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "is_hint", defaultValue = "0")
    val isHint: Boolean = false,

    @ColumnInfo(name = "is_evaluation", defaultValue = "0")
    val isEvaluation: Boolean = false
)
