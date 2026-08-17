package com.example.tielink.domain.nlp

data class EmbeddingRankingCase(
    val name: String,
    val query: String,
    val relevantText: String,
    val irrelevantText: String
)

data class EmbeddingEvaluationReport(
    val totalCases: Int,
    val correctCases: Int,
    val pairAccuracy: Double,
    val meanMargin: Double
)

object EmbeddingEvaluation {
    fun evaluate(
        cases: List<EmbeddingRankingCase>,
        scorer: (String, String) -> Double
    ): EmbeddingEvaluationReport {
        require(cases.isNotEmpty()) { "At least one evaluation case is required" }
        var correct = 0
        var marginSum = 0.0
        cases.forEach { case ->
            val margin = scorer(case.query, case.relevantText) - scorer(case.query, case.irrelevantText)
            if (margin > 0.0) correct++
            marginSum += margin
        }
        return EmbeddingEvaluationReport(
            totalCases = cases.size,
            correctCases = correct,
            pairAccuracy = correct.toDouble() / cases.size,
            meanMargin = marginSum / cases.size
        )
    }
}
