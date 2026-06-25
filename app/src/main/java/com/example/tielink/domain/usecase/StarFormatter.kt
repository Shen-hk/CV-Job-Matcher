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

data class StarResult(
    val situation: String,  // 背景：项目/工作的上下文
    val task: String,       // 任务：你需要完成什么
    val action: String,     // 行动：你具体做了什么
    val result: String,     // 结果：最终达成了什么效果
    val formatted: String   // 格式化后的完整描述
)

/**
 * Reformats a work experience passage into STAR structure
 * (Situation → Task → Action → Result).
 *
 * Uses the LLM with "resume_star_format" prompt.
 */
@Singleton
class StarFormatter @Inject constructor(
    private val aiProviderManager: AiProviderManager,
    private val promptRegistry: PromptRegistry,
    private val moshi: Moshi
) {
    companion object {
        private const val TAG = "StarFormatter"
    }

    /**
     * Format an experience description into STAR structure.
     *
     * @param experienceText the raw experience/project description to format
     * @return [StarResult] with individual STAR components and a formatted text,
     *         or null if the AI call fails
     */
    suspend fun format(experienceText: String): StarResult? = withContext(Dispatchers.IO) {
        try {
            val config = promptRegistry.get("resume_star_format")
            val request = LlmRequest(
                messages = listOf(
                    Message("system", config.system),
                    Message("user", "请将以下工作经历改写为STAR格式：\n\n$experienceText")
                ),
                temperature = config.temperature,
                maxTokens = config.maxTokens
            )
            val response = aiProviderManager.chatWithFallback(request)
            parseStarResponse(response.content)
        } catch (e: Exception) {
            Log.w(TAG, "STAR格式化失败: ${e.message}")
            null
        }
    }

    private fun parseStarResponse(json: String): StarResult? {
        return try {
            val cleaned = json.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
            val map: Map<String, Any> = moshi.adapter<Map<String, Any>>(mapType).fromJson(cleaned)
                ?: return null

            val situation = map["situation"] as? String ?: ""
            val task = map["task"] as? String ?: ""
            val action = map["action"] as? String ?: ""
            val result = map["result"] as? String ?: ""

            if (situation.isBlank() && task.isBlank() && action.isBlank()) return null

            val formatted = buildString {
                if (situation.isNotBlank()) appendLine("【背景】$situation")
                if (task.isNotBlank()) appendLine("【任务】$task")
                if (action.isNotBlank()) appendLine("【行动】$action")
                if (result.isNotBlank()) appendLine("【结果】$result")
            }.trim()

            StarResult(
                situation = situation,
                task = task,
                action = action,
                result = result,
                formatted = formatted
            )
        } catch (e: Exception) {
            Log.w(TAG, "解析STAR响应失败: ${e.message}")
            null
        }
    }
}
