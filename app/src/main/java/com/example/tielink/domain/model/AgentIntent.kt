package com.example.tielink.domain.model

/**
 * Agent 意图识别结果
 */
data class AgentIntent(
    val type: IntentType,
    val toolCall: ToolCall? = null,
    val clarificationNeeded: Boolean = false,
    val clarificationPrompt: String? = null
)

enum class IntentType {
    JD_ANALYZE,      // 分析 JD
    RESUME_EDIT,     // 编辑简历
    MATCH,           // 匹配分析
    INTERVIEW,       // 模拟面试
    TRACKING,        // 投递追踪
    PLATFORM,        // 外部平台集成
    DEBRIEF,         // 面试复盘
    CHAT             // 普通聊天
}

/**
 * 工具调用请求
 */
data class ToolCall(
    val toolName: String,
    val function: String,
    val params: Map<String, Any>
)
