package com.example.tielink.data.remote

import android.util.Log
import com.example.tielink.data.local.AppPreferences
import com.example.tielink.data.remote.dto.DeepSeekRequest
import kotlinx.coroutines.flow.Flow

class DeepSeekProvider constructor(
    private val apiServiceFactory: DeepSeekApiServiceFactory,
    private val preferences: AppPreferences
) : AiProvider {
    
    override val providerName: String = "DeepSeek"
    
    override suspend fun chatCompletion(request: LlmRequest): LlmResponse {
        return try {
            val apiKey = preferences.snapshot().apiKey
            if (apiKey.isBlank()) {
                throw IllegalStateException("API Key未配置")
            }
            
            val apiService = apiServiceFactory.create()
            val deepSeekRequest = DeepSeekRequest(
                model = preferences.snapshot().model.ifBlank { "deepseek-chat" },
                messages = request.messages,
                temperature = request.temperature,
                maxTokens = request.maxTokens,
                tools = request.tools.takeIf { it.isNotEmpty() },
                toolChoice = request.toolChoice.takeIf { request.tools.isNotEmpty() }
            )
            
            Log.d(TAG, "璋冪敤DeepSeek API: messages=${request.messages.size}, temperature=${request.temperature}")
            
            val response = apiService.chatCompletion(deepSeekRequest)
            val message = response.choices.firstOrNull()?.message
                ?: throw IllegalArgumentException("API杩斿洖涓虹┖")
            val content = message.content.orEmpty()
            val toolCalls = message.toolCalls.orEmpty().map { call ->
                LlmToolCall(
                    id = call.id,
                    name = call.function.name,
                    arguments = call.function.arguments
                )
            }
            if (content.isBlank() && toolCalls.isEmpty()) {
                throw IllegalArgumentException("API杩斿洖涓虹┖")
            }
            
            Log.i(TAG, "DeepSeek API鍝嶅簲鎴愬姛: ${content.length}瀛楃")
            
            LlmResponse(
                content = content,
                model = preferences.snapshot().model.ifBlank { "deepseek-chat" },
                usage = null,
                toolCalls = toolCalls
            )
        } catch (e: Exception) {
            Log.e(TAG, "DeepSeek API璋冪敤澶辫触: ${e.message}", e)
            throw e
        }
    }

    override fun chatCompletionStream(request: LlmRequest): Flow<StreamEvent> {
        val snapshot = preferences.snapshot()
        val apiKey = snapshot.apiKey
        val baseUrl = snapshot.baseUrl
        val model = snapshot.model
        return StreamingApiService.streamOpenAiChat(
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = model,
            messages = request.messages,
            temperature = request.temperature,
            maxTokens = request.maxTokens
        )
    }
    
    override suspend fun embed(text: String): FloatArray? {
        Log.w(TAG, "DeepSeek涓嶆敮鎸丒mbedding锛岃繑鍥瀗ull")
        return null
    }
    
    override fun isAvailable(): Boolean {
        return preferences.snapshot().apiKey.isNotBlank()
    }

    companion object {
        private const val TAG = "DeepSeekProvider"
    }
}
