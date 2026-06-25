package com.example.tielink.data.remote.dto

import com.squareup.moshi.Json

data class StreamChunk(
    val id: String? = null,
    @field:Json(name = "object") val objectType: String? = null,
    val created: Long? = null,
    val model: String? = null,
    val choices: List<StreamChoice>? = null
)

data class StreamChoice(
    val index: Int,
    val delta: StreamDelta? = null,
    @field:Json(name = "finish_reason") val finishReason: String? = null
)

data class StreamDelta(
    val role: String? = null,
    val content: String? = null,
    @field:Json(name = "reasoning_content") val reasoningContent: String? = null
)
