package com.example.tielink.data.remote

import android.util.Log
import com.example.tielink.data.local.AppPreferences
import com.example.tielink.domain.nlp.EmbeddingEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiProviderManager @Inject constructor(
    private val deepSeekProvider: DeepSeekProvider,
    private val ollamaProvider: OllamaProvider,
    private val preferences: AppPreferences
) {
    
    enum class Priority {
        DEEPSEEK,    // 云端API，质量最高
        OLLAMA,      // 本地/局域网Ollama，平衡
        LOCAL        // 完全本地Embedding，离线可用
    }
    
    fun getProvider(): AiProvider {
        val providerName = preferences.snapshot().aiProvider
        val priority = when (providerName) {
            "ollama" -> Priority.OLLAMA
            "local" -> Priority.LOCAL
            else -> Priority.DEEPSEEK
        }
        
        return getProviderByPriority(priority)
    }
    
    private fun getProviderByPriority(priority: Priority): AiProvider {
        return when (priority) {
            Priority.DEEPSEEK -> {
                if (deepSeekProvider.isAvailable()) {
                    Log.i(TAG, "使用DeepSeek Provider")
                    deepSeekProvider
                } else {
                    Log.w(TAG, "DeepSeek不可用，降级到Ollama")
                    fallbackToOllama()
                }
            }
            
            Priority.OLLAMA -> {
                if (ollamaProvider.isAvailable()) {
                    Log.i(TAG, "使用Ollama Provider")
                    ollamaProvider
                } else {
                    Log.w(TAG, "Ollama不可用，降级到DeepSeek")
                    fallbackToDeepSeek()
                }
            }
            
            Priority.LOCAL -> {
                Log.i(TAG, "使用本地模式（仅Embedding）")
                LocalProvider()
            }
        }
    }

    private fun fallbackToOllama(): AiProvider {
        return if (ollamaProvider.isAvailable()) {
            ollamaProvider
        } else {
            Log.e(TAG, "所有AI Provider都不可用！")
            throw IllegalStateException("没有可用的AI服务")
        }
    }

    private fun fallbackToDeepSeek(): AiProvider {
        return if (deepSeekProvider.isAvailable()) {
            deepSeekProvider
        } else {
            Log.e(TAG, "所有AI Provider都不可用！")
            throw IllegalStateException("没有可用的AI服务")
        }
    }
    
    suspend fun chatWithFallback(request: LlmRequest): LlmResponse = withContext(Dispatchers.IO) {
        val orderedProviders = when (preferences.snapshot().aiProvider) {
            "ollama" -> listOf(
                ollamaProvider to "Ollama",
                deepSeekProvider to "DeepSeek"
            )
            else -> listOf(
                deepSeekProvider to "DeepSeek",
                ollamaProvider to "Ollama"
            )
        }
        val providers = orderedProviders.filter { (provider, _) ->
            runCatching { provider.isAvailable() }.getOrDefault(false)
        }
        
        if (providers.isEmpty()) {
            throw IllegalStateException("没有可用的AI服务，请检查网络连接或配置")
        }
        
        for ((provider, name) in providers) {
            try {
                Log.d(TAG, "尝试使用$name...")
                return@withContext provider.chatCompletion(request)
            } catch (e: Exception) {
                Log.w(TAG, name + "调用失败: " + e.message + ", 尝试下一个...")
            }
        }
        
        throw Exception("所有AI服务都不可用")
    }

    /**
     * suspend 版本的流式调用，完全避免 runBlocking。
     * AgentUseCase 使用这个方法，不再调用 chatWithFallbackStream。
     */
    suspend fun chatStream(request: LlmRequest): Flow<StreamEvent> {
        val snapshot = preferences.snapshot()
        val providerName = snapshot.aiProvider
        val apiKey = snapshot.apiKey
        val model = snapshot.model
        val baseUrl = snapshot.baseUrl
        val ollamaUrl = snapshot.ollamaBaseUrl
        val ollamaModel = snapshot.ollamaModel

        return when (providerName) {
            "ollama" -> StreamingApiService.streamOllamaChat(
                baseUrl = ollamaUrl,
                model = ollamaModel,
                messages = request.messages,
                temperature = request.temperature
            )
            else -> {
                if (apiKey.isNotBlank()) {
                    StreamingApiService.streamOpenAiChat(
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                        model = model,
                        messages = request.messages,
                        temperature = request.temperature,
                        maxTokens = request.maxTokens
                    )
                } else if (snapshot.ollamaBaseUrl.isNotBlank()) {
                    StreamingApiService.streamOllamaChat(
                        baseUrl = ollamaUrl,
                        model = ollamaModel,
                        messages = request.messages,
                        temperature = request.temperature
                    )
                } else {
                    throw IllegalStateException("没有可用的 AI 服务，请在设置中配置 API Key")
                }
            }
        }
    }

    fun chatWithFallbackStream(request: LlmRequest): Flow<StreamEvent> {
        val provider = getProvider()
        return provider.chatCompletionStream(request)
    }
    
    suspend fun smartEmbed(text: String): FloatArray {
        val provider = getProvider()
        
        val embedding = provider.embed(text)
        if (embedding != null) {
            return embedding
        }
        
        Log.d(TAG, "Provider不支持Embedding，使用本地引擎")
        return EmbeddingEngine.embed(text)
    }

    companion object {
        private const val TAG = "AiProviderManager"
    }
}

class LocalProvider : AiProvider {
    override val providerName: String = "Local"
    
    override suspend fun chatCompletion(request: LlmRequest): LlmResponse {
        throw UnsupportedOperationException("本地模式不支持Chat功能")
    }

    override fun chatCompletionStream(request: LlmRequest): Flow<StreamEvent> {
        throw UnsupportedOperationException("本地模式不支持Chat功能")
    }
    
    override suspend fun embed(text: String): FloatArray? {
        return EmbeddingEngine.embed(text)
    }
    
    override fun isAvailable(): Boolean = EmbeddingEngine.isReady()
}
