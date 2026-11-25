package com.example.tielink.domain.model

import com.squareup.moshi.Moshi

/** Normalize object ordering and JSON representation before comparing tool calls. */
object ToolCallIdentity {
    private val adapter = Moshi.Builder().build().adapter(Any::class.java)

    fun signature(toolName: String, arguments: String): String {
        val parsed = adapter.fromJson(arguments)
        require(parsed is Map<*, *>) { "工具参数必须是 JSON 对象" }
        return "$toolName:${adapter.toJson(normalize(parsed))}"
    }

    private fun normalize(value: Any?): Any? = when (value) {
        is Map<*, *> -> value.entries.sortedBy { it.key.toString() }.associate { it.key.toString() to normalize(it.value) }
        is List<*> -> value.map(::normalize)
        else -> value
    }
}
