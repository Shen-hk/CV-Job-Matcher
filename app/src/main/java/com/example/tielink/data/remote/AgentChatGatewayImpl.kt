package com.example.tielink.data.remote

import com.example.tielink.data.remote.dto.Message
import com.example.tielink.data.remote.dto.MessageFunctionCall
import com.example.tielink.data.remote.dto.MessageToolCall
import com.example.tielink.domain.agent.AgentChatGateway
import com.example.tielink.domain.agent.AgentChatMessage
import com.example.tielink.domain.agent.AgentChatRequest
import com.example.tielink.domain.agent.AgentChatResponse
import com.example.tielink.domain.agent.AgentFunctionCall
import com.example.tielink.domain.agent.AgentFunctionDefinition
import com.example.tielink.domain.agent.AgentMessageToolCall
import com.example.tielink.domain.agent.AgentPromptSource
import com.example.tielink.domain.agent.AgentPromptTemplate
import com.example.tielink.domain.agent.AgentStreamEvent
import com.example.tielink.domain.agent.AgentToolCall
import com.example.tielink.domain.agent.AgentToolDefinition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Keeps OpenAI/Ollama protocol details in the data layer. */
@Singleton
class AgentChatGatewayImpl @Inject constructor(
    private val providerManager: AiProviderManager
) : AgentChatGateway {
    override suspend fun complete(request: AgentChatRequest): AgentChatResponse {
        val response = providerManager.chatWithFallback(request.toRemote())
        return AgentChatResponse(
            content = response.content,
            toolCalls = response.toolCalls.map { call ->
                AgentToolCall(call.id, call.name, call.arguments)
            }
        )
    }

    override suspend fun stream(request: AgentChatRequest): Flow<AgentStreamEvent> =
        providerManager.chatStream(request.toRemote()).map { event ->
            when (event) {
                is StreamEvent.Start -> AgentStreamEvent.Start
                is StreamEvent.Thinking -> AgentStreamEvent.Thinking(event.text)
                is StreamEvent.Content -> AgentStreamEvent.Content(event.text)
                is StreamEvent.Done -> AgentStreamEvent.Done
                is StreamEvent.Error -> AgentStreamEvent.Error(event.message)
            }
        }

    private fun AgentChatRequest.toRemote(): LlmRequest = LlmRequest(
        messages = messages.map { message -> message.toRemote() },
        temperature = temperature,
        maxTokens = maxTokens,
        tools = tools.map { tool -> tool.toRemote() },
        toolChoice = toolChoice
    )

    private fun AgentChatMessage.toRemote(): Message = Message(
        role = role,
        content = content,
        toolCallId = toolCallId,
        toolCalls = toolCalls?.map { call ->
            MessageToolCall(
                id = call.id,
                function = MessageFunctionCall(call.function.name, call.function.arguments)
            )
        },
        name = name
    )

    private fun AgentToolDefinition.toRemote(): LlmToolDefinition = LlmToolDefinition(
        type = type,
        function = LlmFunctionDefinition(
            name = function.name,
            description = function.description,
            parameters = function.parameters
        )
    )
}

@Singleton
class AgentPromptSourceImpl @Inject constructor(
    private val promptRegistry: PromptRegistry
) : AgentPromptSource {
    override fun get(key: String): AgentPromptTemplate = promptRegistry.get(key).let { config ->
        AgentPromptTemplate(config.system, config.temperature, config.maxTokens)
    }
}
