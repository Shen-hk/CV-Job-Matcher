package com.example.tielink.data.remote.dto

import com.squareup.moshi.Json

data class DeepSeekResponse(
    val id: String,
    val choices: List<Choice>
)

data class Choice(
    val index: Int,
    val message: MessageDto
)

data class MessageDto(
    val role: String,
    val content: String? = null,
    @param:Json(name = "tool_calls") val toolCalls: List<ResponseToolCall>? = null
)

data class ResponseToolCall(
    val id: String,
    val type: String = "function",
    val function: ResponseFunctionCall
)

data class ResponseFunctionCall(
    val name: String,
    val arguments: String
)
