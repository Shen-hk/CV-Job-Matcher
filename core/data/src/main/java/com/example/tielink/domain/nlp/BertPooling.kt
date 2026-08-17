package com.example.tielink.domain.nlp

import kotlin.math.sqrt

object BertPooling {
    fun cls(sequenceOutput: Array<FloatArray>): FloatArray =
        sequenceOutput.firstOrNull()?.copyOf() ?: FloatArray(0)

    fun mean(sequenceOutput: Array<FloatArray>, attentionMask: IntArray): FloatArray {
        val embeddingSize = sequenceOutput.firstOrNull()?.size ?: return FloatArray(0)
        val pooled = FloatArray(embeddingSize)
        var includedTokens = 0

        sequenceOutput.forEachIndexed { tokenIndex, tokenEmbedding ->
            if (attentionMask.getOrElse(tokenIndex) { 0 } == 0) return@forEachIndexed
            require(tokenEmbedding.size == embeddingSize) { "Inconsistent BERT hidden-state dimensions" }
            tokenEmbedding.forEachIndexed { dimension, value -> pooled[dimension] += value }
            includedTokens++
        }

        if (includedTokens > 0) {
            pooled.indices.forEach { pooled[it] /= includedTokens.toFloat() }
        }
        return pooled
    }

    fun l2Normalize(vector: FloatArray): FloatArray {
        var squaredNorm = 0.0
        vector.forEach { squaredNorm += it * it }
        val norm = sqrt(squaredNorm).toFloat()
        if (norm > 0f) vector.indices.forEach { vector[it] /= norm }
        return vector
    }
}
