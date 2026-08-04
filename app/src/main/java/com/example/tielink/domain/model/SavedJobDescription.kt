package com.example.tielink.domain.model

/** A persisted job description exposed outside the Room data layer. */
data class SavedJobDescription(
    val id: Long,
    val companyName: String,
    val positionName: String,
    val rawText: String,
    val structuredJson: String,
    val skills: String,
    val salary: String,
    val sourceType: String,
    val createdAt: Long
)

/** Input for creating a saved job description. The database owns the ID and timestamp. */
data class NewJobDescription(
    val companyName: String = "",
    val positionName: String = "",
    val rawText: String,
    val structuredJson: String = "",
    val skills: String = "",
    val salary: String = "",
    val sourceType: String = "manual"
)
