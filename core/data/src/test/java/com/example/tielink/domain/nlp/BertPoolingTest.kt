package com.example.tielink.domain.nlp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class BertPoolingTest {
    @Test
    fun mean_ignoresPaddingTokens() {
        val sequence = arrayOf(
            floatArrayOf(1f, 2f),
            floatArrayOf(3f, 4f),
            floatArrayOf(100f, 100f)
        )

        assertArrayEquals(floatArrayOf(2f, 3f), BertPooling.mean(sequence, intArrayOf(1, 1, 0)), 0.0001f)
    }

    @Test
    fun l2Normalize_producesUnitVector() {
        val normalized = BertPooling.l2Normalize(floatArrayOf(3f, 4f))

        assertArrayEquals(floatArrayOf(0.6f, 0.8f), normalized, 0.0001f)
        assertEquals(1.0, normalized.sumOf { (it * it).toDouble() }, 0.0001)
    }
}
