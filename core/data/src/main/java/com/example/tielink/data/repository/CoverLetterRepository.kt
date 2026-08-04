package com.example.tielink.data.repository

import android.util.Log
import com.example.tielink.data.remote.AiProviderManager
import com.example.tielink.data.remote.LlmRequest
import com.example.tielink.data.remote.PromptRegistry
import com.example.tielink.data.remote.dto.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoverLetterRepository @Inject constructor(
    private val aiProviderManager: AiProviderManager,
    private val promptRegistry: PromptRegistry
) {

    companion object {
        private const val TAG = "CoverLetterRepo"
    }

    suspend fun generateCoverLetter(
        jdText: String,
        resumeText: String,
        language: String = "zh"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val configKey = if (language == "zh") "cover_letter_zh" else "cover_letter_en"
            val config = promptRegistry.get(configKey)

            val userMessage = """【Job Description】
${jdText.take(3000)}

【Resume】
${resumeText.take(5000)}"""

            val request = LlmRequest(
                messages = listOf(
                    Message("system", config.system),
                    Message("user", userMessage)
                ),
                temperature = config.temperature,
                maxTokens = config.maxTokens
            )

            Log.d(TAG, "生成求职信: JD=${jdText.length}chars, Resume=${resumeText.length}chars, lang=$language")

            val response = aiProviderManager.chatWithFallback(request)

            Log.i(TAG, "求职信生成成功: ${response.content.length}字符")
            Result.success(response.content)
        } catch (e: Exception) {
            Log.e(TAG, "求职信生成失败: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun generateTemplateSuggestions(jdText: String): List<String> {
        val suggestions = mutableListOf<String>()

        if (jdText.contains("senior", ignoreCase = true) || jdText.contains("高级")) {
            suggestions.add("建议使用'高管风格'模板，突出领导力和战略思维")
        }
        if (jdText.contains("intern", ignoreCase = true) || jdText.contains("实习")) {
            suggestions.add("建议使用'紧凑专业'模板，突出学习能力和项目经验")
        }
        if (jdText.contains("frontend", ignoreCase = true) || jdText.contains("前端")) {
            suggestions.add("建议使用'现代双栏'模板，突出技术栈和项目成果")
        }

        return suggestions
    }
}
