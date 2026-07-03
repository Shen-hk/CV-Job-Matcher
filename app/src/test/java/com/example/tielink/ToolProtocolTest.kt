package com.example.tielink

import com.example.tielink.data.remote.LlmFunctionDefinition
import com.example.tielink.data.remote.LlmToolDefinition
import com.example.tielink.data.remote.dto.DeepSeekRequest
import com.example.tielink.data.remote.dto.DeepSeekResponse
import com.example.tielink.data.remote.dto.Message
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolProtocolTest {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun parsesOpenAiCompatibleToolCall() {
        val json = """
            {
              "id": "response-1",
              "choices": [{
                "index": 0,
                "message": {
                  "role": "assistant",
                  "content": null,
                  "tool_calls": [{
                    "id": "call-1",
                    "type": "function",
                    "function": {
                      "name": "calculate_match",
                      "arguments": "{}"
                    }
                  }]
                }
              }]
            }
        """.trimIndent()

        val response = moshi.adapter(DeepSeekResponse::class.java).fromJson(json)
        val call = response!!.choices.single().message.toolCalls!!.single()

        assertEquals("call-1", call.id)
        assertEquals("calculate_match", call.function.name)
        assertEquals("{}", call.function.arguments)
    }

    @Test
    fun serializesToolDefinitionsAndAutoChoice() {
        val request = DeepSeekRequest(
            messages = listOf(Message("user", "分析匹配度")),
            tools = listOf(
                LlmToolDefinition(
                    function = LlmFunctionDefinition(
                        name = "calculate_match",
                        description = "计算匹配度",
                        parameters = mapOf(
                            "type" to "object",
                            "properties" to emptyMap<String, Any?>(),
                            "required" to emptyList<String>(),
                            "additionalProperties" to false
                        )
                    )
                )
            ),
            toolChoice = "auto"
        )

        val json = moshi.adapter(DeepSeekRequest::class.java).toJson(request)

        assertTrue(json.contains("\"tools\""))
        assertTrue(json.contains("\"tool_choice\":\"auto\""))
        assertTrue(json.contains("\"name\":\"calculate_match\""))
    }
}
