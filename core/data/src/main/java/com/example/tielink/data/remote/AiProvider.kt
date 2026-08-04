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
    val stream: Boolean = false,
    val tools: List<LlmToolDefinition> = emptyList(),
    val toolChoice: String = "auto"
)

data class LlmResponse(
    val content: String,
    val model: String,
    val usage: TokenUsage? = null,
    val toolCalls: List<LlmToolCall> = emptyList()
)

data class LlmToolDefinition(
    val type: String = "function",
    val function: LlmFunctionDefinition
)

data class LlmFunctionDefinition(
    val name: String,
    val description: String,
    val parameters: Map<String, Any?>
)

data class LlmToolCall(
    val id: String,
    val name: String,
    /** JSON object encoded as text, matching OpenAI-compatible tool-call responses. */
    val arguments: String
)

data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)
