package com.example.cv_jobmatcher.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 投递记录 — 追踪每次简历投递的状态流转。
 */
@Entity(tableName = "tracking")
data class TrackingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "company_name")
    val companyName: String,

    @ColumnInfo(name = "position_name")
    val positionName: String,

    @ColumnInfo(name = "status")
    val status: String = "已投", // 已投/简历过筛/待面试/已面试/已offer/已拒

    @ColumnInfo(name = "resume_version_id")
    val resumeVersionId: Long? = null,

    @ColumnInfo(name = "jd_raw_text", defaultValue = "")
    val jdRawText: String = "",

    @ColumnInfo(name = "notes", defaultValue = "")
    val notes: String = "",

    @ColumnInfo(name = "timeline", defaultValue = "[]")
    val timeline: String = "[]", // JSON array of {status, timestamp}

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
