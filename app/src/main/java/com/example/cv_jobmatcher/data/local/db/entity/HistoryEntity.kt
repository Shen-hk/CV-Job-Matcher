package com.example.cv_jobmatcher.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "jd_raw_text")
    val jdRawText: String,

    @ColumnInfo(name = "jd_title")
    val jdTitle: String,

    @ColumnInfo(name = "original_resume")
    val originalResume: String,

    @ColumnInfo(name = "polished_resume")
    val polishedResume: String,

    @ColumnInfo(name = "resume_json", defaultValue = "")
    val resumeJson: String = "",

    @ColumnInfo(name = "jd_skills")
    val jdSkills: String,

    @ColumnInfo(name = "match_note")
    val matchNote: String,

    @ColumnInfo(name = "match_score")
    val matchScore: Int = 0,

    @ColumnInfo(name = "matched_keywords")
    val matchedKeywords: String = "[]",

    @ColumnInfo(name = "missing_keywords")
    val missingKeywords: String = "[]",

    @ColumnInfo(name = "suggestions")
    val suggestions: String = "[]",

    @ColumnInfo(name = "original_file_path")
    val originalFilePath: String? = null,

    @ColumnInfo(name = "source_type")
    val sourceType: String = "text",

    @ColumnInfo(name = "template_style")
    val templateStyle: String = "classic"
)
