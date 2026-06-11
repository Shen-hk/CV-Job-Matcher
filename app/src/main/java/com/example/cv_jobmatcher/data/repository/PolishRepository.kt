package com.example.cv_jobmatcher.data.repository

import android.util.Log
import com.example.cv_jobmatcher.data.remote.AiProviderManager
import com.example.cv_jobmatcher.data.remote.LlmRequest
import com.example.cv_jobmatcher.data.remote.PromptRegistry
import com.example.cv_jobmatcher.data.remote.dto.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PolishRepository @Inject constructor(
    private val aiProviderManager: AiProviderManager,
    private val promptRegistry: PromptRegistry
) {
    companion object {
        private const val TAG = "PolishRepo"
        private const val MAX_JD_LENGTH = 3000
        private const val MAX_RESUME_LENGTH = 5000
    }

    suspend fun polishResume(jdText: String, resumeText: String, fullPolish: Boolean = true): Result<String> =
        withContext(Dispatchers.IO) {
            val mode = if (fullPolish) "全篇优化" else "部分优化"
            val configKey = if (fullPolish) "polish_full" else "polish_partial"
            Log.d(TAG, "polishResume: $mode, JD=${jdText.length}, Resume=${resumeText.length}")
            try {
                val config = promptRegistry.get(configKey)
                val userMsg = "【JD】\n${jdText.take(MAX_JD_LENGTH)}\n\n【简历】\n${resumeText.take(MAX_RESUME_LENGTH)}"
                val request = LlmRequest(
                    messages = listOf(Message("system", config.system), Message("user", userMsg)),
                    temperature = config.temperature,
                    maxTokens = config.maxTokens
                )

                val response = aiProviderManager.chatWithFallback(request)
                val content = response.content

                Log.i(TAG, "$mode 完成 (${aiProviderManager.getProvider().providerName}): ${content.length} chars")
                Result.success(content)
            } catch (e: Exception) {
                Log.e(TAG, "润色失败: ${e.message}", e)
                Result.failure(e)
            }
        }

    suspend fun iterativePolish(jdText: String, currentResumeJson: String, instruction: String): Result<String> =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "iterativePolish: instruction=${instruction.take(50)}, resumeJson=${currentResumeJson.length}")
            try {
                val config = promptRegistry.get("polish_iterative")
                val userMsg = "【JD】\n${jdText.take(MAX_JD_LENGTH)}\n\n【当前简历JSON】\n${currentResumeJson.take(MAX_RESUME_LENGTH)}\n\n【修改指令】\n$instruction"
                val request = LlmRequest(
                    messages = listOf(Message("system", config.system), Message("user", userMsg)),
                    temperature = config.temperature,
                    maxTokens = config.maxTokens
                )

                val response = aiProviderManager.chatWithFallback(request)
                val content = response.content

                Log.i(TAG, "迭代润色完成 (${aiProviderManager.getProvider().providerName}): ${content.length} chars")
                Result.success(content)
            } catch (e: Exception) {
                Log.e(TAG, "迭代润色失败: ${e.message}", e)
                Result.failure(e)
            }
        }
}
