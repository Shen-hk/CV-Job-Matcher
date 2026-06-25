package com.example.tielink.domain.nlp

import com.example.tielink.domain.model.AgentIntent
import com.example.tielink.domain.model.IntentType
import com.example.tielink.domain.model.ToolCall

/**
 * 意图识别器 - 规则层
 * 先用关键词规则快速匹配高置信度意图
 */
object IntentClassifier {

    // 意图关键词映射
    private val intentKeywords = mapOf(
        IntentType.JD_ANALYZE to listOf(
            "分析岗位", "看jd", "岗位描述", "职位要求", "这个岗位", "这个职位",
            "粘贴jd", "发个jd", "帮我看看这个", "这份工作"
        ),
        IntentType.MATCH to listOf(
            "匹配度", "匹配吗", "胜率", "合适吗", "合适不", "能不能过",
            "有多大把握", "几率多大", "差距", "缺什么", "差在哪"
        ),
        IntentType.RESUME_EDIT to listOf(
            "改简历", "优化简历", "修改简历", "润色简历", "简历改", "简历优化",
            "换个说法", "重写", "帮我改", "改一下", "调整简历", "简历调整"
        ),
        IntentType.INTERVIEW to listOf(
            "模拟面试", "练面试", "面试模拟", "准备面试", "面试准备",
            "我要面试", "帮我练", "模拟一下", "面一下"
        ),
        IntentType.TRACKING to listOf(
            "投递", "投了", "记录投递", "投递记录", "投递状态",
            "跟进", "跟进一下", "投了哪", "投了哪些"
        ),
        IntentType.PLATFORM to listOf(
            "打招呼", "话术", "生成话术", "boss", "猎聘",
            "帮我写个打招呼", "怎么打招呼"
        ),
        IntentType.DEBRIEF to listOf(
            "复盘", "面试复盘", "上传录音", "面试录音", "录音分析",
            "分析面试", "复盘面试"
        )
    )

    /**
     * 识别用户意图
     * @param userText 用户输入文本
     * @return 识别出的意图，包含置信度和可能的工具调用
     */
    fun classify(userText: String): AgentIntent {
        val lowerText = userText.lowercase().trim()

        // 1. 遍历关键词规则，找到最高置信度的匹配
        var bestMatch: Pair<IntentType, Int>? = null
        var matchedKeywords = 0

        for ((intentType, keywords) in intentKeywords) {
            val count = keywords.count { keyword ->
                lowerText.contains(keyword.lowercase())
            }
            if (count > matchedKeywords) {
                matchedKeywords = count
                bestMatch = intentType to count
            }
        }

        // 2. 根据匹配结果返回意图
        return when {
            bestMatch != null && matchedKeywords >= 2 -> {
                // 高置信度：匹配到2个及以上关键词
                createIntentForType(bestMatch.first, userText)
            }
            bestMatch != null -> {
                // 中置信度：匹配到1个关键词，需要简单确认
                AgentIntent(
                    type = bestMatch.first,
                    clarificationNeeded = true,
                    clarificationPrompt = "你是想要${getIntentDescription(bestMatch.first)}吗？"
                )
            }
            else -> {
                // 低置信度：没有匹配到任何关键词，走普通聊天
                AgentIntent(type = IntentType.CHAT)
            }
        }
    }

    /**
     * 为特定意图类型创建工具调用
     */
    private fun createIntentForType(type: IntentType, userText: String): AgentIntent {
        return when (type) {
            IntentType.JD_ANALYZE -> AgentIntent(
                type = type,
                toolCall = ToolCall(
                    toolName = "jd_tool",
                    function = "analyze_jd",
                    params = mapOf("text" to userText)
                )
            )
            IntentType.MATCH -> AgentIntent(
                type = type,
                toolCall = ToolCall(
                    toolName = "match_tool",
                    function = "calculate_match",
                    params = emptyMap() // 需要从 AgentContext 获取当前 JD 和简历
                )
            )
            IntentType.RESUME_EDIT -> AgentIntent(
                type = type,
                toolCall = ToolCall(
                    toolName = "resume_tool",
                    function = "edit_section",
                    params = mapOf("instruction" to userText)
                )
            )
            IntentType.INTERVIEW -> AgentIntent(
                type = type,
                toolCall = ToolCall(
                    toolName = "interview_tool",
                    function = "start_interview",
                    params = emptyMap()
                )
            )
            IntentType.TRACKING -> AgentIntent(
                type = type,
                toolCall = ToolCall(
                    toolName = "tracking_tool",
                    function = "create_application",
                    params = emptyMap()
                )
            )
            IntentType.PLATFORM -> AgentIntent(
                type = type,
                toolCall = ToolCall(
                    toolName = "platform_tool",
                    function = "generate_greeting",
                    params = emptyMap()
                )
            )
            IntentType.DEBRIEF -> AgentIntent(
                type = type,
                toolCall = ToolCall(
                    toolName = "debrief_tool",
                    function = "upload_recording",
                    params = emptyMap()
                )
            )
            IntentType.CHAT -> AgentIntent(type = type)
        }
    }

    /**
     * 获取意图描述，用于澄清提示
     */
    private fun getIntentDescription(type: IntentType): String {
        return when (type) {
            IntentType.JD_ANALYZE -> "分析岗位"
            IntentType.MATCH -> "进行匹配度分析"
            IntentType.RESUME_EDIT -> "优化简历"
            IntentType.INTERVIEW -> "开始模拟面试"
            IntentType.TRACKING -> "管理投递记录"
            IntentType.PLATFORM -> "生成打招呼话术"
            IntentType.DEBRIEF -> "复盘面试"
            IntentType.CHAT -> "聊天"
        }
    }
}
