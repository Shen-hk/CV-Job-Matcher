package com.example.cv_jobmatcher.domain.model

/**
 * ATS match analysis extracted from LLM output.
 * v2: 添加分维度评分支持。
 */
data class MatchAnalysis(
    val score: Int = 0,                        // 0-100 综合评分
    val matched: List<String> = emptyList(),   // keywords found in resume
    val missing: List<String> = emptyList(),   // keywords missing from resume
    val suggestions: List<String> = emptyList(), // improvement suggestions
    // ── v2: 分维度评分 ──
    val keywordCoverage: Float = 0f,           // 关键词覆盖度 0-1
    val skillFit: Float = 0f,                  // 技能契合度 0-1
    val experienceRelevance: Float = 0f,       // 经验相关度 0-1
    val educationMatch: Float = 0f,            // 学历匹配度 0-1
    val missingSkills: List<SkillGap> = emptyList() // 缺失技能详情
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

/**
 * 简历中缺失的技能项，包含重要度权重。
 */
data class SkillGap(
    val skill: String,
    val importance: SkillImportance = SkillImportance.NORMAL,
    val category: String = "" // 技术/软技能/领域知识
)

enum class SkillImportance { REQUIRED, PREFERRED, NORMAL }

enum class MatchLevel { HIGH, MEDIUM, LOW }
