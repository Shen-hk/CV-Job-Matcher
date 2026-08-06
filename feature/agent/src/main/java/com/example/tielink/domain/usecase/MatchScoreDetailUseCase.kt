package com.example.tielink.domain.usecase

import com.example.tielink.domain.model.MatchAnalysis
import com.example.tielink.domain.model.SkillGap
import com.example.tielink.domain.model.SkillImportance
import com.example.tielink.domain.nlp.KeywordClassifier
import com.example.tielink.domain.nlp.NlpEngine
import javax.inject.Inject
import javax.inject.Singleton

data class DetailedMatchResult(
    val keywordCoverage: Float,     // 关键词覆盖度 [0,1]
    val skillFit: Float,            // 技能契合度 [0,1]
    val experienceRelevance: Float, // 经验相关度 [0,1]
    val educationMatch: Float,      // 学历匹配度 [0,1]
    val missingSkills: List<SkillGap>
)

/**
 * Computes the four dimension scores for MatchAnalysis v2.
 *
 * keywordCoverage — ratio of JD keywords found in resume
 * skillFit        — technical skill keyword match (subset of keyword coverage)
 * experienceRelevance — TF-IDF similarity of experience-related text
 * educationMatch  — rule-based education requirement detection
 */
@Singleton
class MatchScoreDetailUseCase @Inject constructor(
    private val classifier: KeywordClassifier
) {
    private val TECH_CATEGORIES = setOf("编程语言", "框架", "数据库", "工具", "云平台", "操作系统", "技术")

    // Education level hierarchy (higher index = higher level)
    private val EDU_LEVELS = listOf("高中", "大专", "本科", "硕士", "研究生", "博士")

    // Regex patterns to isolate experience-related sections
    private val EXPERIENCE_HEADERS = Regex(
        "(?i)(工作经历|工作经验|项目经验|实习经历|experience|work history)",
        RegexOption.IGNORE_CASE
    )
    private val NEXT_SECTION = Regex(
        "(?m)^(教育|学历|技能|Skills|Education|Certifications|证书|奖项)",
        RegexOption.IGNORE_CASE
    )

    fun compute(
        jdText: String,
        resumeText: String,
        jdKeywords: List<String>,
        missingKeywords: List<String>
    ): DetailedMatchResult {
        val keywordCoverage = computeKeywordCoverage(jdKeywords, missingKeywords)
        val skillFit = computeSkillFit(jdKeywords, resumeText)
        val experienceRelevance = computeExperienceRelevance(jdText, resumeText)
        val educationMatch = computeEducationMatch(jdText, resumeText)
        val missingSkills = buildSkillGaps(jdText, missingKeywords)
        return DetailedMatchResult(
            keywordCoverage = keywordCoverage,
            skillFit = skillFit,
            experienceRelevance = experienceRelevance,
            educationMatch = educationMatch,
            missingSkills = missingSkills
        )
    }

    /** Enrich an existing MatchAnalysis with v2 dimension scores. */
    fun enrich(existing: MatchAnalysis, jdText: String, resumeText: String): MatchAnalysis {
        val detail = compute(
            jdText = jdText,
            resumeText = resumeText,
            jdKeywords = existing.matched + existing.missing,
            missingKeywords = existing.missing
        )
        return existing.copy(
            keywordCoverage = detail.keywordCoverage,
            skillFit = detail.skillFit,
            experienceRelevance = detail.experienceRelevance,
            educationMatch = detail.educationMatch,
            missingSkills = detail.missingSkills
        )
    }

    // ── Dimension computations ─────────────────────────────────

    private fun computeKeywordCoverage(jdKeywords: List<String>, missingKeywords: List<String>): Float {
        if (jdKeywords.isEmpty()) return 0.5f
        val matchedCount = jdKeywords.size - missingKeywords.size
        return (matchedCount.toFloat() / jdKeywords.size).coerceIn(0f, 1f)
    }

    private fun computeSkillFit(jdKeywords: List<String>, resumeText: String): Float {
        if (jdKeywords.isEmpty()) return 0f
        val classified = classifier.classify(jdKeywords)
        val techKeywords = classified.byCategory
            .filter { (cat, _) -> cat in TECH_CATEGORIES }
            .values.flatten()
        if (techKeywords.isEmpty()) return computeKeywordCoverage(jdKeywords, emptyList())

        val resumeLower = resumeText.lowercase()
        val matchedTech = techKeywords.count { kw -> resumeLower.contains(kw.lowercase()) }
        return (matchedTech.toFloat() / techKeywords.size).coerceIn(0f, 1f)
    }

    private fun computeExperienceRelevance(jdText: String, resumeText: String): Float {
        val expSection = extractExperienceSection(resumeText)
        val textToCompare = expSection.ifBlank { resumeText }
        if (textToCompare.isBlank() || jdText.isBlank()) return 0f
        return NlpEngine.matchScore(jdText, textToCompare).toFloat().coerceIn(0f, 1f)
    }

    private fun computeEducationMatch(jdText: String, resumeText: String): Float {
        val requiredLevel = detectRequiredEduLevel(jdText) ?: return 0.8f // no requirement → good match
        val resumeLevel = detectEduLevelInText(resumeText)
        if (resumeLevel == null) return 0.4f // can't determine

        val required = EDU_LEVELS.indexOf(requiredLevel)
        val actual = EDU_LEVELS.indexOf(resumeLevel)
        return when {
            actual >= required -> 1.0f
            actual == required - 1 -> 0.6f  // one level below required
            else -> 0.3f
        }
    }

    private fun buildSkillGaps(jdText: String, missingKeywords: List<String>): List<SkillGap> {
        val jdLower = jdText.lowercase()
        return missingKeywords.map { skill ->
            val importance = when {
                isRequired(skill, jdLower) -> SkillImportance.REQUIRED
                isPreferred(skill, jdLower) -> SkillImportance.PREFERRED
                else -> SkillImportance.NORMAL
            }
            val category = classifier.getCategory(skill) ?: ""
            SkillGap(skill = skill, importance = importance, category = category)
        }.sortedWith(compareByDescending<SkillGap> { it.importance.ordinal })
    }

    // ── Helpers ────────────────────────────────────────────────

    private fun extractExperienceSection(resumeText: String): String {
        val start = EXPERIENCE_HEADERS.find(resumeText)?.range?.first ?: return ""
        val endMatch = NEXT_SECTION.find(resumeText, start + 10)
        val end = endMatch?.range?.first ?: resumeText.length
        return resumeText.substring(start, end)
    }

    private fun detectRequiredEduLevel(jdText: String): String? {
        val lower = jdText.lowercase()
        // Match in reverse order (most specific first)
        for (level in EDU_LEVELS.reversed()) {
            if (lower.contains(level)) return level
        }
        return null
    }

    private fun detectEduLevelInText(text: String): String? {
        val lower = text.lowercase()
        for (level in EDU_LEVELS.reversed()) {
            if (lower.contains(level)) return level
        }
        return null
    }

    private fun isRequired(skill: String, jdLower: String): Boolean {
        val requiredPatterns = listOf("必须", "必备", "要求", "需要", "必要", "required", "must")
        val idx = jdLower.indexOf(skill.lowercase())
        if (idx < 0) return false
        val context = jdLower.substring(maxOf(0, idx - 50), idx + skill.length + 50)
        return requiredPatterns.any { context.contains(it) }
    }

    private fun isPreferred(skill: String, jdLower: String): Boolean {
        val preferredPatterns = listOf("优先", "加分", "最好", "preferred", "nice to have", "plus")
        val idx = jdLower.indexOf(skill.lowercase())
        if (idx < 0) return false
        val context = jdLower.substring(maxOf(0, idx - 50), idx + skill.length + 50)
        return preferredPatterns.any { context.contains(it) }
    }
}
