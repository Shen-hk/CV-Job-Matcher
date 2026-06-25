package com.example.tielink.domain.usecase

import android.util.Log
import com.example.tielink.domain.model.MatchAnalysis
import com.example.tielink.domain.model.MatchLevel
import com.example.tielink.domain.nlp.ClassifiedKeywords
import com.example.tielink.domain.nlp.KeywordClassifier
import com.example.tielink.domain.nlp.NlpEngine
import javax.inject.Inject
import javax.inject.Singleton

data class MatchResult(
    val analysis: MatchAnalysis,
    val classifiedMatched: ClassifiedKeywords,
    val classifiedMissing: ClassifiedKeywords,
    val tfidfScore: Double,   // raw cosine similarity [0,1]
    val keywordScore: Double  // matched/total ratio [0,1]
)

/**
 * Domain UseCase: orchestrates NLP matching pipeline.
 *
 * Score blending:
 *   - 60% keyword match ratio (deterministic, based on JD skills list)
 *   - 40% TF-IDF cosine similarity (semantic overlap of full texts)
 *
 * This produces a score that is both explainable (keywords) and
 * semantically-aware (full-text TF-IDF), matching the Resume-Matcher approach.
 */
@Singleton
class MatchAnalysisUseCase @Inject constructor(
    private val classifier: KeywordClassifier
) {
    companion object {
        private const val TAG = "MatchAnalysisUseCase"
        private const val KEYWORD_WEIGHT = 0.60
        private const val TFIDF_WEIGHT = 0.40
    }

    /**
     * @param jdText        Raw job description text
     * @param resumeText    Polished or original resume text to evaluate
     * @param jdSkills      Pre-extracted JD skill keywords (from LLM or local)
     * @param llmScore      LLM-provided score (if available); used as a sanity anchor.
     *                      Pass null to compute purely locally.
     */
    fun analyze(
        jdText: String,
        resumeText: String,
        jdSkills: List<String>,
        llmScore: Int? = null
    ): MatchResult {
        Log.d(TAG, "analyze: jdSkills=${jdSkills.size}, llmScore=$llmScore")

        // 1. Keyword match (deterministic string matching)
        val (matched, missing) = NlpEngine.matchKeywords(jdSkills, resumeText)
        val keywordScore = if (jdSkills.isEmpty()) 0.0
                           else matched.size.toDouble() / jdSkills.size

        // 2. TF-IDF cosine similarity on full texts
        val tfidfScore = NlpEngine.matchScore(jdText, resumeText)

        // 3. Blend into final score
        val blendedRaw = (keywordScore * KEYWORD_WEIGHT + tfidfScore * TFIDF_WEIGHT)
        val blended = (blendedRaw * 100).toInt().coerceIn(0, 100)

        // 4. If LLM provided a score, use weighted average (LLM is authoritative on semantics)
        val finalScore = if (llmScore != null && llmScore > 0) {
            ((blended * 0.4 + llmScore * 0.6).toInt()).coerceIn(0, 100)
        } else blended

        Log.d(TAG, "scores: keyword=${(keywordScore*100).toInt()}, tfidf=${(tfidfScore*100).toInt()}, blended=$blended, final=$finalScore")

        // 5. Classify matched / missing keywords by category
        val classifiedMatched = classifier.classify(matched)
        val classifiedMissing = classifier.classify(missing)

        val analysis = MatchAnalysis(
            score = finalScore,
            matched = matched,
            missing = missing,
            suggestions = buildSuggestions(missing, classifiedMissing, finalScore)
        )

        return MatchResult(
            analysis = analysis,
            classifiedMatched = classifiedMatched,
            classifiedMissing = classifiedMissing,
            tfidfScore = tfidfScore,
            keywordScore = keywordScore
        )
    }

    // ── Private helpers ───────────────────────────────────────

    private fun buildSuggestions(
        missing: List<String>,
        classifiedMissing: ClassifiedKeywords,
        score: Int
    ): List<String> {
        val suggestions = mutableListOf<String>()

        if (score < 50) suggestions.add("整体匹配度较低，建议针对 JD 要求重点补充相关经验描述")

        // Group missing by category and suggest per category
        for ((category, keywords) in classifiedMissing.byCategory) {
            if (keywords.isNotEmpty()) {
                val sample = keywords.take(3).joinToString("、")
                suggestions.add("${category}方向缺失关键词：$sample${if (keywords.size > 3) " 等" else ""}，建议在对应项目/技能栏中添加")
            }
        }

        if (classifiedMissing.uncategorized.isNotEmpty()) {
            val sample = classifiedMissing.uncategorized.take(3).joinToString("、")
            suggestions.add("其他缺失关键词：$sample，建议评估是否可以在工作经历中体现")
        }

        if (missing.isEmpty() && score >= 80) {
            suggestions.add("关键词覆盖度优秀！建议进一步量化工作成果以提升竞争力")
        }

        return suggestions.take(5)
    }
}
