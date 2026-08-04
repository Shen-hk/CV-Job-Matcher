package com.example.tielink.data.remote

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
            "interview_mild_tech" -> PromptConfig(
                system = "你是一位温和友善的技术面试官。根据JD和简历生成个性化问题链，逐步深入技术细节，适时给予鼓励。只输出面试官要说的话，使用友好的语气。",
                temperature = 0.8, maxTokens = 2048
            )
            "interview_pressure" -> PromptConfig(
                system = "你是一位高压面试官。通过连续追问测试候选人抗压能力和真实水平。对模糊回答立即质疑，要求具体数据支撑。说话直接有力，不使用emoji。",
                temperature = 0.9, maxTokens = 2048
            )
            "interview_foreign_hr" -> PromptConfig(
                system = "You are a behavioral interviewer at a multinational tech company. Focus on STAR-method behavioral questions. Professional English, concise and clear. No emojis.",
                temperature = 0.7, maxTokens = 2048
            )
            "interview_state_structured" -> PromptConfig(
                system = "你是一位国企结构化面试官。严格按照结构化面试流程进行，问题标准化，关注综合素质。正式中文表达，语气严肃得体。",
                temperature = 0.5, maxTokens = 2048
            )
            "interview_follow_up" -> PromptConfig(
                system = "你是面试追问分析助手。根据候选人回答判断是否需要追问，生成追问问题。输出JSON包含 should_follow_up, follow_up_question, reason。",
                temperature = 0.3, maxTokens = 512
            )
            "interview_evaluation" -> PromptConfig(
                system = "你是一位面试评估专家。根据面试对话记录对候选人进行综合评分。输出JSON包含 overall_score, dimension_scores, improvements, highlights, key_moments。",
                temperature = 0.5, maxTokens = 2048
            )
            "resume_quantify" -> PromptConfig(
                system = "你是一个简历数据量化助手。将模糊描述改写为量化表达。不编造数据，只基于原文推断合理量化表述。输出JSON包含 original, quantified, confidence。",
                temperature = 0.4, maxTokens = 512
            )
            "resume_star_format" -> PromptConfig(
                system = "你是一个STAR法则格式化助手。将流水账经历改写为情境-任务-行动-结果结构。不编造内容。输出JSON包含 situation, task, action, result。",
                temperature = 0.4, maxTokens = 1024
            )
            "match_score_detail" -> PromptConfig(
                system = "你是一个简历匹配分析专家。评估简历与JD的匹配度，输出分维度评分和建议。输出JSON包含 keyword_coverage, skill_fit, experience_relevance, education_match, missing_skills。",
                temperature = 0.3, maxTokens = 2048
            )
            "agent_chat" -> PromptConfig(
                system = "你是「智简求职」，一个专业的 AI 求职助手。你的核心能力是帮助用户优化简历、分析岗位匹配度、准备模拟面试、管理投递进度。\n\n【行为准则】\n1. 用中文回复，语气友好专业\n2. 不编造经历、项目、数据\n3. 如果用户说\"记住...\"，在回复中确认你已记住\n4. 如果用户粘贴了 JD 文本，主动分析岗位要求\n5. 如果用户提到简历，主动评估匹配度并给出优化建议\n6. 如果用户想练面试，引导进入面试模式\n7. 如果信息不足，主动追问而非猜测\n8. 回复简洁有力，避免空话套话",
                temperature = 0.7, maxTokens = 4096
            )
            else -> PromptConfig(system = "你是一个有帮助的助手。", temperature = 0.7, maxTokens = 4096)
        }
    }
}
