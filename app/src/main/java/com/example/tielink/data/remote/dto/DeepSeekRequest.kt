package com.example.tielink.data.remote.dto

import com.squareup.moshi.Json
import com.example.tielink.data.remote.LlmToolDefinition

data class DeepSeekRequest(
    val model: String = "deepseek-chat",
    val messages: List<Message>,
    val temperature: Double = 0.7,
    @param:Json(name = "max_tokens") val maxTokens: Int = 4096,
    val stream: Boolean = false,
    val tools: List<LlmToolDefinition>? = null,
    @param:Json(name = "tool_choice") val toolChoice: String? = null
)

data class Message(
    val role: String,
    val content: String? = null,
    @param:Json(name = "tool_call_id") val toolCallId: String? = null,
    @param:Json(name = "tool_calls") val toolCalls: List<MessageToolCall>? = null,
    /** Function name for native Ollama tool-result messages. */
    val name: String? = null
)

data class MessageToolCall(
    val id: String,
    val type: String = "function",
    val function: MessageFunctionCall
)

data class MessageFunctionCall(
    val name: String,
    val arguments: String
)
