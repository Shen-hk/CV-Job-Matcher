package com.example.tielink.data.remote.dto

import com.squareup.moshi.Json

data class DeepSeekRequest(
    val model: String = "deepseek-chat",
    val messages: List<Message>,
    val temperature: Double = 0.7,
    @field:Json(name = "max_tokens") val maxTokens: Int = 4096,
    val stream: Boolean = false
)

data class Message(
    val role: String,
    val content: String
)
