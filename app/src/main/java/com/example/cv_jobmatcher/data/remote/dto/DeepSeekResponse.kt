package com.example.cv_jobmatcher.data.remote.dto

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
