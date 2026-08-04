package com.example.tielink.domain.model

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

data class NewJobDescription(
    val companyName: String = "",
    val positionName: String = "",
    val rawText: String,
    val structuredJson: String = "",
    val skills: String = "",
    val salary: String = "",
    val sourceType: String = "manual"
)
