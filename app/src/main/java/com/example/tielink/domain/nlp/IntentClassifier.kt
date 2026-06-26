package com.example.tielink.domain.nlp

import com.example.tielink.domain.model.AgentIntent
import com.example.tielink.domain.model.IntentType
import com.example.tielink.domain.model.ToolCall

/**
 * 意图识别器 - 规则层
 * 先用关键词规则快速匹配高置信度意图
 */
object IntentClassifier {

    // 意图关键词映射 — 每个 IntentType 有一组关键词/短语，用 contains 做子串匹配
    private val intentKeywords = mapOf(
        IntentType.MATCH to listOf(
            // 精确命中
            "匹配度", "匹配吗", "匹配情况", "匹配分析", "匹配率",
            "胜率", "合适吗", "合适不", "能不能过", "有多大把握", "几率多大",
            // 自然语言
            "匹不匹配", "配不配", "符不符合", "差距", "缺什么", "差在哪",
            "能过吗", "通过率", "竞争力", "有竞争力", "有戏吗", "有希望吗",
            "帮我分析", "帮我匹配", "分析匹配", "看看匹配", "查匹配",
            "岗位匹配", "职位匹配", "jd匹配", "匹配一下", "匹配看看",
            "打多少分", "多少分", "能打几分", "评分"
        ),
        IntentType.RESUME_EDIT to listOf(
            // 精确命中
            "改简历", "优化简历", "修改简历", "润色简历", "简历改", "简历优化",
            "调整简历", "简历调整", "简历润色", "简历修改",
            // 自然语言
            "换个说法", "重写", "帮我改", "改一下", "改写",
            "优化经历", "改经历", "改描述", "改写经历",
            "润色", "美化", "措辞", "改措辞", "量化", "帮我量化",
            "star", "star法则", "star改写", "行动", "成果",
            "简历不好", "简历差", "简历太", "改改简历"
        ),
        IntentType.INTERVIEW to listOf(
            // 精确命中
            "模拟面试", "练面试", "面试模拟", "准备面试", "面试准备",
            // 自然语言
            "我要面试", "帮我练", "模拟一下", "面一下", "练习面试",
            "面试练习", "练一下", "开始面试", "进入面试", "打开面试",
            "来面试", "搞个面试", "做个面试", "帮我面试",
            "面试官", "面我", "考考我", "问我", "出题"
        ),
        IntentType.TRACKING to listOf(
            // 精确命中
            "记录投递", "投递记录", "投递状态",
            // 自然语言
            "投递", "投了", "跟进", "跟进一下", "投了哪", "投了哪些",
            "投了什么", "投过什么", "投了没", "投递情况", "投递进度",
            "申请记录", "申请状态", "申请进度", "投了几个",
            "记录一下", "记一下", "添加投递", "新建投递"
        ),
        IntentType.PLATFORM to listOf(
            // 精确命中
            "打招呼", "话术", "生成话术", "怎么打招呼",
            // 自然语言
            "帮我写个打招呼", "帮我写话术", "写个话术",
            "boss", "boss直聘", "猎聘", "拉钩", "智联",
            "招聘软件", "招聘平台", "招聘app",
            "怎么开口", "怎么聊", "开场白", "私信", "发消息"
        ),
        IntentType.JD_ANALYZE to listOf(
            "分析岗位", "看jd", "岗位描述", "职位要求", "这个岗位", "这个职位",
            "粘贴jd", "发个jd", "帮我看看这个", "这份工作",
            "jd分析", "分析jd", "解读岗位", "岗位分析"
        ),
        IntentType.DEBRIEF to listOf(
            "复盘", "面试复盘", "上传录音", "面试录音", "录音分析",
            "分析面试", "复盘面试", "回顾面试", "面试回顾"
        )
    )

    // 弱关键词：短词/高频词，需要 ≥2 个命中才触发，避免误判
    private val weakKeywords = mapOf(
        IntentType.MATCH to listOf(
            "匹配", "胜率", "差距", "缺什么", "差在哪", "分析", "评估",
            "打分", "多少分", "竞争力", "胜算"
        ),
        IntentType.RESUME_EDIT to listOf(
            "改", "优化", "润色", "修改", "重写", "措辞", "量化",
            "star", "美化", "提升"
        ),
        IntentType.INTERVIEW to listOf(
            "面试", "模拟", "练习", "面", "考官", "提问"
        ),
        IntentType.TRACKING to listOf(
            "投递", "投了", "跟进", "记录", "申请", "进度", "跟踪"
        ),
        IntentType.PLATFORM to listOf(
            "打招呼", "话术", "开场", "私信", "撩", "回复"
        )
    )

    /**
     * 识别用户意图
     * @param userText 用户输入文本
     * @return 识别出的意图，包含置信度和可能的工具调用
     */
    fun classify(userText: String): AgentIntent {
        val lowerText = userText.lowercase().trim()

        // 1. 先检查强关键词（长短语，单次命中即可触发）
        for ((intentType, keywords) in intentKeywords) {
            val matchCount = keywords.count { lowerText.contains(it.lowercase()) }
            if (matchCount > 0) {
                return createIntentForType(intentType, userText)
            }
        }

        // 2. 再检查弱关键词（短词/高频词，需要 ≥2 个命中才触发）
        for ((intentType, weakWords) in weakKeywords) {
            val matchCount = weakWords.count { lowerText.contains(it.lowercase()) }
            if (matchCount >= 2) {
                return createIntentForType(intentType, userText)
            }
        }

        // 3. 都没有匹配到，走普通聊天
        return AgentIntent(type = IntentType.CHAT)
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
