package com.example.tielink.data.remote

import android.util.Log
import com.example.tielink.data.remote.dto.StreamChunk
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class StreamEvent {
    data object Start : StreamEvent()
    data class Thinking(val text: String) : StreamEvent()
    data class Content(val text: String) : StreamEvent()
    data object Done : StreamEvent()
    data class Error(val message: String) : StreamEvent()
}

object StreamingApiService {

    private const val TAG = "StreamingApiService"
    private const val JSON_MEDIA_TYPE = "application/json; charset=utf-8"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor { msg ->
            Log.d(TAG, msg)
        }.apply { level = HttpLoggingInterceptor.Level.HEADERS })
        .build()

    fun streamOpenAiChat(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<com.example.tielink.data.remote.dto.Message>,
        temperature: Double = 0.7,
        maxTokens: Int = 4096
    ): Flow<StreamEvent> = callbackFlow {
        val url = runCatching {
            ApiEndpointResolver.openAiChatCompletionsUrl(baseUrl)
        }.getOrElse {
            trySend(StreamEvent.Error(it.message ?: "Base URL 无效"))
            close()
            return@callbackFlow
        }

        val json = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                messages.forEach { msg ->
                    put(JSONObject().apply {
                        put("role", msg.role)
                        put("content", msg.content)
                    })
                }
            })
            put("temperature", temperature)
            put("max_tokens", maxTokens)
            put("stream", true)
        }

        val requestBody = json.toString().toRequestBody(JSON_MEDIA_TYPE.toMediaType())

        val builder = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(requestBody)
        if (apiKey.isNotEmpty()) {
            builder.addHeader("Authorization", "Bearer $apiKey")
        }

        Log.d(TAG, "流式请求 → $url, model=$model, messages=${messages.size}")

        val call = client.newCall(builder.build())
        call.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                trySend(StreamEvent.Error("网络错误: ${e.message}"))
                close()
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                if (!response.isSuccessful) {
                    val errBody = response.body?.string().orEmpty().take(200)
                    trySend(StreamEvent.Error("请求失败 (${response.code}) $errBody"))
                    close()
                    return
                }
                trySend(StreamEvent.Start)
                response.body?.charStream()?.buffered()?.useLines { lines ->
                    lines.forEach { line ->
                        processOpenAiLine(line)?.let { event ->
                            trySend(event)
                            if (event is StreamEvent.Done) return@useLines
                        }
                    }
                }
                response.close()
                close()
            }
        })
        awaitClose { call.cancel() }
    }.flowOn(Dispatchers.IO)

    fun streamOllamaChat(
        baseUrl: String,
        model: String,
        messages: List<com.example.tielink.data.remote.dto.Message>,
        temperature: Double = 0.7
    ): Flow<StreamEvent> = callbackFlow {
        val url = "${baseUrl.trimEnd('/')}/api/chat"

        val json = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                messages.forEach { msg ->
                    put(JSONObject().apply {
                        put("role", msg.role)
                        put("content", msg.content)
                    })
                }
            })
            put("temperature", temperature)
            put("stream", true)
        }

        val requestBody = json.toString().toRequestBody(JSON_MEDIA_TYPE.toMediaType())

        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        val call = client.newCall(request)
        call.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                trySend(StreamEvent.Error("网络错误: ${e.message}"))
                close()
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                if (!response.isSuccessful) {
                    val errBody = response.body?.string().orEmpty().take(200)
                    trySend(StreamEvent.Error("请求失败 (${response.code}) $errBody"))
                    close()
                    return
                }
                trySend(StreamEvent.Start)
                response.body?.charStream()?.buffered()?.useLines { lines ->
                    lines.forEach { line ->
                        processOllamaLine(line)?.let { event ->
                            trySend(event)
                            if (event is StreamEvent.Done) return@useLines
                        }
                    }
                }
                response.close()
                close()
            }
        })
        awaitClose { call.cancel() }
    }.flowOn(Dispatchers.IO)

    private fun processOpenAiLine(line: String): StreamEvent? {
        if (line.isBlank() || line.startsWith(":") || !line.startsWith("data:")) return null
        val data = line.removePrefix("data:").trim()
        if (data == "[DONE]") return StreamEvent.Done
        return try {
            val adapter = moshi.adapter(StreamChunk::class.java)
            val chunk = adapter.fromJson(data) ?: return null
            val delta = chunk.choices?.firstOrNull()?.delta ?: return null
            delta.reasoningContent?.let { return StreamEvent.Thinking(it) }
            delta.content?.let { StreamEvent.Content(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun processOllamaLine(line: String): StreamEvent? {
        if (line.isBlank()) return null
        return try {
            val obj = JSONObject(line)
            if (obj.optBoolean("done", false)) {
                return StreamEvent.Done
            }
            val content = obj.optJSONObject("message")?.optString("content", "") ?: ""
            if (content.isNotEmpty()) StreamEvent.Content(content) else null
        } catch (_: Exception) {
            null
        }
    }
}
