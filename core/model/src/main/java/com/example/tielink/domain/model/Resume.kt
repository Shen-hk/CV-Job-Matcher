package com.example.tielink.domain.model

data class Resume(
    val rawText: String,
    val cleanedText: String = rawText,
    val versionId: Long? = null,
    val sections: ResumeSections? = null,
    val tags: List<String> = emptyList()
)

data class ResumeSections(
    val personalInfo: String = "",
    val summary: String = "",
    val experience: List<String> = emptyList(),
    val education: List<String> = emptyList(),
    val skills: List<String> = emptyList(),
    val projects: List<String> = emptyList(),
    val others: String = ""
)
