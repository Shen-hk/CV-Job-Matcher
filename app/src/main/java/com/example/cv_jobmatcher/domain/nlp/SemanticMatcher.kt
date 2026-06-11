package com.example.cv_jobmatcher.domain.nlp

import android.util.Log
import com.example.cv_jobmatcher.domain.model.MatchAnalysis
import com.example.cv_jobmatcher.domain.model.MatchLevel

data class SemanticMatchResult(
    val analysis: MatchAnalysis,
    val semanticScore: Double,
    val keywordScore: Double,
    val classifiedMatched: ClassifiedKeywords,
    val classifiedMissing: ClassifiedKeywords
)

object SemanticMatcher {
    private const val TAG = "SemanticMatcher"
    private const val SEMANTIC_WEIGHT = 0.60
    private const val KEYWORD_WEIGHT = 0.40

    fun analyze(
        jdText: String,
        resumeText: String,
        jdSkills: List<String>,
        classifier: KeywordClassifier? = null,
        llmScore: Int? = null
    ): SemanticMatchResult {
        Log.d(TAG, "开始语义分析: skills=${jdSkills.size}, llmScore=$llmScore")

        val matchResult = NlpEngine.matchKeywords(jdSkills, resumeText)
        val matched = matchResult.first
        val missing = matchResult.second
        val keywordScore = if (jdSkills.isEmpty()) 0.0
                           else matched.size.toDouble() / jdSkills.size

        val semanticScore = if (EmbeddingEngine.isReady()) {
            EmbeddingEngine.computeSemanticScore(jdText, resumeText)
        } else {
            Log.w(TAG, "Embedding未就绪，降级到TF-IDF")
            NlpEngine.matchScore(jdText, resumeText)
        }

        val blendedRaw = semanticScore * SEMANTIC_WEIGHT + keywordScore * KEYWORD_WEIGHT
        val blended = (blendedRaw * 100).toInt().coerceIn(0, 100)

        val finalScore = if (llmScore != null && llmScore > 0) {
            ((blended * 0.4 + llmScore * 0.6).toInt()).coerceIn(0, 100)
        } else blended

        Log.d(TAG, "评分详情: semantic=${(semanticScore*100).toInt()}, keyword=${(keywordScore*100).toInt()}, blended=$blended, final=$finalScore")

        val classifiedMatched = classifier?.classify(matched) ?: ClassifiedKeywords(emptyMap(), matched)
        val classifiedMissing = classifier?.classify(missing) ?: ClassifiedKeywords(emptyMap(), missing)

        val suggestions = buildSuggestions(missing, classifiedMissing, finalScore)

        val analysis = MatchAnalysis(
            score = finalScore,
            matched = matched,
            missing = missing,
            suggestions = suggestions
        )

        return SemanticMatchResult(
            analysis = analysis,
            semanticScore = semanticScore,
            keywordScore = keywordScore,
            classifiedMatched = classifiedMatched,
            classifiedMissing = classifiedMissing
        )
    }

    private fun buildSuggestions(
        missing: List<String>,
        classifiedMissing: ClassifiedKeywords,
        score: Int
    ): List<String> {
        val suggestions = mutableListOf<String>()

        when {
            score >= 80 -> {
                suggestions.add("简历与岗位高度匹配，建议保持现有内容")
                if (missing.isNotEmpty()) {
                    suggestions.add("可考虑补充以下技能以进一步提升竞争力：${missing.take(3).joinToString(", ")}")
                }
            }
            score in 50..79 -> {
                suggestions.add("简历基本符合要求，但有关键技能缺失")
                val topMissing = classifiedMissing.getTopCategories(2)
                topMissing.forEach { (category, keywords) ->
                    suggestions.add(category + "领域缺失：" + keywords.joinToString(", "))
                }
            }
            else -> {
                suggestions.add("简历与岗位匹配度较低，需要大幅优化")
                suggestions.add("建议重点补充以下核心技能：${missing.take(5).joinToString(", ")}")
                if (missing.size > 5) {
                    suggestions.add("还有${missing.size - 5}项次要技能可考虑补充")
                }
            }
        }

        return suggestions
    }
}
