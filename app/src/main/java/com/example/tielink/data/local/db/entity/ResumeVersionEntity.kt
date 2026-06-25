package com.example.tielink.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 简历多版本存储 — 同一用户可保存多份简历。
 */
@Entity(tableName = "resume_versions")
data class ResumeVersionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "raw_text")
    val rawText: String,

    @ColumnInfo(name = "cleaned_text", defaultValue = "")
    val cleanedText: String = "",

    @ColumnInfo(name = "jd_matched_with", defaultValue = "")
    val jdMatchedWith: String = "",

    @ColumnInfo(name = "match_score", defaultValue = "0")
    val matchScore: Float = 0f,

    @ColumnInfo(name = "tags", defaultValue = "")
    val tags: String = "", // JSON array of strings

    @ColumnInfo(name = "is_active", defaultValue = "0")
    val isActive: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
