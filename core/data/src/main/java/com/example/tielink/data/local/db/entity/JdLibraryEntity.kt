package com.example.tielink.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * JD 库 — 用户保存的所有岗位描述，支持手动录入 / OCR / AI 自动提取三种来源。
 */
@Entity(tableName = "jd_library")
data class JdLibraryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "company_name", defaultValue = "")
    val companyName: String = "",

    @ColumnInfo(name = "position_name", defaultValue = "")
    val positionName: String = "",

    @ColumnInfo(name = "raw_text")
    val rawText: String,

    @ColumnInfo(name = "structured_json", defaultValue = "")
    val structuredJson: String = "",

    @ColumnInfo(name = "skills", defaultValue = "")
    val skills: String = "",  // JSON array

    @ColumnInfo(name = "salary", defaultValue = "")
    val salary: String = "",  // e.g. "20k-40k"

    @ColumnInfo(name = "source_type", defaultValue = "manual")
    val sourceType: String = "manual",  // "manual" / "ocr" / "ai_auto"

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
