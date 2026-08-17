package com.example.tielink.domain.nlp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class BertTokenizerTest {
    private val vocabulary = listOf(
        "[PAD]", "[UNK]", "[CLS]", "[SEP]", "你", "好", "play", "##ing", ","
    )
    private val tokenizer = BertTokenizer(vocabulary)

    @Test
    fun tokenize_appliesBasicAndWordPieceRules() {
        assertEquals(
            listOf("你", "好", "play", "##ing", ",", "[UNK]"),
            tokenizer.tokenize("你好 Playing, missing")
        )
    }

    @Test
    fun encode_addsSpecialTokensAndAllThreeInputs() {
        val encoded = tokenizer.encode("你好 playing", maxSequenceLength = 9)

        assertArrayEquals(intArrayOf(2, 4, 5, 6, 7, 3, 0, 0, 0), encoded.inputIds)
        assertArrayEquals(intArrayOf(1, 1, 1, 1, 1, 1, 0, 0, 0), encoded.attentionMask)
        assertArrayEquals(IntArray(9), encoded.tokenTypeIds)
    }

    @Test
    fun encode_reservesSpaceForClsAndSepWhenTruncating() {
        val encoded = tokenizer.encode("你好 playing missing", maxSequenceLength = 5)

        assertArrayEquals(intArrayOf(2, 4, 5, 6, 3), encoded.inputIds)
        assertArrayEquals(IntArray(5) { 1 }, encoded.attentionMask)
    }
}
