package com.example.tielink.domain.model

data class ResumeLibraryItem(
    val id: Long,
    val type: String,  // "history" or "version"
    val title: String,
    val subtitle: String,
    val matchScore: Int = 0,
    val createdAt: Long
)
