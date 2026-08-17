package com.example.tielink.domain.nlp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddingEvaluationTest {
    @Test
    fun evaluate_reportsPairAccuracyAndMeanMargin() {
        val cases = listOf(
            EmbeddingRankingCase("first", "q1", "positive", "negative"),
            EmbeddingRankingCase("second", "q2", "negative", "positive")
        )
        val report = EmbeddingEvaluation.evaluate(cases) { _, candidate ->
            if (candidate == "positive") 0.8 else 0.2
        }

        assertEquals(2, report.totalCases)
        assertEquals(1, report.correctCases)
        assertEquals(0.5, report.pairAccuracy, 0.0001)
        assertTrue(report.meanMargin in -0.0001..0.0001)
    }
}
