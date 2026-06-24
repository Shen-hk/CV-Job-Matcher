package com.example.cv_jobmatcher.data.remote

import android.util.Log
import com.example.cv_jobmatcher.data.local.AppPreferences
import com.example.cv_jobmatcher.domain.nlp.EmbeddingEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
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
        val providerName = runBlocking { preferences.getAiProvider() }
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
    
    suspend fun chatWithFallback(request: LlmRequest): LlmResponse {
        val providers = listOf(
            deepSeekProvider to "DeepSeek",
            ollamaProvider to "Ollama"
        ).filter { it.first.isAvailable() }
        
        if (providers.isEmpty()) {
            throw IllegalStateException("没有可用的AI服务，请检查网络连接或配置")
        }
        
        for ((provider, name) in providers) {
            try {
                Log.d(TAG, "尝试使用$name...")
                return provider.chatCompletion(request)
            } catch (e: Exception) {
                Log.w(TAG, name + "调用失败: " + e.message + ", 尝试下一个...")
            }
        }
        
        throw Exception("所有AI服务都不可用")
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
