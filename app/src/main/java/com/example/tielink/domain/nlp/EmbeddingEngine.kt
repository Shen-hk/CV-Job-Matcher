package com.example.tielink.domain.nlp

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt

object EmbeddingEngine {
    private const val TAG = "EmbeddingEngine"
    private const val MODEL_FILE = "text2vec_base_chinese_quantized.tflite"
    private const val EMBEDDING_DIM = 768
    private const val MAX_SEQUENCE_LENGTH = 128

    private var interpreter: Interpreter? = null
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        try {
            val modelBuffer = loadModelFile(context)
            val options = Interpreter.Options()
            options.setNumThreads(4)
            interpreter = Interpreter(modelBuffer, options)
            isInitialized = true
            Log.i(TAG, "Embedding模型初始化成功: $MODEL_FILE")
        } catch (e: Exception) {
            Log.e(TAG, "Embedding模型初始化失败: ${e.message}", e)
        }
    }

    @Throws(IOException::class)
    private fun loadModelFile(context: Context): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(MODEL_FILE)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun embed(text: String): FloatArray {
        if (!isInitialized || interpreter == null) {
            Log.w(TAG, "模型未初始化，返回零向量")
            return FloatArray(EMBEDDING_DIM) { 0f }
        }

        try {
            val tokens = tokenizeAndPad(text)
            val input = Array(1) { IntArray(MAX_SEQUENCE_LENGTH) { tokens[it] } }
            val output = Array(1) { FloatArray(EMBEDDING_DIM) }

            interpreter?.run(input, output)

            val embedding = output[0]
            normalize(embedding)
            return embedding
        } catch (e: Exception) {
            Log.e(TAG, "Embedding计算失败: ${e.message}", e)
            return FloatArray(EMBEDDING_DIM) { 0f }
        }
    }

    private fun tokenizeAndPad(text: String): IntArray {
        val tokens = simpleTokenize(text).take(MAX_SEQUENCE_LENGTH)
        val padded = IntArray(MAX_SEQUENCE_LENGTH) { 0 }
        tokens.forEachIndexed { index, token ->
            if (index < MAX_SEQUENCE_LENGTH) padded[index] = token
        }
        return padded
    }

    private fun simpleTokenize(text: String): List<Int> {
        val tokens = mutableListOf<Int>()
        var tokenId = 1

        val chars = text.toList()
        var i = 0
        while (i < chars.size && tokens.size < MAX_SEQUENCE_LENGTH) {
            when {
                chars[i].isCJK() -> {
                    tokens.add(tokenId++)
                    i++
                }
                chars[i].isLetterOrDigit() -> {
                    val word = StringBuilder()
                    while (i < chars.size && (chars[i].isLetterOrDigit() || chars[i] == '_' || chars[i] == '-')) {
                        word.append(chars[i])
                        i++
                    }
                    if (word.length >= 2) {
                        tokens.add(tokenId++)
                    }
                }
                else -> i++
            }
        }

        return tokens
    }

    private fun normalize(vector: FloatArray) {
        var norm = 0f
        vector.forEach { norm += it * it }
        norm = sqrt(norm.toDouble()).toFloat()
        if (norm > 0) {
            for (i in vector.indices) {
                vector[i] /= norm
            }
        }
    }

    fun cosineSimilarity(vec1: FloatArray, vec2: FloatArray): Float {
        if (vec1.size != vec2.size) return 0f

        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f

        for (i in vec1.indices) {
            dotProduct += vec1[i] * vec2[i]
            norm1 += vec1[i] * vec1[i]
            norm2 += vec2[i] * vec2[i]
        }

        val denominator = sqrt(norm1.toDouble()) * sqrt(norm2.toDouble())
        return if (denominator > 0) (dotProduct / denominator.toFloat()) else 0f
    }

    fun computeSemanticScore(jdText: String, resumeText: String): Double {
        val jdEmbedding = embed(jdText)
        val resumeEmbedding = embed(resumeText)
        val similarity = cosineSimilarity(jdEmbedding, resumeEmbedding)
        Log.d(TAG, "语义相似度: ${(similarity * 100).toInt()}%")
        return similarity.toDouble()
    }

    fun isReady(): Boolean = isInitialized && interpreter != null

    fun close() {
        try {
            interpreter?.close()
            interpreter = null
            isInitialized = false
            Log.i(TAG, "Embedding模型已释放")
        } catch (e: Exception) {
            Log.e(TAG, "关闭模型失败: ${e.message}", e)
        }
    }
}

private fun Char.isCJK(): Boolean {
    val code = this.code
    return code in 0x4E00..0x9FFF ||
           code in 0x3400..0x4DBF ||
           code in 0x20000..0x2A6DF ||
           code in 0x2A700..0x2B73F ||
           code in 0xF900..0xFAFF ||
           code in 0x2F800..0x2FA1F
}
