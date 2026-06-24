package com.example.cv_jobmatcher.domain.model

/**
 * Agent 输出事件流，ViewModel → UI 的通信管道
 * 用于解耦工具调用和 UI 渲染
 */
sealed class AgentOutput {
    /**
     * 流式文本片段，追加到当前气泡
     */
    data class StreamText(val chunk: String) : AgentOutput()

    /**
     * 工具开始调用，显示 loading 状态
     */
    data class ToolStart(val toolName: String, val description: String) : AgentOutput()

    /**
     * 工具调用结束，插入富卡片
     */
    data class ToolResult(val card: UiCard) : AgentOutput()

    /**
     * 需要用户澄清，展示澄清问题
     */
    data class ClarificationRequest(val question: String, val options: List<String>) : AgentOutput()

    /**
     * 错误信息
     */
    data class Error(val message: String) : AgentOutput()

    /**
     * 对话结束标记
     */
    object Done : AgentOutput()
}

/**
 * 富卡片 UI 模型，对应不同工具的返回结果
 */
sealed class UiCard {
    data class MatchCard(
        val overallScore: Int,
        val keywordScore: Int,
        val experienceScore: Int,
        val educationScore: Int,
        val skillScore: Int,
        val missingSkills: List<String>,
        val highlights: List<String>
    ) : UiCard()

    data class ResumeDiffCard(
        val section: String,
        val before: String,
        val after: String,
        val onAccept: () -> Unit,
        val onRollback: () -> Unit
    ) : UiCard()

    data class ResumePreviewCard(
        val versionName: String,
        val versionId: Long,
        val previewText: String
    ) : UiCard()

    data class EvalCard(
        val overallScore: Int,
        val dimensions: Map<String, Int>,
        val keyMoments: List<String>
    ) : UiCard()

    data class TrackingCard(
        val company: String,
        val status: String,
        val applicationId: Long
    ) : UiCard()

    data class GreetingCard(
        val companyName: String,
        val position: String,
        val greetings: List<GreetingVersion>
    ) : UiCard()

    data class InterviewTurnCard(
        val questionNumber: Int,
        val totalQuestions: Int,
        val question: String,
        val feedback: String?
    ) : UiCard()
}

data class GreetingVersion(
    val style: String, // "简洁版" / "详细版" / "亮点突出版"
    val content: String,
    val highlightedSkills: List<String>
)
