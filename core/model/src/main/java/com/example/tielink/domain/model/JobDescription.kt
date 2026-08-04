package com.example.tielink.domain.model

import com.squareup.moshi.Json

/**
 * Structured JD extracted by LLM from raw job posting text.
 */
data class JobDescription(
    @field:Json(name = "job_title") val jobTitle: String = "",
    val requirements: List<String> = emptyList(),
    val skills: List<String> = emptyList(),
    val responsibilities: List<String> = emptyList(),
    @field:Json(name = "nice_to_have") val niceToHave: List<String> = emptyList(),
    val summary: String = ""
) {
    val allKeywords: List<String>
        get() = skills + requirements.filter { it.length < 30 }

    val isEmpty: Boolean
        get() = jobTitle.isBlank() && skills.isEmpty() && requirements.isEmpty()
}
