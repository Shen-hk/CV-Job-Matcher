package com.example.tielink.ui.agent

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tielink.data.repository.AgentChatDraftRepository
import com.example.tielink.data.repository.AgentContextRepository
import com.example.tielink.data.repository.ResumeVersionRepository
import com.example.tielink.data.local.AppPreferences
import com.example.tielink.domain.model.AgentChatUiState
import com.example.tielink.domain.model.AgentMessage
import com.example.tielink.domain.model.AgentMessageRole
import com.example.tielink.domain.model.AgentOutput
import com.example.tielink.domain.model.ContextBarState
import com.example.tielink.domain.model.AgentProcessStage
import com.example.tielink.domain.model.AgentProcessState
import com.example.tielink.domain.model.PersistedAgentChatDraft
import com.example.tielink.domain.model.PersistedAgentMessage
import com.example.tielink.domain.model.ResumeVersion
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AgentViewModel @Inject constructor(
    private val agentUseCase: AgentUseCase,
    private val agentChatDraftRepository: AgentChatDraftRepository,
    private val agentContextRepository: AgentContextRepository,
    private val resumeVersionRepository: ResumeVersionRepository,
    private val appPreferences: AppPreferences,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    companion object {
        private const val TAG = "AgentViewModel"
        private const val STREAMING_THROTTLE_MS = 120L
    }

    private val _uiState = MutableStateFlow(AgentChatUiState())
    val uiState: StateFlow<AgentChatUiState> = _uiState.asStateFlow()

    // 通知 UI 触发文件选择器；String = toolName（非空时来自上传卡片，空串时来自输入框附件按钮）
    private val _openFilePicker = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val openFilePicker: SharedFlow<String> = _openFilePicker.asSharedFlow()

    private var streamJob: Job? = null
    private var persistDraftJob: Job? = null

    private val activeResume = resumeVersionRepository.getActiveFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Streaming throttle state — accumulates deltas between UI pushes
    private var streamingContentBuf = StringBuilder()
    private var thinkingBuf = StringBuilder()
    private var lastStreamUiUpdate = 0L

    private fun setProcessState(
        stage: AgentProcessStage,
        title: String,
        detail: String = "",
        sourceLabel: String? = null,
        sourceBreakdown: List<String> = emptyList(),
        canCancel: Boolean = false
    ) {
        _uiState.update {
            it.copy(
                processState = AgentProcessState(
                    stage = stage,
                    title = title,
                    detail = detail,
                    sourceLabel = sourceLabel,
                    sourceBreakdown = sourceBreakdown,
                    canCancel = canCancel
                )
            )
        }
    }

    private fun clearProcessState() {
        _uiState.update {
            it.copy(processState = AgentProcessState())
        }
    }

    private fun processStateForTool(toolName: String, description: String): AgentProcessState {
        val lowered = "${toolName.lowercase()} ${description.lowercase()}"
        val stage = when {
            lowered.contains("draw") || lowered.contains("image") || lowered.contains("图") || lowered.contains("绘") ->
                AgentProcessStage.DRAWING
            lowered.contains("search") || lowered.contains("fetch") || lowered.contains("query") ||
                lowered.contains("检索") || lowered.contains("读取") || lowered.contains("分析") ->
                AgentProcessStage.RETRIEVING
            toolName == "platform_tool" ->
                AgentProcessStage.TEXT_GENERATION
            else -> AgentProcessStage.RETRIEVING
        }

        val title = when (stage) {
            AgentProcessStage.DRAWING -> "绘图中"
            AgentProcessStage.RETRIEVING -> "检索中"
            AgentProcessStage.TEXT_GENERATION -> "文本生成中"
            AgentProcessStage.THINKING -> "思考中"
            AgentProcessStage.INTERRUPTED -> "已中断"
            AgentProcessStage.IDLE -> "待机"
        }

        val sourceLabel = when (toolName) {
            "match_tool" -> "简历库 · 当前JD"
            "resume_tool" -> "当前简历"
            "interview_tool" -> "模拟面试会话"
            "tracking_tool" -> "投递记录"
            "platform_tool" -> "JD + 简历"
            "jd_tool" -> "JD 文本"
            else -> null
        }

        val sourceBreakdown = when (toolName) {
            "match_tool" -> listOf("简历库", "JD", "匹配分析")
            "resume_tool" -> listOf("当前简历", "优化建议")
            "interview_tool" -> listOf("会话记录", "最近问答")
            "tracking_tool" -> listOf("投递记录", "最新状态")
            "platform_tool" -> listOf("JD", "简历", "话术生成")
            "jd_tool" -> listOf("JD 文本", "结构化提取")
            else -> emptyList()
        }

        return AgentProcessState(
            stage = stage,
            title = title,
            detail = description,
            sourceLabel = sourceLabel,
            sourceBreakdown = sourceBreakdown,
            canCancel = true
        )
    }

    init {
        viewModelScope.launch {
            restorePersistedDraft()
            loadContextBar()
            if (_uiState.value.messages.isEmpty()) {
                loadWelcomeMessage()
            }
        }
        viewModelScope.launch {
            activeResume.collect { resume ->
                _uiState.update { state ->
                    state.copy(
                        contextBar = state.contextBar.copy(
                            resumeVersionName = resume?.name,
                            resumeVersionId = resume?.id
                        )
                    )
                }
            }
        }
        viewModelScope.launch {
            appPreferences.getResumeOptimizeContinueFlow().collect { shouldContinue ->
                if (!shouldContinue) return@collect
                if (!_uiState.value.isLoading) {
                    sendPrompt("帮我优化简历")
                } else {
                    updateInputText("帮我优化简历")
                }
            }
        }
    }

    private suspend fun restorePersistedDraft() {
        val draft = agentChatDraftRepository.load()
        if (draft.messages.isEmpty() && draft.inputText.isBlank() && draft.pendingAttachmentText.isNullOrBlank()) {
            return
        }
        _uiState.update {
            it.copy(
                messages = draft.messages.map { persisted ->
                    AgentMessage(
                        role = persisted.role,
                        content = persisted.content,
                        timestamp = persisted.timestamp,
                        thinkingContent = persisted.thinkingContent,
                        isStreaming = false
                    )
                },
                inputText = draft.inputText,
                pendingAttachmentName = draft.pendingAttachmentName,
                pendingAttachmentText = draft.pendingAttachmentText,
                suggestedPrompts = emptyList()
            )
        }
    }

    private suspend fun loadContextBar() {
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
        schedulePersistDraft()
        sendMessage()
    }

    fun requestFilePicker(toolName: String = "") {
        _openFilePicker.tryEmit(toolName)
    }

    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
        schedulePersistDraft()
    }

    fun startNewSession(prefillPrompt: String = "") {
        streamJob?.cancel()
        streamJob = null
        streamingContentBuf.clear()
        thinkingBuf.clear()
        lastStreamUiUpdate = 0L
        clearProcessState()
        _uiState.update {
            it.copy(
                messages = emptyList(),
                inputText = prefillPrompt,
                isLoading = false,
                isStreaming = false,
                error = null,
                pendingAttachmentName = null,
                pendingAttachmentText = null,
                isParsingFile = false,
                thinkingBuffer = ""
            )
        }
        schedulePersistDraft(immediate = true)
        loadWelcomeMessage()
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
        setProcessState(
            stage = AgentProcessStage.THINKING,
            title = "思考中",
            detail = "正在理解你的问题",
            sourceLabel = null,
            sourceBreakdown = emptyList(),
            canCancel = true
        )
        val userMessage = AgentMessage(role = AgentMessageRole.USER, content = finalText)
        _uiState.update { it.copy(messages = it.messages + userMessage) }
        schedulePersistDraft()

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
                    is AgentOutput.ProcessPhase -> {
                        setProcessState(
                            stage = output.stage,
                            title = output.title,
                            detail = output.detail,
                            sourceLabel = output.sourceLabel,
                            sourceBreakdown = output.sourceBreakdown,
                            canCancel = output.canCancel
                        )
                    }
                    is AgentOutput.Thinking -> {
                        thinkingBuf.append(output.chunk)
                        setProcessState(
                            stage = AgentProcessStage.THINKING,
                            title = "思考中",
                            detail = "正在推理回复内容",
                            sourceLabel = null,
                            sourceBreakdown = emptyList(),
                            canCancel = true
                        )
                        throttledStreamUpdate()
                    }
                    is AgentOutput.StreamText -> {
                        streamingContentBuf.append(output.chunk)
                        setProcessState(
                            stage = AgentProcessStage.TEXT_GENERATION,
                            title = "文本生成中",
                            detail = "正在流式输出答案",
                            sourceLabel = null,
                            sourceBreakdown = emptyList(),
                            canCancel = true
                        )
                        throttledStreamUpdate()
                    }
                    is AgentOutput.ToolStart -> {
                        _uiState.update {
                            it.copy(processState = processStateForTool(output.toolName, output.description))
                        }
                        // Insert a transient tool-loading bubble;
                        // if a "润色中" processing placeholder is still present, replace it
                        val loadingMsg = AgentMessage(
                            role = AgentMessageRole.AGENT,
                            content = output.description,
                            toolLoadingName = output.toolName
                        )
                        _uiState.update { state ->
                            val msgs = state.messages.toMutableList()
                            val processingIdx = msgs.indexOfLast {
                                it.role == AgentMessageRole.AGENT &&
                                        it.content == "收到文件啦，正在润色中 ✨" &&
                                        it.card == null
                            }
                            if (processingIdx >= 0) {
                                msgs[processingIdx] = loadingMsg
                            } else {
                                msgs.add(loadingMsg)
                            }
                            state.copy(messages = msgs)
                        }
                        schedulePersistDraft()
                    }
                    is AgentOutput.ToolCancelled -> {
                        // 工具因缺少前置条件被取消，移除 loading 气泡，交由 LLM 回复
                        _uiState.update { state ->
                            val msgs = state.messages.toMutableList()
                            msgs.removeAll { it.toolLoadingName != null }
                            state.copy(messages = msgs, processState = AgentProcessState())
                        }
                        schedulePersistDraft()
                    }
                    is AgentOutput.ToolResult -> {
                        // 先用占位回调建消息以拿到稳定 id，再回填真正引用该 id 的回调
                        val rawCard = output.card
                        val placeholderMsg = AgentMessage(
                            role = AgentMessageRole.AGENT,
                            content = "",
                            card = rawCard
                        )
                        val msgId = placeholderMsg.id
                        val card = when (rawCard) {
                            is com.example.tielink.domain.model.UiCard.ResumeDiffCard -> rawCard.copy(
                                onAccept = { acceptResumeDiff(msgId, rawCard.before, rawCard.after) },
                                onRollback = { rollbackResumeDiff(msgId) }
                            )
                            is com.example.tielink.domain.model.UiCard.UploadPromptCard -> rawCard.copy(
                                onUpload = { _openFilePicker.tryEmit(rawCard.toolName) }
                            )
                            else -> rawCard
                        }
                        val cardMsg = placeholderMsg.copy(card = card)
                        _uiState.update { state ->
                            val msgs = state.messages.toMutableList()
                            val lastIdx = msgs.indexOfLast { it.toolLoadingName != null }
                            if (lastIdx >= 0) msgs[lastIdx] = cardMsg else msgs.add(cardMsg)
                            state.copy(messages = msgs)
                        }
                        schedulePersistDraft()
                    }
                    is AgentOutput.ClarificationRequest -> {
                        val clarMsg = AgentMessage(role = AgentMessageRole.AGENT, content = output.question)
                        _uiState.update {
                            it.copy(
                                messages = it.messages + clarMsg,
                                isLoading = false,
                                isStreaming = false,
                                processState = AgentProcessState()
                            )
                        }
                        schedulePersistDraft()
                    }
                    is AgentOutput.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isStreaming = false,
                                error = output.message,
                                processState = AgentProcessState()
                            )
                        }
                        schedulePersistDraft()
                    }
                    is AgentOutput.Done -> {
                        forceStreamUpdate() // flush any pending buffered text
                        finalizeStreamingMessage()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Agent 处理异常", e)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isStreaming = false,
                    error = e.localizedMessage ?: "AI 回复失败",
                    processState = AgentProcessState()
                )
            }
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
        schedulePersistDraft()
    }

    private fun finalizeStreamingMessage() {
        _uiState.update { state ->
            val msgs = state.messages.toMutableList()
            val lastMsg = msgs.lastOrNull()
            if (lastMsg?.role == AgentMessageRole.AGENT && lastMsg.isStreaming) {
                msgs[msgs.lastIndex] = lastMsg.copy(isStreaming = false)
            }
            state.copy(
                messages = msgs,
                isLoading = false,
                isStreaming = false,
                thinkingBuffer = "",
                processState = AgentProcessState()
            )
        }
        schedulePersistDraft(immediate = true)
    }

    // ── File attachment ────────────────────────────────────────────────────────

    fun attachFile(context: Context, uri: Uri, mimeType: String?, fileName: String?, fromCardToolName: String = "") {
        Log.d(TAG, "attachFile: uri=$uri, mime=$mimeType, name=$fileName, fromCard=$fromCardToolName")
        viewModelScope.launch {
            _uiState.update { it.copy(isParsingFile = true, error = null) }
            val result = withContext(Dispatchers.IO) { FileParser.extractText(context, uri, mimeType) }
            result.fold(
                onSuccess = { text ->
                    Log.d(TAG, "文件解析成功: ${text.length} 字符, fromCardToolName=$fromCardToolName")
                    _uiState.update { it.copy(isParsingFile = false) }
                    if (fromCardToolName.isNotEmpty()) {
                        saveResumeAndAutoTrigger(text, fileName ?: "我的简历", fromCardToolName)
                    } else {
                        _uiState.update { it.copy(pendingAttachmentName = fileName ?: "文件", pendingAttachmentText = text) }
                        schedulePersistDraft(immediate = true)
                    }
                },
                onFailure = { e ->
                    Log.e(TAG, "文件解析失败: ${e.message}", e)
                    _uiState.update { it.copy(isParsingFile = false, error = "文件解析失败: ${e.message}") }
                    schedulePersistDraft()
                }
            )
        }
    }

    private suspend fun saveResumeAndAutoTrigger(text: String, fileName: String, toolName: String) {
        Log.d(TAG, "saveResumeAndAutoTrigger: fileName=$fileName, toolName=$toolName, textLen=${text.length}")
        val cleanName = fileName.substringBeforeLast(".").ifBlank { "我的简历" }
        try {
            resumeVersionRepository.insertAndActivate(
                ResumeVersion(name = cleanName, rawText = text)
            )
            _uiState.update { state ->
                state.copy(contextBar = state.contextBar.copy(resumeVersionName = cleanName))
            }
        } catch (e: Exception) {
            Log.e(TAG, "保存简历版本失败", e)
            _uiState.update { it.copy(error = "保存简历失败: ${e.message}") }
            return
        }

        // ── 阶段1：替换上传卡片为"收到文件"提示 ──────────────────────────
        _uiState.update { state ->
            val msgs = state.messages.toMutableList()
            val uploadCardIdx = msgs.indexOfLast {
                it.card is com.example.tielink.domain.model.UiCard.UploadPromptCard
            }
            if (uploadCardIdx >= 0) {
                msgs[uploadCardIdx] = AgentMessage(
                    role = AgentMessageRole.AGENT,
                    content = "收到文件啦，正在润色中 ✨"
                )
            }
            state.copy(messages = msgs)
        }
        schedulePersistDraft()

        // ── 阶段2：自动触发工具（工具完成后会 emit 预览卡）────────────────
        val autoPrompt = when (toolName) {
            "match_tool" -> "分析我的简历匹配度"
            "resume_tool" -> "帮我优化简历"
            else -> "分析我的简历"
        }

        if (toolName == "resume_tool") {
            appPreferences.setResumeOptimizeContinue(true)
        }

        val ackMsg = AgentMessage(role = AgentMessageRole.USER, content = "📎 已上传简历：$cleanName")
        _uiState.update { state ->
            state.copy(
                messages = state.messages + ackMsg,
                error = null,
                thinkingBuffer = "",
                suggestedPrompts = emptyList(),
                isLoading = toolName != "resume_tool"
            )
        }
        setProcessState(
            stage = if (toolName == "platform_tool") AgentProcessStage.TEXT_GENERATION else AgentProcessStage.RETRIEVING,
            title = if (toolName == "platform_tool") "文本生成中" else "检索中",
            detail = "正在处理上传的简历",
            sourceLabel = when (toolName) {
                "match_tool" -> "简历库 · 当前JD"
                "resume_tool" -> "当前简历"
                "platform_tool" -> "JD + 简历"
                else -> null
            },
            sourceBreakdown = when (toolName) {
                "match_tool" -> listOf("简历库", "JD", "匹配分析")
                "resume_tool" -> listOf("当前简历", "优化建议")
                "platform_tool" -> listOf("JD", "简历", "话术生成")
                else -> emptyList()
            },
            canCancel = toolName != "resume_tool"
        )
        schedulePersistDraft()
        streamingContentBuf.clear()
        thinkingBuf.clear()
        lastStreamUiUpdate = 0L
        if (toolName == "resume_tool") return
        streamJob = viewModelScope.launch { processAgentReply(autoPrompt) }
    }

    fun clearAttachment() {
        _uiState.update { it.copy(pendingAttachmentName = null, pendingAttachmentText = null) }
        schedulePersistDraft(immediate = true)
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
            state.copy(
                messages = msgs,
                isLoading = false,
                isStreaming = false,
                processState = AgentProcessState(
                    stage = AgentProcessStage.INTERRUPTED,
                    title = "已中断",
                    detail = "已停止当前生成，输入新内容即可继续",
                    sourceLabel = null,
                    sourceBreakdown = emptyList(),
                    canCancel = false
                )
            )
        }
        schedulePersistDraft(immediate = true)
    }

    // ── Resume diff callbacks ──────────────────────────────────────────────────

    /** 用新 status 替换指定消息里的 ResumeDiffCard，触发卡片重组反馈 */
    private fun updateDiffStatus(msgId: Long, status: com.example.tielink.domain.model.DiffStatus) {
        _uiState.update { state ->
            val msgs = state.messages.toMutableList()
            val idx = msgs.indexOfFirst { it.id == msgId }
            if (idx >= 0) {
                val card = msgs[idx].card as? com.example.tielink.domain.model.UiCard.ResumeDiffCard
                if (card != null) {
                    msgs[idx] = msgs[idx].copy(card = card.copy(status = status))
                }
            }
            state.copy(messages = msgs)
        }
    }

    private fun acceptResumeDiff(msgId: Long, before: String, newText: String) {
        viewModelScope.launch {
            val active = resumeVersionRepository.getActive()
            if (active == null) {
                updateDiffStatus(msgId, com.example.tielink.domain.model.DiffStatus.FAILED)
                return@launch
            }
            // 优先在 rawText 中定位，其次 cleanedText
            val targetField = when {
                active.rawText.contains(before) -> "raw"
                active.cleanedText.contains(before) -> "cleaned"
                else -> null
            }
            if (targetField == null) {
                // 原文未在简历中定位到，无法安全替换 — 给出失败反馈
                updateDiffStatus(msgId, com.example.tielink.domain.model.DiffStatus.FAILED)
                return@launch
            }
            try {
                val updated = when (targetField) {
                    "raw" -> active.copy(rawText = active.rawText.replace(before, newText), updatedAt = System.currentTimeMillis())
                    else -> active.copy(cleanedText = active.cleanedText.replace(before, newText), updatedAt = System.currentTimeMillis())
                }
                resumeVersionRepository.save(updated)
                updateDiffStatus(msgId, com.example.tielink.domain.model.DiffStatus.ACCEPTED)
            } catch (e: Exception) {
                Log.e(TAG, "采用简历优化失败", e)
                updateDiffStatus(msgId, com.example.tielink.domain.model.DiffStatus.FAILED)
            }
        }
    }

    private fun rollbackResumeDiff(msgId: Long) {
        updateDiffStatus(msgId, com.example.tielink.domain.model.DiffStatus.ROLLED_BACK)
    }

    override fun onCleared() {
        super.onCleared()
        streamJob?.cancel()
        persistDraftJob?.cancel()
        viewModelScope.launch {
            persistDraftNow()
        }
    }

    private fun schedulePersistDraft(immediate: Boolean = false) {
        persistDraftJob?.cancel()
        persistDraftJob = viewModelScope.launch {
            if (!immediate) {
                delay(400)
            }
            persistDraftNow()
        }
    }

    private suspend fun persistDraftNow() {
        val state = _uiState.value
        val persistedMessages = state.messages
            .filter { it.toolLoadingName == null && it.card == null }
            .filter { it.content.isNotBlank() || !it.thinkingContent.isNullOrBlank() }
            .map {
                PersistedAgentMessage(
                    role = it.role,
                    content = it.content,
                    timestamp = it.timestamp,
                    thinkingContent = it.thinkingContent
                )
            }

        val draft = PersistedAgentChatDraft(
            messages = persistedMessages,
            inputText = state.inputText,
            pendingAttachmentName = state.pendingAttachmentName,
            pendingAttachmentText = state.pendingAttachmentText
        )

        if (draft.messages.isEmpty() && draft.inputText.isBlank() && draft.pendingAttachmentText.isNullOrBlank()) {
            agentChatDraftRepository.clear()
        } else {
            agentChatDraftRepository.save(draft)
        }
    }
}
