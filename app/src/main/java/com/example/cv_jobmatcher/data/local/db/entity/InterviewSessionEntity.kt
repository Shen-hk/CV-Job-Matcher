package com.example.cv_jobmatcher.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 模拟面试会话记录。
 */
@Entity(tableName = "interview_sessions")
data class InterviewSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "persona_type")
    val personaType: String, // MILD_TECH / PRESSURE / FOREIGN_HR / STATE_STRUCTURED / CUSTOM

    @ColumnInfo(name = "jd_raw_text", defaultValue = "")
    val jdRawText: String = "",

    @ColumnInfo(name = "resume_version_id")
    val resumeVersionId: Long? = null,

    @ColumnInfo(name = "resume_text", defaultValue = "")
    val resumeText: String = "",

    @ColumnInfo(name = "is_active", defaultValue = "1")
    val isActive: Boolean = true,

    @ColumnInfo(name = "question_count", defaultValue = "0")
    val questionCount: Int = 0,

    @ColumnInfo(name = "overall_score")
    val overallScore: Float? = null,

    @ColumnInfo(name = "dimension_scores", defaultValue = "")
    val dimensionScores: String = "", // JSON map

    @ColumnInfo(name = "improvements", defaultValue = "")
    val improvements: String = "", // JSON array

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
