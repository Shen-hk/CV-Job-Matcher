package com.example.cv_jobmatcher.domain.model

/**
 * ATS match analysis extracted from LLM output.
 */
data class MatchAnalysis(
    val score: Int = 0,                        // 0-100
    val matched: List<String> = emptyList(),   // keywords found in resume
    val missing: List<String> = emptyList(),   // keywords missing from resume
    val suggestions: List<String> = emptyList() // improvement suggestions
) {
    val level: MatchLevel
        get() = when {
            score >= 80 -> MatchLevel.HIGH
            score >= 50 -> MatchLevel.MEDIUM
            else -> MatchLevel.LOW
        }

    val matchedCount: Int get() = matched.size
    val missingCount: Int get() = missing.size
    val totalKeywords: Int get() = matched.size + missing.size
    val matchPercent: Float
        get() = if (totalKeywords > 0) matched.size.toFloat() / totalKeywords else 0f

    companion object {
        val EMPTY = MatchAnalysis()
    }
}

enum class MatchLevel { HIGH, MEDIUM, LOW }
