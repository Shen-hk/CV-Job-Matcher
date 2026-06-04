package com.example.cv_jobmatcher.domain.model

/**
 * UI-facing representation of a history record.
 */
data class HistoryItem(
    val id: Long,
    val createdAt: Long,
    val jdTitle: String,
    val jdRawText: String,
    val originalResume: String,
    val polishedResume: String,
    val jdSkills: List<String>,
    val optimizationNote: String
)
