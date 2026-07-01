package com.example.tielink.data.remote

import android.util.Log
import com.example.tielink.data.local.AppPreferences
import com.example.tielink.data.remote.dto.DeepSeekRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking

class DeepSeekProvider constructor(
    private val apiServiceFactory: DeepSeekApiServiceFactory,
    private val preferences: AppPreferences
) : AiProvider {
    
    override val providerName: String = "DeepSeek"
    
    override suspend fun chatCompletion(request: LlmRequest): LlmResponse {
        return try {
            val apiKey = preferences.getApiKey()
            if (apiKey.isBlank()) {
                throw IllegalStateException("API Key未配置")
            }
            
            val apiService = apiServiceFactory.create()
            val deepSeekRequest = DeepSeekRequest(
                messages = request.messages,
                temperature = request.temperature,
                maxTokens = request.maxTokens
            )
            
            Log.d(TAG, "璋冪敤DeepSeek API: messages=${request.messages.size}, temperature=${request.temperature}")
            
            val response = apiService.chatCompletion(deepSeekRequest)
            val content = response.choices.firstOrNull()?.message?.content
                ?: throw IllegalArgumentException("API杩斿洖涓虹┖")
            
            Log.i(TAG, "DeepSeek API鍝嶅簲鎴愬姛: ${content.length}瀛楃")
            
            LlmResponse(
                content = content,
                model = "deepseek-chat",
                usage = null
            )
        } catch (e: Exception) {
            Log.e(TAG, "DeepSeek API璋冪敤澶辫触: ${e.message}", e)
            throw e
        }
    }

    override fun chatCompletionStream(request: LlmRequest): Flow<StreamEvent> {
        val apiKey = runBlocking { preferences.getApiKey() }
        val baseUrl = preferences.getBaseUrlSync()
        val model = runBlocking { preferences.getModel() }
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
        return try {
            runBlocking { preferences.getApiKey().isNotBlank() }
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        private const val TAG = "DeepSeekProvider"
    }
}
