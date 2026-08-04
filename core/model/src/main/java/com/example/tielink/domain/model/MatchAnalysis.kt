package com.example.tielink.domain.model

data class MatchAnalysis(
    val score: Int = 0,
    val matched: List<String> = emptyList(),
    val missing: List<String> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val keywordCoverage: Float = 0f,
    val skillFit: Float = 0f,
    val experienceRelevance: Float = 0f,
    val educationMatch: Float = 0f,
    val missingSkills: List<SkillGap> = emptyList()
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

data class SkillGap(
    val skill: String,
    val importance: SkillImportance = SkillImportance.NORMAL,
    val category: String = ""
)

enum class SkillImportance { REQUIRED, PREFERRED, NORMAL }

enum class MatchLevel { HIGH, MEDIUM, LOW }
