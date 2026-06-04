package com.example.cv_jobmatcher.domain.model

/**
 * Represents a user's resume content.
 * v1.0: raw text only. v1.1: parsed sections (experience, education, skills).
 */
data class Resume(
    val rawText: String,
    val cleanedText: String = rawText
)
