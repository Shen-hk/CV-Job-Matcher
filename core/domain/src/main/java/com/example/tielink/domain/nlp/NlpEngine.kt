package com.example.tielink.domain.nlp

import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Lightweight TF-IDF vectorizer + cosine similarity engine.
 *
 * Why hand-rolled: avoids any JVM NLP library dependency, runs fully offline,
 * and is intentionally simple enough to explain in an interview.
 *
 * Pipeline:
 *   1. tokenize() — split on CJK character boundaries + Latin word boundaries
 *   2. buildTfIdf()  — compute per-document TF-IDF weight vectors
 *   3. cosineSimilarity() — measure overlap between two weight vectors
 */
object NlpEngine {

    // ── Public API ────────────────────────────────────────────

    /**
     * Score how well [resumeText] matches [jdText] in [0.0, 1.0].
     */
    fun matchScore(jdText: String, resumeText: String): Double {
        val jdTokens = tokenize(jdText)
        val resumeTokens = tokenize(resumeText)
        if (jdTokens.isEmpty() || resumeTokens.isEmpty()) return 0.0

        val (jdVec, resumeVec) = buildTfIdf(listOf(jdTokens, resumeTokens))
        return cosineSimilarity(jdVec, resumeVec)
    }

    /**
     * Return the top-[topN] keywords from [text] by TF-IDF weight,
     * filtered to tokens that actually appear in [referenceText] (if provided).
     */
    fun extractKeywords(text: String, topN: Int = 20, referenceText: String? = null): List<String> {
        val tokens = tokenize(text)
        if (tokens.isEmpty()) return emptyList()

        // Single-document TF-IDF: IDF = 1 (only one doc), so just rank by TF
        val tf = tokens.groupingBy { it }.eachCount()
        val totalWords = tokens.size.toDouble()
        val scored = tf.mapValues { (_, count) -> count / totalWords }
            .entries
            .sortedByDescending { it.value }
            .map { it.key }

        val filtered = if (referenceText != null) {
            val refLower = referenceText.lowercase()
            scored.filter { refLower.contains(it) }
        } else scored

        return filtered.take(topN)
    }

    /**
     * Deterministic keyword matching: returns (matched, missing) from [candidates]
     * against [text], using whole-word / substring matching.
     */
    fun matchKeywords(
        candidates: List<String>,
        text: String
    ): Pair<List<String>, List<String>> {
        val textLower = text.lowercase()
        val matched = mutableListOf<String>()
        val missing = mutableListOf<String>()
        for (kw in candidates) {
            if (textLower.contains(kw.lowercase())) matched.add(kw) else missing.add(kw)
        }
        return matched to missing
    }

    // ── Tokenizer ─────────────────────────────────────────────

    /**
     * Split text into tokens:
     * - CJK characters: each single character is a token (bigrams generated separately)
     * - Latin/digit sequences: lowercased whole word
     * - Stop words filtered out
     */
    fun tokenize(text: String): List<String> {
        val tokens = mutableListOf<String>()
        val latinBuffer = StringBuilder()

        fun flushLatin() {
            val word = latinBuffer.toString().lowercase().trim()
            if (word.length >= 2 && word !in LATIN_STOP_WORDS) tokens.add(word)
            latinBuffer.clear()
        }

        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                isCjk(ch) -> {
                    flushLatin()
                    val single = ch.toString()
                    if (single !in CJK_STOP_WORDS) tokens.add(single)
                    // CJK bigrams
                    if (i + 1 < text.length && isCjk(text[i + 1])) {
                        val bigram = "$ch${text[i + 1]}"
                        if (bigram !in CJK_STOP_WORDS) tokens.add(bigram)
                    }
                }
                ch.isLetterOrDigit() -> latinBuffer.append(ch)
                ch == '+' || ch == '#' -> latinBuffer.append(ch) // keep C++, C#
                else -> flushLatin()
            }
            i++
        }
        flushLatin()
        return tokens
    }

    // ── TF-IDF ────────────────────────────────────────────────

    /**
     * Compute TF-IDF vectors for a corpus of tokenized documents.
     * Returns one weight map per document.
     */
    fun buildTfIdf(documents: List<List<String>>): List<Map<String, Double>> {
        val n = documents.size.toDouble()

        // Document frequency per term
        val df = mutableMapOf<String, Int>()
        for (doc in documents) {
            for (term in doc.toSet()) df[term] = (df[term] ?: 0) + 1
        }

        return documents.map { doc ->
            val total = doc.size.toDouble()
            val tf = doc.groupingBy { it }.eachCount()
            tf.entries.associate { (term, count) ->
                val tfVal = count / total
                // Smoothed IDF: log((N+1)/(df+1)) + 1
                val idfVal = ln((n + 1.0) / ((df[term] ?: 0) + 1.0)) + 1.0
                term to tfVal * idfVal
            }
        }
    }

    /**
     * Cosine similarity between two TF-IDF weight vectors.
     */
    fun cosineSimilarity(vec1: Map<String, Double>, vec2: Map<String, Double>): Double {
        var dot = 0.0
        var norm1 = 0.0
        var norm2 = 0.0

        for ((term, w1) in vec1) {
            dot += w1 * (vec2[term] ?: 0.0)
            norm1 += w1 * w1
        }
        for ((_, w2) in vec2) norm2 += w2 * w2

        return if (norm1 == 0.0 || norm2 == 0.0) 0.0
        else dot / (sqrt(norm1) * sqrt(norm2))
    }

    // ── Helpers ───────────────────────────────────────────────

    private fun isCjk(ch: Char): Boolean =
        ch.code in 0x4E00..0x9FFF ||   // CJK Unified Ideographs
        ch.code in 0x3400..0x4DBF ||   // CJK Extension A
        ch.code in 0x3040..0x309F ||   // Hiragana
        ch.code in 0x30A0..0x30FF      // Katakana

    // Common CJK stop words (single chars that carry little signal)
    private val CJK_STOP_WORDS = setOf(
        "的", "了", "和", "是", "在", "有", "与", "为", "以", "及",
        "等", "或", "其", "对", "到", "中", "上", "下", "按", "由",
        "从", "如", "将", "能", "可", "要", "也", "但", "并", "而",
        "被", "该", "每", "各", "此", "该", "了", "着", "地", "得",
        "们", "个", "年", "月", "日", "时"
    )

    private val LATIN_STOP_WORDS = setOf(
        "the", "a", "an", "and", "or", "in", "on", "at", "to", "for",
        "of", "with", "as", "is", "are", "was", "were", "be", "by",
        "this", "that", "it", "its", "from", "have", "has", "not",
        "we", "you", "they", "he", "she", "will", "can", "do", "does"
    )
}
