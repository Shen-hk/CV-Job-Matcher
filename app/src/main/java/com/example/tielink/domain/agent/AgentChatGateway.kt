package com.example.tielink.domain.agent

import kotlinx.coroutines.flow.Flow

/** Domain-facing contract for the Agent's conversational model capability. */
interface AgentChatGateway {
    suspend fun complete(request: AgentChatRequest): AgentChatResponse
    suspend fun stream(request: AgentChatRequest): Flow<AgentStreamEvent>
}

data class AgentChatRequest(
    val messages: List<AgentChatMessage>,
    val temperature: Double = 0.7,
    val maxTokens: Int = 4096,
    val tools: List<AgentToolDefinition> = emptyList(),
    val toolChoice: String = "auto"
)

data class AgentChatResponse(
    val content: String,
    val toolCalls: List<AgentToolCall> = emptyList()
)

data class AgentChatMessage(
    val role: String,
    val content: String? = null,
    val toolCallId: String? = null,
    val toolCalls: List<AgentMessageToolCall>? = null,
    val name: String? = null
)

data class AgentMessageToolCall(
    val id: String,
    val function: AgentFunctionCall
)

data class AgentFunctionCall(
    val name: String,
    val arguments: String
)

data class AgentToolCall(
    val id: String,
    val name: String,
    val arguments: String
)

data class AgentToolDefinition(
    val type: String = "function",
    val function: AgentFunctionDefinition
)

data class AgentFunctionDefinition(
    val name: String,
    val description: String,
    val parameters: Map<String, Any?>
)

sealed class AgentStreamEvent {
    data object Start : AgentStreamEvent()
    data class Thinking(val text: String) : AgentStreamEvent()
    data class Content(val text: String) : AgentStreamEvent()
    data object Done : AgentStreamEvent()
    data class Error(val message: String) : AgentStreamEvent()
}
