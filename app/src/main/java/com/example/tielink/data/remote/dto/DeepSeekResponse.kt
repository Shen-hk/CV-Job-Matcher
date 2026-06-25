package com.example.tielink.data.remote.dto

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
    val content: String
)
