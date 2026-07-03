package com.example.tielink.domain.usecase

import com.example.tielink.data.remote.LlmToolDefinition
import com.example.tielink.domain.model.UiCard
import org.json.JSONObject

/**
 * 可由 Hilt multibinding 注入的 Agent 工具扩展点。
 *
 * 新工具只需实现此接口，并使用 @IntoSet 绑定；Agent 循环会自动把 definition
 * 交给模型，并把模型返回的参数传给 execute。
 */
interface AgentTool {
    val definition: LlmToolDefinition
    val progressDescription: String

    suspend fun execute(
        arguments: JSONObject,
        fallbackUserText: String
    ): ToolExecutionResult
}

data class ToolExecutionResult(
    /** 会作为 tool result 回传给模型，因此不能包含密钥或只供内部使用的数据。 */
    val content: String,
    val cards: List<UiCard> = emptyList()
)
