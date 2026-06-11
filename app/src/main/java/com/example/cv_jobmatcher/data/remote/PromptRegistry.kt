package com.example.cv_jobmatcher.data.remote

import android.content.Context
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class PromptConfig(
    val system: String,
    val temperature: Double = 0.7,
    val maxTokens: Int = 4096
)

@Singleton
class PromptRegistry @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "PromptRegistry"
        private const val FILE_NAME = "prompts.json"
    }

    private var configs: Map<String, PromptConfig> = emptyMap()
    private var loaded = false

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        try {
            val json = context.assets.open(FILE_NAME).bufferedReader().use { it.readText() }
            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val type = com.squareup.moshi.Types.newParameterizedType(
                Map::class.java, String::class.java, Any::class.java
            )
            val rawMap: Map<String, Any>? = moshi.adapter<Map<String, Any>>(type).fromJson(json)
            rawMap?.forEach { (key, value) ->
                @Suppress("UNCHECKED_CAST")
                val map = value as? Map<String, Any> ?: return@forEach
                configs = configs + (key to PromptConfig(
                    system = map["system"] as? String ?: "",
                    temperature = (map["temperature"] as? Number)?.toDouble() ?: 0.7,
                    maxTokens = (map["maxTokens"] as? Number)?.toInt() ?: 4096
                ))
            }
            Log.i(TAG, "加载 ${configs.size} 个 prompt 配置: ${configs.keys}")
        } catch (e: Exception) {
            Log.e(TAG, "加载 prompts.json 失败: ${e.message}", e)
        }
    }

    fun get(key: String): PromptConfig {
        ensureLoaded()
        return configs[key] ?: fallback(key)
    }

    private fun fallback(key: String): PromptConfig {
        Log.w(TAG, "Prompt '$key' 未找到，使用硬编码回退")
        return when (key) {
            "polish_full" -> PromptConfig(
                system = "你是一个资深简历优化顾问。根据JD对简历进行优化，提高ATS匹配度。不编造经历/项目/数据，用JD关键词替换原文措辞，匹配JD的经验和技能提前重点描述，量化已有成果。只输出JSON对象。",
                temperature = 0.7, maxTokens = 4096
            )
            "polish_partial" -> PromptConfig(
                system = "你是一个简历微调顾问。只对简历进行最小化修改使其更匹配JD。不编造经历/项目/数据，保持原文段落结构和顺序完全不变。只输出JSON对象。",
                temperature = 0.3, maxTokens = 4096
            )
            "polish_iterative" -> PromptConfig(
                system = "你是一个简历迭代优化顾问。根据用户指令对简历进行针对性调整。不编造经历/项目/数据，只修改指令涉及的部分。只输出JSON对象。",
                temperature = 0.5, maxTokens = 4096
            )
            else -> PromptConfig(system = "你是一个有帮助的助手。", temperature = 0.7, maxTokens = 4096)
        }
    }
}
