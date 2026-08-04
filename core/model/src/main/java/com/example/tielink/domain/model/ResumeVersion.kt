package com.example.tielink.domain.model

data class ResumeVersion(
    val id: Long = 0,
    val name: String,
    val rawText: String,
    val cleanedText: String = rawText,
    val jdMatchedWith: String = "",
    val matchScore: Float = 0f,
    val tags: List<String> = emptyList(),
    val isActive: Boolean = false,
    val originalFilePath: String = "",
    val originalMimeType: String = "",
    val isPolished: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
