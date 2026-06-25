package com.example.tielink.domain.usecase

import com.example.tielink.domain.model.SkillGap
import com.example.tielink.domain.model.SkillImportance
import com.example.tielink.domain.nlp.KeywordClassifier
import com.example.tielink.domain.nlp.NlpEngine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Analyzes the skill gap between a JD and a resume.
 *
 * Returns a list of missing skills with:
 *   - importance: REQUIRED / PREFERRED / NORMAL
 *   - category: e.g. "编程语言", "框架", or "" for uncategorized
 *
 * Skills are extracted from the JD via NlpEngine keyword extraction,
 * then filtered to those absent from the resume.
 */
@Singleton
class SkillGapAnalyzer @Inject constructor(
    private val classifier: KeywordClassifier
) {
    companion object {
        private const val TOP_JD_KEYWORDS = 30
        private val REQUIRED_CONTEXTS = listOf("必须", "必备", "要求掌握", "熟练掌握", "精通", "required", "must have")
        private val PREFERRED_CONTEXTS = listOf("优先", "加分", "熟悉", "了解", "preferred", "nice to have", "plus")
    }

    /**
     * @param jdText      raw JD text
     * @param resumeText  resume text to compare against
     * @param jdSkills    pre-extracted JD skill list (from LLM or empty for NLP extraction)
     * @return gaps sorted by importance (REQUIRED first) then alphabetically
     */
    fun analyze(
        jdText: String,
        resumeText: String,
        jdSkills: List<String> = emptyList()
    ): List<SkillGap> {
        // Use provided skills or extract from JD text
        val skillCandidates = jdSkills.ifEmpty {
            NlpEngine.extractKeywords(jdText, topN = TOP_JD_KEYWORDS, referenceText = resumeText)
        }

        val resumeLower = resumeText.lowercase()
        val jdLower = jdText.lowercase()

        return skillCandidates
            .filter { skill -> !resumeLower.contains(skill.lowercase()) }
            .map { skill ->
                SkillGap(
                    skill = skill,
                    importance = detectImportance(skill, jdLower),
                    category = classifier.getCategory(skill) ?: ""
                )
            }
            .sortedWith(
                compareByDescending<SkillGap> { it.importance.ordinal }
                    .thenBy { it.skill }
            )
    }

    private fun detectImportance(skill: String, jdLower: String): SkillImportance {
        val idx = jdLower.indexOf(skill.lowercase())
        if (idx < 0) return SkillImportance.NORMAL
        val contextStart = maxOf(0, idx - 60)
        val contextEnd = minOf(jdLower.length, idx + skill.length + 60)
        val context = jdLower.substring(contextStart, contextEnd)

        return when {
            REQUIRED_CONTEXTS.any { context.contains(it) } -> SkillImportance.REQUIRED
            PREFERRED_CONTEXTS.any { context.contains(it) } -> SkillImportance.PREFERRED
            else -> SkillImportance.NORMAL
        }
    }
}
