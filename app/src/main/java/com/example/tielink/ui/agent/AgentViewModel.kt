package com.example.tielink.ui.agent

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tielink.data.repository.AgentContextRepository
import com.example.tielink.data.repository.ResumeVersionRepository
import com.example.tielink.domain.model.AgentChatUiState
import com.example.tielink.domain.model.AgentMessage
import com.example.tielink.domain.model.AgentMessageRole
import com.example.tielink.domain.model.AgentOutput
import com.example.tielink.domain.model.ContextBarState
import com.example.tielink.domain.usecase.AgentUseCase
import com.example.tielink.util.FileParser
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        private const val STREAMING_THROTTLE_MS = 120L
    }

    private val _uiState = MutableStateFlow(AgentChatUiState())
    val uiState: StateFlow<AgentChatUiState> = _uiState.asStateFlow()

    private var streamJob: Job? = null

    private val activeResume = resumeVersionRepository.getActiveFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Streaming throttle state — accumulates deltas between UI pushes
    private var streamingContentBuf = StringBuilder()
    private var thinkingBuf = StringBuilder()
    private var lastStreamUiUpdate = 0L

    init {
        loadContextBar()
        loadWelcomeMessage()
    }

    private fun loadContextBar() {
        viewModelScope.launch {
            val agentContext = agentContextRepository.getAgentContext()
            val jdTitle = agentContext.currentJdText?.let { text ->
                val firstLine = text.lines().firstOrNull()?.trim()?.take(50) ?: ""
                if (agentContext.currentJdCompany != null) "${agentContext.currentJdCompany} · $firstLine"
                else firstLine
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
            hasJd && hasResume -> "你好！我看到你已有目标岗位和简历，有什么可以帮你的？比如分析匹配度、优化简历、准备面试，直接说就行。"
            hasJd -> "你好！我看到你已有目标岗位。你可以粘贴简历文本或上传简历文件，我来帮你分析和优化。"
            hasResume -> "你好！我看到你已有一份简历。你可以粘贴岗位描述（JD），我来帮你分析匹配度并优化简历。"
            else -> "你好！我是智简求职，你的 AI 求职伙伴。可以粘贴 JD 或简历，我来帮你分析匹配度、优化简历、准备面试、管理投递。直接说就行！"
        }
        _uiState.update { it.copy(messages = listOf(AgentMessage(role = AgentMessageRole.AGENT, content = welcome))) }
    }

    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val state = _uiState.value
        val inputText = state.inputText.trim()
        val hasAttachment = state.pendingAttachmentText != null
        if ((inputText.isBlank() && !hasAttachment) || state.isLoading) return

        val finalText = buildString {
            if (hasAttachment) {
                appendLine("【已上传文件：${state.pendingAttachmentName ?: "文件"}】")
                appendLine(state.pendingAttachmentText!!.take(4000))
                if (inputText.isNotBlank()) { appendLine(); append(inputText) }
            } else {
                append(inputText)
            }
        }.trim()

        _uiState.update {
            it.copy(
                inputText = "",
                isLoading = true,
                error = null,
                pendingAttachmentName = null,
                pendingAttachmentText = null,
                thinkingBuffer = ""
            )
        }
        val userMessage = AgentMessage(role = AgentMessageRole.USER, content = finalText)
        _uiState.update { it.copy(messages = it.messages + userMessage) }

        // Reset streaming buffers
        streamingContentBuf.clear()
        thinkingBuf.clear()
        lastStreamUiUpdate = 0L

        streamJob = viewModelScope.launch { processAgentReply(finalText) }
    }

    private suspend fun processAgentReply(userText: String) {
        try {
            agentUseCase.process(
                userText = userText,
                conversationHistory = _uiState.value.messages,
                appContext = appContext
            ).collect { output ->
                when (output) {
                    is AgentOutput.Thinking -> {
                        thinkingBuf.append(output.chunk)
                        throttledStreamUpdate()
                    }
                    is AgentOutput.StreamText -> {
                        streamingContentBuf.append(output.chunk)
                        throttledStreamUpdate()
                    }
                    is AgentOutput.ToolStart -> {
                        // Insert a transient tool-loading bubble
                        val loadingMsg = AgentMessage(
                            role = AgentMessageRole.AGENT,
                            content = output.description,
                            toolLoadingName = output.toolName
                        )
                        _uiState.update { it.copy(messages = it.messages + loadingMsg) }
                    }
                    is AgentOutput.ToolResult -> {
                        // Replace the last tool-loading bubble (if any) with the card
                        val cardMsg = AgentMessage(
                            role = AgentMessageRole.AGENT,
                            content = "",
                            card = output.card
                        )
                        _uiState.update { state ->
                            val msgs = state.messages.toMutableList()
                            val lastIdx = msgs.indexOfLast { it.toolLoadingName != null }
                            if (lastIdx >= 0) msgs[lastIdx] = cardMsg else msgs.add(cardMsg)
                            state.copy(messages = msgs)
                        }
                    }
                    is AgentOutput.ClarificationRequest -> {
                        val clarMsg = AgentMessage(role = AgentMessageRole.AGENT, content = output.question)
                        _uiState.update { it.copy(messages = it.messages + clarMsg, isLoading = false, isStreaming = false) }
                    }
                    is AgentOutput.Error -> {
                        _uiState.update { it.copy(isLoading = false, isStreaming = false, error = output.message) }
                    }
                    is AgentOutput.Done -> {
                        forceStreamUpdate() // flush any pending buffered text
                        finalizeStreamingMessage()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Agent 处理异常", e)
            _uiState.update { it.copy(isLoading = false, isStreaming = false, error = e.localizedMessage ?: "AI 回复失败") }
        }
    }

    /** Push buffered streaming content to UI if 120ms has elapsed. */
    private fun throttledStreamUpdate() {
        val now = System.currentTimeMillis()
        if (now - lastStreamUiUpdate >= STREAMING_THROTTLE_MS) {
            lastStreamUiUpdate = now
            forceStreamUpdate()
        }
    }

    /** Always push buffered content to UI regardless of throttle. */
    private fun forceStreamUpdate() {
        val content = streamingContentBuf.toString()
        val thinking = thinkingBuf.toString().ifBlank { null }
        if (content.isBlank() && thinking == null) return

        _uiState.update { state ->
            val msgs = state.messages.toMutableList()
            val lastMsg = msgs.lastOrNull()
            if (lastMsg?.role == AgentMessageRole.AGENT && lastMsg.isStreaming) {
                msgs[msgs.lastIndex] = lastMsg.copy(content = content, thinkingContent = thinking)
            } else {
                msgs.add(AgentMessage(
                    role = AgentMessageRole.AGENT,
                    content = content,
                    thinkingContent = thinking,
                    isStreaming = true
                ))
            }
            state.copy(messages = msgs, isStreaming = true, thinkingBuffer = thinking ?: "")
        }
    }

    private fun finalizeStreamingMessage() {
        _uiState.update { state ->
            val msgs = state.messages.toMutableList()
            val lastMsg = msgs.lastOrNull()
            if (lastMsg?.role == AgentMessageRole.AGENT && lastMsg.isStreaming) {
                msgs[msgs.lastIndex] = lastMsg.copy(isStreaming = false)
            }
            state.copy(messages = msgs, isLoading = false, isStreaming = false, thinkingBuffer = "")
        }
    }

    // ── File attachment ────────────────────────────────────────────────────────

    fun attachFile(context: Context, uri: Uri, mimeType: String?, fileName: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isParsingFile = true, error = null) }
            val result = withContext(Dispatchers.IO) { FileParser.extractText(context, uri, mimeType) }
            result.fold(
                onSuccess = { text ->
                    _uiState.update { it.copy(isParsingFile = false, pendingAttachmentName = fileName ?: "文件", pendingAttachmentText = text) }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isParsingFile = false, error = "文件解析失败: ${e.message}") }
                }
            )
        }
    }

    fun clearAttachment() {
        _uiState.update { it.copy(pendingAttachmentName = null, pendingAttachmentText = null) }
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
                if (lastMsg.content.isBlank()) msgs.removeAt(msgs.lastIndex)
                else msgs[msgs.lastIndex] = lastMsg.copy(isStreaming = false)
            }
            state.copy(messages = msgs, isLoading = false, isStreaming = false)
        }
    }

    override fun onCleared() {
        super.onCleared()
        streamJob?.cancel()
    }
}
