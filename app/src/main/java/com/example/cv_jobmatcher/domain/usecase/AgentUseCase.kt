package com.example.cv_jobmatcher.domain.usecase

import android.content.Context
import android.util.Log
import com.example.cv_jobmatcher.data.remote.AiProviderManager
import com.example.cv_jobmatcher.data.remote.LlmRequest
import com.example.cv_jobmatcher.data.remote.PromptRegistry
import com.example.cv_jobmatcher.data.remote.dto.Message
import com.example.cv_jobmatcher.data.repository.AgentContextRepository
import com.example.cv_jobmatcher.data.repository.ResumeVersionRepository
import com.example.cv_jobmatcher.domain.model.AgentContext
import com.example.cv_jobmatcher.domain.model.AgentIntent
import com.example.cv_jobmatcher.domain.model.AgentOutput
import com.example.cv_jobmatcher.domain.nlp.IntentClassifier
import com.example.cv_jobmatcher.util.AgentWorkspace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Agent 核心用例
 * 处理意图识别、工具调用、LLM 对话
 */
@Singleton
class AgentUseCase @Inject constructor(
    private val aiProviderManager: AiProviderManager,
    private val promptRegistry: PromptRegistry,
    private val agentContextRepository: AgentContextRepository,
    private val resumeVersionRepository: ResumeVersionRepository
) {
    companion object {
        private const val TAG = "AgentUseCase"
        private const val MAX_CONTEXT_MESSAGES = 20
    }

    /**
     * 处理用户输入，返回 AgentOutput 流
     */
    fun process(
        userText: String,
        conversationHistory: List<com.example.cv_jobmatcher.domain.model.AgentMessage>,
        appContext: Context
    ): Flow<AgentOutput> = flow {
        // 1. 意图识别
        val intent = IntentClassifier.classify(userText)
        Log.d(TAG, "识别意图: ${intent.type}, 工具调用: ${intent.toolCall}")

        // 2. 如果需要澄清，发出澄清请求
        if (intent.clarificationNeeded) {
            emit(AgentOutput.ClarificationRequest(
                question = intent.clarificationPrompt ?: "请确认你的意图",
                options = listOf("是", "否", "取消")
            ))
            return@flow
        }

        // 3. 如果有工具调用，先执行工具
        if (intent.toolCall != null) {
            emit(AgentOutput.ToolStart(
                toolName = intent.toolCall.toolName,
                description = "正在调用 ${intent.toolCall.function}..."
            ))

            // TODO: 实现工具调用逻辑
            // 这里暂时跳过，直接进入 LLM 对话
        }

        // 4. 构建系统提示词
        val systemPrompt = buildSystemPrompt(appContext)

        // 5. 构建 LLM 请求
        val messages = buildMessages(systemPrompt, conversationHistory, userText)
        val request = LlmRequest(
            messages = messages,
            temperature = 0.7,
            maxTokens = 4096
        )

        // 6. 流式调用 LLM
        try {
            val streamFlow = aiProviderManager.chatWithFallbackStream(request)
            val textBuilder = StringBuilder()

            streamFlow.collect { event ->
                when (event) {
                    is com.example.cv_jobmatcher.data.remote.StreamEvent.Start -> {
                        // 开始标记
                    }
                    is com.example.cv_jobmatcher.data.remote.StreamEvent.Content -> {
                        textBuilder.append(event.text)
                        emit(AgentOutput.StreamText(event.text))
                    }
                    is com.example.cv_jobmatcher.data.remote.StreamEvent.Done -> {
                        // 处理记忆提取
                        handleMemoryExtraction(userText, textBuilder.toString(), appContext)
                        emit(AgentOutput.Done)
                    }
                    is com.example.cv_jobmatcher.data.remote.StreamEvent.Error -> {
                        emit(AgentOutput.Error(event.message))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Agent 处理异常", e)
            emit(AgentOutput.Error(e.localizedMessage ?: "处理失败"))
        }
    }

    /**
     * 构建系统提示词
     */
    private suspend fun buildSystemPrompt(appContext: Context): String {
        val config = promptRegistry.get("agent_chat")
        val basePrompt = config.system

        val sb = StringBuilder(basePrompt)

        // 添加上下文信息
        val agentContext = agentContextRepository.getAgentContext()

        agentContext.currentJdText?.let { jdText ->
            sb.append("\n\n【当前岗位】\n${jdText.take(500)}")
        }

        agentContext.currentJdCompany?.let { company ->
            sb.append("\n公司: $company")
        }

        // 添加简历上下文
        val resume = resumeVersionRepository.getActive()
        resume?.let {
            sb.append("\n\n【当前简历】${it.name}")
            agentContextRepository.updateAgentContext(currentResumeVersionId = it.id)
        }

        // 添加记忆系统
        val resumeMemory = AgentWorkspace.buildResumeContext(appContext)
        if (resumeMemory.length > 50) {
            sb.append("\n\n【用户简历记忆】\n${resumeMemory.take(1000)}")
        }

        val jdMemory = AgentWorkspace.buildJdContext(appContext)
        if (jdMemory.length > 50) {
            sb.append("\n\n【用户岗位偏好】\n${jdMemory.take(500)}")
        }

        val interviewMemory = AgentWorkspace.buildInterviewContext(appContext)
        if (interviewMemory.length > 50) {
            sb.append("\n\n【用户面试记忆】\n${interviewMemory.take(500)}")
        }

        return sb.toString()
    }

    /**
     * 构建消息列表
     */
    private fun buildMessages(
        systemPrompt: String,
        conversationHistory: List<com.example.cv_jobmatcher.domain.model.AgentMessage>,
        userText: String
    ): List<Message> {
        val messages = mutableListOf(Message("system", systemPrompt))

        // 添加历史消息
        conversationHistory
            .filter { it.role != com.example.cv_jobmatcher.domain.model.AgentMessageRole.SYSTEM }
            .takeLast(MAX_CONTEXT_MESSAGES)
            .forEach { msg ->
                messages.add(Message(
                    role = when (msg.role) {
                        com.example.cv_jobmatcher.domain.model.AgentMessageRole.USER -> "user"
                        com.example.cv_jobmatcher.domain.model.AgentMessageRole.AGENT -> "assistant"
                        com.example.cv_jobmatcher.domain.model.AgentMessageRole.SYSTEM -> "system"
                    },
                    content = msg.content
                ))
            }

        // 添加当前用户消息
        messages.add(Message("user", userText))

        return messages
    }

    /**
     * 处理记忆提取
     */
    private fun handleMemoryExtraction(userText: String, agentReply: String, appContext: Context) {
        // 从用户输入提取记忆
        val userMemory = AgentWorkspace.extractExplicitMemory(userText)
        if (userMemory != null) {
            val lower = userText.lowercase()
            val saved = when {
                lower.contains("简历") || lower.contains("经历") || lower.contains("技能") -> {
                    AgentWorkspace.appendResumeMemory(appContext, userMemory)
                }
                lower.contains("岗位") || lower.contains("职位") || lower.contains("偏好") || lower.contains("行业") -> {
                    AgentWorkspace.appendJdMemory(appContext, userMemory)
                }
                lower.contains("面试") || lower.contains("薄弱") || lower.contains("擅长") -> {
                    AgentWorkspace.appendInterviewMemory(appContext, userMemory)
                }
                else -> AgentWorkspace.appendResumeMemory(appContext, userMemory)
            }
            if (saved) {
                Log.d(TAG, "已保存用户记忆: $userMemory")
            }
        }
    }
}
