package com.example.tielink.data.remote

import android.util.Log
import com.example.tielink.data.local.AppPreferences
import com.example.tielink.domain.nlp.EmbeddingEngine
import kotlinx.coroutines.flow.Flow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class OllamaProvider constructor(
    private val preferences: AppPreferences
) : AiProvider {

    override val providerName: String = "Ollama"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val jsonMediaType = "application/json".toMediaType()

    override suspend fun chatCompletion(request: LlmRequest): LlmResponse {
        return try {
            val snapshot = preferences.snapshot()
            val baseUrl = snapshot.ollamaBaseUrl
            val model = snapshot.ollamaModel

            if (baseUrl.isBlank() || model.isBlank()) {
                throw IllegalStateException("Ollama未配置")
            }

            Log.d(TAG, "调用Ollama API: url=$baseUrl, model=$model")

            val json = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    request.messages.forEach { msg ->
                        put(JSONObject().apply {
                            put("role", msg.role)
                            put("content", msg.content)
                        })
                    }
                })
                put("temperature", request.temperature)
                put("stream", false)
            }

            val requestBody = json.toString().toRequestBody(jsonMediaType)

            val httpRequest = Request.Builder()
                .url("$baseUrl/api/chat")
                .post(requestBody)
                .build()

            val response = client.newCall(httpRequest).execute()

            if (!response.isSuccessful) {
                throw Exception("Ollama请求失败: ${response.code}")
            }

            val responseBody = response.body?.string() ?: throw Exception("响应为空")
            val jsonResponse = JSONObject(responseBody)

            val content = jsonResponse.getJSONObject("message")?.getString("content") ?: ""
            val actualModel = jsonResponse.getString("model")

            Log.i(TAG, "Ollama API响应成功: ${content.length}字符, model=$actualModel")

            LlmResponse(
                content = content,
                model = actualModel,
                usage = null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Ollama API调用失败: ${e.message}", e)
            throw e
        }
    }

    override fun chatCompletionStream(request: LlmRequest): Flow<StreamEvent> {
        val snapshot = preferences.snapshot()
        return StreamingApiService.streamOllamaChat(
            baseUrl = snapshot.ollamaBaseUrl,
            model = snapshot.ollamaModel,
            messages = request.messages,
            temperature = request.temperature
        )
    }

    override suspend fun embed(text: String): FloatArray? {
        return try {
            if (!EmbeddingEngine.isReady()) {
                Log.w(TAG, "Embedding引擎未就绪，尝试使用Ollama Embedding")
            }

            val snapshot = preferences.snapshot()
            val baseUrl = snapshot.ollamaBaseUrl
            val embedModel = snapshot.ollamaEmbedModel

            if (baseUrl.isBlank()) return null

            Log.d(TAG, "调用Ollama Embedding: model=$embedModel")

            val json = JSONObject().apply {
                put("model", embedModel.ifBlank { "nomic-embed-text" })
                put("prompt", text)
            }

            val requestBody = json.toString().toRequestBody(jsonMediaType)

            val httpRequest = Request.Builder()
                .url("$baseUrl/api/embeddings")
                .post(requestBody)
                .build()

            val response = client.newCall(httpRequest).execute()

            if (!response.isSuccessful) {
                Log.w(TAG, "Ollama Embedding请求失败: ${response.code}")
                return null
            }

            val responseBody = response.body?.string() ?: return null
            val jsonResponse = JSONObject(responseBody)

            val embeddingArray = jsonResponse.getJSONArray("embedding")
            val embedding = FloatArray(embeddingArray.length()) { i ->
                embeddingArray.getDouble(i).toFloat()
            }

            Log.d(TAG, "Ollama Embedding成功: 维度=${embedding.size}")
            embedding
        } catch (e: Exception) {
            Log.e(TAG, "Ollama Embedding失败: ${e.message}", e)
            null
        }
    }

    override fun isAvailable(): Boolean {
        val baseUrl = preferences.snapshot().ollamaBaseUrl
        return baseUrl.isNotBlank() && isServerReachable(baseUrl)
    }

    private fun isServerReachable(baseUrl: String): Boolean {
        return try {
            val request = Request.Builder()
                .url("$baseUrl/api/tags")
                .get()
                .build()

            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        private const val TAG = "OllamaProvider"
    }
}
