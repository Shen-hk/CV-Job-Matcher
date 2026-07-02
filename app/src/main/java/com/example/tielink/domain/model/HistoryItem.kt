package com.example.tielink.domain.model

/**
 * UI-facing representation of a history record.
 */
data class HistoryItem(
    val id: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val jdTitle: String,
    val customTitle: String,
    val jdRawText: String,
    val originalResume: String,
    val polishedResume: String,
    val jdSkills: List<String>,
    val optimizationNote: String,
    val isPinned: Boolean
)

val HistoryItem.displayTitle: String
    get() = customTitle.ifBlank { jdTitle }

val HistoryItem.previewText: String
    get() = optimizationNote.ifBlank {
        polishedResume.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
    }

fun HistoryItem.matches(query: String): Boolean {
    if (query.isBlank()) return true
    val normalized = query.trim().lowercase()
    return displayTitle.lowercase().contains(normalized) ||
        jdRawText.lowercase().contains(normalized) ||
        optimizationNote.lowercase().contains(normalized) ||
        polishedResume.lowercase().contains(normalized)
}
