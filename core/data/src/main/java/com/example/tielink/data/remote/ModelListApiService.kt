package com.example.tielink.data.remote

import com.example.tielink.data.local.db.entity.ProviderEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 拉取 Provider 模型列表。
 *
 * - OpenAI 兼容: GET {resolvedModelListUrl} + Authorization: Bearer {key}
 * - Anthropic   : GET {resolvedModelListUrl} + x-api-key: {key} + anthropic-version
 *
 * 常见响应形态包括 { "data": [...] }、{ "models": [...] } 或顶层数组，统一抽取 id/name/model。
 */
object ModelListApiService {

    private const val ANTHROPIC_VERSION = "2023-06-01"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun fetchModels(
        baseUrl: String,
        apiKey: String,
        apiFormat: String
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = ApiEndpointResolver.modelListUrl(baseUrl)
            val builder = Request.Builder().url(url).get()
            when (apiFormat) {
                ProviderEntity.API_FORMAT_ANTHROPIC -> {
                    if (apiKey.isNotEmpty()) {
                        builder.addHeader("x-api-key", apiKey)
                    }
                    builder.addHeader("anthropic-version", ANTHROPIC_VERSION)
                }
                else -> {
                    if (apiKey.isNotEmpty()) {
                        builder.addHeader("Authorization", "Bearer $apiKey")
                    }
                }
            }
            builder.addHeader("Accept", "application/json")

            client.newCall(builder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw RuntimeException("GET $url -> HTTP ${response.code}: ${body.take(200)}")
                }
                parseModelIds(body)
            }
        }
    }

    private fun parseModelIds(body: String): List<String> {
        val trimmed = body.trim()
        val modelArray: JSONArray? = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            trimmed.startsWith("{") -> {
                val obj = JSONObject(trimmed)
                // Try "data" first, then "models"
                val dataArray = obj.optJSONArray("data")
                val modelsArray = obj.optJSONArray("models")
                dataArray ?: modelsArray
            }
            else -> null
        }

        if (modelArray == null) return emptyList()

        val result = mutableListOf<String>()
        for (i in 0 until modelArray.length()) {
            val element = modelArray.get(i)
            val name: String? = when (element) {
                is String -> element
                is JSONObject -> {
                    // Try "id" first, then "name", then "model"
                    element.optString("id").takeIf { it.isNotBlank() }
                        ?: element.optString("name").takeIf { it.isNotBlank() }
                        ?: element.optString("model").takeIf { it.isNotBlank() }
                }
                else -> null
            }
            name?.trim()?.takeIf { it.isNotBlank() }?.let { result.add(it) }
        }

        return result.distinct()
    }
}
