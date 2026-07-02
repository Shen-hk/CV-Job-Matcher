package com.example.tielink.domain.model

/**
 * Agent 输出事件流，ViewModel → UI 的通信管道
 * 用于解耦工具调用和 UI 渲染
 */
sealed class AgentOutput {
    data class ProcessPhase(
        val stage: AgentProcessStage,
        val title: String,
        val detail: String = "",
        val sourceLabel: String? = null,
        val sourceBreakdown: List<String> = emptyList(),
        val canCancel: Boolean = false
    ) : AgentOutput()

    data class StreamText(val chunk: String) : AgentOutput()
    data class Thinking(val chunk: String) : AgentOutput()
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
     * 工具调用因缺少前置条件被取消（回退到 LLM 文本回复），
     * ViewModel 收到后应移除对应的 toolLoading 气泡。
     */
    data class ToolCancelled(val toolName: String) : AgentOutput()

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
        val onRollback: () -> Unit,
        /** 交互状态：决定卡片渲染采用/撤回反馈 */
        val status: DiffStatus = DiffStatus.PENDING
    ) : UiCard()

    data class ResumePreviewCard(
        val versionName: String,
        val versionId: Long,
        val previewText: String,
        val resumeData: com.example.tielink.domain.model.ResumeData? = null,
        val onNavigateToResult: (() -> Unit)? = null
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

    data class UploadPromptCard(
        val title: String,
        val description: String,
        val toolName: String = "",
        val onUpload: () -> Unit = {}
    ) : UiCard()

    data class ResumeSourceChoiceCard(
        val title: String,
        val description: String,
        val libraryActionLabel: String = "从简历库选择",
        val uploadActionLabel: String = "上传新简历"
    ) : UiCard()
}

data class GreetingVersion(
    val style: String, // "简洁版" / "详细版" / "亮点突出版"
    val content: String,
    val highlightedSkills: List<String>
)

/** ResumeDiffCard 的交互状态 */
enum class DiffStatus {
    PENDING,    // 待用户决定
    ACCEPTED,   // 已采用并写回简历
    ROLLED_BACK,// 已撤回（保留原文）
    FAILED      // 采用失败（原文未在简历中定位到）
}
