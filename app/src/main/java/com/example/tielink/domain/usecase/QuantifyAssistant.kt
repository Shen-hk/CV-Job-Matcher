package com.example.tielink.domain.usecase

import android.util.Log
import com.example.tielink.data.remote.AiProviderManager
import com.example.tielink.data.remote.LlmRequest
import com.example.tielink.data.remote.PromptRegistry
import com.example.tielink.data.remote.dto.Message
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class QuantifySuggestion(
    val original: String,
    val quantified: String,
    val confidence: Float // 0-1
)

/**
 * Detects vague (non-quantified) phrases in a resume and suggests
 * data-driven rewrites via the LLM.
 *
 * Step 1: Regex to identify vague bullet-point sentences (no digits/percentages)
 * Step 2: For each candidate, call AI with "resume_quantify" prompt
 */
@Singleton
class QuantifyAssistant @Inject constructor(
    private val aiProviderManager: AiProviderManager,
    private val promptRegistry: PromptRegistry,
    private val moshi: Moshi
) {
    companion object {
        private const val TAG = "QuantifyAssistant"

        // Verbs that signal an accomplishment statement
        private val ACTION_VERBS = listOf(
            "负责", "参与", "完成", "主导", "推动", "优化", "搭建", "实现",
            "开发", "设计", "管理", "领导", "协调", "解决", "支持", "维护"
        )

        // A sentence is "vague" if it starts with an action verb and has NO numeric data
        private val DIGIT_PATTERN = Regex("""\d""")
        private val PERCENTAGE_PATTERN = Regex("""%|倍|个|万|千|百|次|人""")
    }

    /** Find vague (un-quantified) sentences in the resume. */
    fun detectVaguePhrases(resumeText: String): List<String> {
        return resumeText.lines()
            .map { it.trim() }
            .filter { line ->
                line.length > 10 &&
                ACTION_VERBS.any { verb -> line.startsWith(verb) || line.startsWith("- $verb") || line.startsWith("• $verb") } &&
                !DIGIT_PATTERN.containsMatchIn(line) &&
                !PERCENTAGE_PATTERN.containsMatchIn(line)
            }
    }

    /**
     * Ask AI to suggest a quantified rewrite for a single vague phrase.
     * Returns null if AI call fails or response is malformed.
     */
    suspend fun suggestQuantification(vaguePhrase: String): QuantifySuggestion? =
        withContext(Dispatchers.IO) {
            try {
                val config = promptRegistry.get("resume_quantify")
                val request = LlmRequest(
                    messages = listOf(
                        Message("system", config.system),
                        Message("user", "请帮我量化这条简历描述：\n$vaguePhrase")
                    ),
                    temperature = config.temperature,
                    maxTokens = config.maxTokens
                )
                val response = aiProviderManager.chatWithFallback(request)
                parseQuantifyResponse(vaguePhrase, response.content)
            } catch (e: Exception) {
                Log.w(TAG, "量化建议失败: ${e.message}")
                null
            }
        }

    /**
     * Batch: detect vague phrases and get quantification suggestions for all of them.
     * Returns suggestions only for phrases where AI succeeds.
     */
    suspend fun analyzeAndSuggest(resumeText: String): List<QuantifySuggestion> {
        val candidates = detectVaguePhrases(resumeText).take(5) // limit to avoid too many AI calls
        return candidates.mapNotNull { phrase -> suggestQuantification(phrase) }
    }

    private fun parseQuantifyResponse(original: String, json: String): QuantifySuggestion? {
        return try {
            val cleaned = json.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
            val map: Map<String, Any> = moshi.adapter<Map<String, Any>>(mapType).fromJson(cleaned)
                ?: return null
            val quantified = map["quantified"] as? String ?: return null
            val confidence = (map["confidence"] as? Number)?.toFloat() ?: 0.7f
            QuantifySuggestion(
                original = (map["original"] as? String) ?: original,
                quantified = quantified,
                confidence = confidence.coerceIn(0f, 1f)
            )
        } catch (e: Exception) {
            Log.w(TAG, "解析量化响应失败: ${e.message}")
            null
        }
    }
}