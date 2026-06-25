package com.example.tielink.data.remote

import com.example.tielink.data.remote.dto.Message
import kotlinx.coroutines.flow.Flow

interface AiProvider {
    val providerName: String
    suspend fun chatCompletion(request: LlmRequest): LlmResponse
    fun chatCompletionStream(request: LlmRequest): Flow<StreamEvent>
    suspend fun embed(text: String): FloatArray?
    fun isAvailable(): Boolean
}

data class LlmRequest(
    val messages: List<Message>,
    val temperature: Double = 0.7,
    val maxTokens: Int = 4096,
    val stream: Boolean = false
)

data class LlmResponse(
    val content: String,
    val model: String,
    val usage: TokenUsage? = null
)

data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)
