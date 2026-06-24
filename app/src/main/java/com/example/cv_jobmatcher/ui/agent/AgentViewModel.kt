package com.example.cv_jobmatcher.ui.agent

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cv_jobmatcher.data.repository.AgentContextRepository
import com.example.cv_jobmatcher.data.repository.ResumeVersionRepository
import com.example.cv_jobmatcher.domain.model.AgentChatUiState
import com.example.cv_jobmatcher.domain.model.AgentMessage
import com.example.cv_jobmatcher.domain.model.AgentMessageRole
import com.example.cv_jobmatcher.domain.model.AgentOutput
import com.example.cv_jobmatcher.domain.model.ContextBarState
import com.example.cv_jobmatcher.domain.usecase.AgentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AgentViewModel @Inject constructor(
    private val agentUseCase: AgentUseCase,
    private val agentContextRepository: AgentContextRepository,
    private val resumeVersionRepository: ResumeVersionRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    companion object {
        private const val TAG = "AgentViewModel"
    }

    private val _uiState = MutableStateFlow(AgentChatUiState())
    val uiState: StateFlow<AgentChatUiState> = _uiState.asStateFlow()

    private var streamJob: Job? = null

    private val activeResume = resumeVersionRepository.getActiveFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        loadContextBar()
        loadWelcomeMessage()
    }

    private fun loadContextBar() {
        viewModelScope.launch {
            val agentContext = agentContextRepository.getAgentContext()

            val jdTitle = agentContext.currentJdText?.let { text ->
                val firstLine = text.lines().firstOrNull()?.trim()?.take(50) ?: ""
                if (agentContext.currentJdCompany != null) {
                    "${agentContext.currentJdCompany} · $firstLine"
                } else {
                    firstLine
                }
            }

            val resume = resumeVersionRepository.getActive()
            _uiState.update {
                it.copy(contextBar = ContextBarState(
                    jdTitle = jdTitle?.ifBlank { null },
                    jdCompany = agentContext.currentJdCompany?.ifBlank { null },
                    resumeVersionName = resume?.name,
                    resumeVersionId = resume?.id
                ))
            }
        }
    }

    private fun loadWelcomeMessage() {
        val hasJd = _uiState.value.contextBar.jdTitle != null
        val hasResume = _uiState.value.contextBar.resumeVersionName != null

        val welcome = when {
            hasJd && hasResume -> "你好！我看到你已经有一个目标岗位和简历了。有什么我可以帮你的？比如分析匹配度、优化简历、准备面试，都可以直接说。"
            hasJd -> "你好！我看到你已经有目标岗位了。你可以粘贴简历文本或上传简历文件，我来帮你分析和优化。"
            hasResume -> "你好！我看到你已经有一份简历了。你可以粘贴一个岗位描述（JD），我来帮你分析匹配度并优化简历。"
            else -> "你好！我是智简求职，你的 AI 求职伙伴。你可以粘贴岗位描述（JD）或简历，我来帮你分析匹配度、优化简历、准备面试、管理投递。直接说就行！"
        }

        _uiState.update {
            it.copy(messages = listOf(
                AgentMessage(
                    role = AgentMessageRole.AGENT,
                    content = welcome
                )
            ))
        }
    }

    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank() || _uiState.value.isLoading) return

        _uiState.update { it.copy(inputText = "", isLoading = true, error = null) }

        val userMessage = AgentMessage(
            role = AgentMessageRole.USER,
            content = text
        )
        _uiState.update {
            it.copy(messages = it.messages + userMessage)
        }

        streamJob = viewModelScope.launch {
            processAgentReply(text)
        }
    }

    private suspend fun processAgentReply(userText: String) {
        try {
            val outputFlow = agentUseCase.process(
                userText = userText,
                conversationHistory = _uiState.value.messages,
                appContext = appContext
            )

            var fullContent = ""

            outputFlow.collect { output ->
                when (output) {
                    is AgentOutput.StreamText -> {
                        fullContent += output.chunk
                        updateStreamingMessage(fullContent)
                    }
                    is AgentOutput.ToolStart -> {
                        Log.d(TAG, "工具开始: ${output.toolName}")
                        // 可以在 UI 显示 loading chip
                    }
                    is AgentOutput.ToolResult -> {
                        Log.d(TAG, "工具结果: ${output.card}")
                        // TODO: 渲染富卡片
                    }
                    is AgentOutput.ClarificationRequest -> {
                        // TODO: 显示澄清对话
                        updateStreamingMessage(output.question)
                    }
                    is AgentOutput.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isStreaming = false,
                                error = output.message
                            )
                        }
                    }
                    is AgentOutput.Done -> {
                        finalizeStreamingMessage(fullContent)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Agent 处理异常", e)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isStreaming = false,
                    error = e.localizedMessage ?: "AI 回复失败"
                )
            }
        }
    }

    private fun updateStreamingMessage(content: String) {
        _uiState.update { state ->
            val msgs = state.messages.toMutableList()
            val lastMsg = msgs.lastOrNull()
            if (lastMsg?.role == AgentMessageRole.AGENT && lastMsg.isStreaming) {
                msgs[msgs.lastIndex] = lastMsg.copy(content = content)
            } else {
                msgs.add(AgentMessage(
                    role = AgentMessageRole.AGENT,
                    content = content,
                    isStreaming = true
                ))
            }
            state.copy(messages = msgs, isStreaming = true)
        }
    }

    private fun finalizeStreamingMessage(content: String) {
        _uiState.update { state ->
            val msgs = state.messages.toMutableList()
            val lastMsg = msgs.lastOrNull()
            if (lastMsg?.role == AgentMessageRole.AGENT && lastMsg.isStreaming) {
                msgs[msgs.lastIndex] = lastMsg.copy(
                    content = content,
                    isStreaming = false
                )
            }
            state.copy(
                messages = msgs,
                isLoading = false,
                isStreaming = false
            )
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun cancelStream() {
        streamJob?.cancel()
        streamJob = null
        _uiState.update { state ->
            val msgs = state.messages.toMutableList()
            val lastMsg = msgs.lastOrNull()
            if (lastMsg?.role == AgentMessageRole.AGENT && lastMsg.isStreaming) {
                if (lastMsg.content.isBlank()) {
                    msgs.removeAt(msgs.lastIndex)
                } else {
                    msgs[msgs.lastIndex] = lastMsg.copy(isStreaming = false)
                }
            }
            state.copy(messages = msgs, isLoading = false, isStreaming = false)
        }
    }

    override fun onCleared() {
        super.onCleared()
        streamJob?.cancel()
    }
}
