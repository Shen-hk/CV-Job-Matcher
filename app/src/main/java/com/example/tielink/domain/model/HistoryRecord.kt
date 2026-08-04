package com.example.tielink.domain.model

/** Full history record used outside the Room persistence boundary. */
data class HistoryRecord(
    val id: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val jdRawText: String = "",
    val jdTitle: String = "",
    val customTitle: String = "",
    val originalResume: String = "",
    val polishedResume: String = "",
    val resumeJson: String = "",
    val jdSkills: String = "[]",
    val matchNote: String = "",
    val matchScore: Int = 0,
    val matchedKeywords: String = "[]",
    val missingKeywords: String = "[]",
    val suggestions: String = "[]",
    val originalFilePath: String? = null,
    val sourceType: String = "text",
    val templateStyle: String = "classic",
    val isPinned: Boolean = false
)
