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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    // 通知 UI 触发文件选择器
    private val _openFilePicker = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openFilePicker: SharedFlow<Unit> = _openFilePicker.asSharedFlow()

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

        val prompts = when {
            hasJd && hasResume -> listOf("分析我的匹配度", "针对这个 JD 优化简历", "准备面试问题", "更新投递进度")
            hasJd              -> listOf("帮我写一份匹配的简历", "分析岗位核心要求", "准备面试问题", "推荐相关岗位")
            hasResume          -> listOf("分析我的简历优势", "帮我找匹配岗位", "我该怎么优化简历", "模拟一次面试")
            else               -> listOf("我想优化简历", "我有一个 JD 想分析", "帮我准备面试", "怎么追踪投递进度")
        }

        _uiState.update { it.copy(suggestedPrompts = prompts) }
    }

    fun sendPrompt(prompt: String) {
        _uiState.update { it.copy(inputText = prompt) }
        sendMessage()
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
                thinkingBuffer = "",
                suggestedPrompts = emptyList()
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
                    is AgentOutput.ToolCancelled -> {
                        // 工具因缺少前置条件被取消，移除 loading 气泡，交由 LLM 回复
                        _uiState.update { state ->
                            val msgs = state.messages.toMutableList()
                            msgs.removeAll { it.toolLoadingName != null }
                            state.copy(messages = msgs)
                        }
                    }
                    is AgentOutput.ToolResult -> {
                        // Attach real callbacks; replace loading bubble with card
                        val card = when (val raw = output.card) {
                            is com.example.tielink.domain.model.UiCard.ResumeDiffCard -> raw.copy(
                                onAccept = { acceptResumeDiff(raw.after) },
                                onRollback = { /* nothing — caller keeps original */ }
                            )
                            is com.example.tielink.domain.model.UiCard.UploadPromptCard -> raw.copy(
                                onUpload = { _openFilePicker.tryEmit(Unit) }
                            )
                            else -> raw
                        }
                        val cardMsg = AgentMessage(
                            role = AgentMessageRole.AGENT,
                            content = "",
                            card = card
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

    // ── Resume diff callbacks ──────────────────────────────────────────────────

    private fun acceptResumeDiff(newText: String) {
        viewModelScope.launch {
            val active = resumeVersionRepository.getActive() ?: return@launch
            resumeVersionRepository.save(
                active.copy(
                    rawText = active.rawText.replace(
                        // Find the original sentence in the resume and swap it
                        // Use a best-effort substring replacement; if not found, append
                        findOriginalInResume(active.rawText, newText) ?: return@launch,
                        newText
                    ),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private fun findOriginalInResume(resumeText: String, newText: String): String? {
        // Locate the corresponding original diff bubble to get the before-text
        val diffCard = _uiState.value.messages
            .mapNotNull { it.card as? com.example.tielink.domain.model.UiCard.ResumeDiffCard }
            .firstOrNull { it.after == newText }
        return diffCard?.before?.takeIf { resumeText.contains(it) }
    }

    override fun onCleared() {
        super.onCleared()
        streamJob?.cancel()
    }
}
