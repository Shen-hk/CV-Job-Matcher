package com.example.tielink.ui.interview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tielink.data.local.AppPreferences
import com.example.tielink.data.local.db.entity.JdLibraryEntity
import com.example.tielink.data.repository.InterviewRepository
import com.example.tielink.data.repository.JdLibraryRepository
import com.example.tielink.data.repository.ResumeVersionRepository
import com.example.tielink.domain.model.InterviewMessage
import com.example.tielink.domain.model.InterviewPersona
import com.example.tielink.domain.model.InterviewSession
import com.example.tielink.domain.model.MessageRole
import com.example.tielink.domain.model.ResumeVersion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InterviewUiState(
    val selectedPersona: InterviewPersona = InterviewPersona.MILD_TECH,
    val resumes: List<ResumeVersion> = emptyList(),
    val jds: List<JdLibraryEntity> = emptyList(),
    val selectedResumeId: Long? = null,
    val selectedJdId: Long? = null,
    val companyName: String = "",
    val positionName: String = "",
    val jdSummary: String = "",
    val hasResumeContext: Boolean = false,
    val isInCall: Boolean = false,
    val isCameraEnabled: Boolean = true,
    val isMicEnabled: Boolean = true,
    val isSpeakerEnabled: Boolean = true,
    val isUserListening: Boolean = false,
    val pressureLevel: Float = 0.45f,
    val followUpDepth: Float = 0.55f,
    val fundamentalsWeight: Float = 0.35f,
    val projectWeight: Float = 0.75f,
    val algorithmWeight: Float = 0.25f,
    val systemDesignWeight: Float = 0.45f,
    val behavioralWeight: Float = 0.35f,
    val instantFeedbackEnabled: Boolean = false,
    val elapsedSeconds: Int = 0,
    val questionCount: Int = 0,
    val activeSessionId: Long? = null,
    val draftAnswer: String = "",
    val liveUserTranscript: String = "",
    val messages: List<InterviewMessage> = emptyList(),
    val statusText: String = "视频面试模式已就绪，开启后会直接进入通话态。",
    val isLoading: Boolean = true
)

@HiltViewModel
class InterviewViewModel @Inject constructor(
    private val interviewRepository: InterviewRepository,
    private val appPreferences: AppPreferences,
    private val resumeVersionRepository: ResumeVersionRepository,
    private val jdLibraryRepository: JdLibraryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(InterviewUiState())
    val uiState: StateFlow<InterviewUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var messagesJob: Job? = null

    init {
        viewModelScope.launch {
            hydrateScreen()
        }
        observeResumeOptions()
        observeJdOptions()
    }

    fun selectPersona(persona: InterviewPersona) {
        _uiState.update { it.copy(selectedPersona = persona) }
        viewModelScope.launch {
            appPreferences.setLastInterviewPersona(persona.name)
        }
    }

    fun selectResume(versionId: Long?) {
        val selected = _uiState.value.resumes.firstOrNull { it.id == versionId }
        _uiState.update {
            it.copy(
                selectedResumeId = selected?.id,
                hasResumeContext = selected != null || it.hasResumeContext,
                statusText = selected?.let { resume -> "已选择简历：${resume.name}" } ?: "已切回最近简历上下文。"
            )
        }
    }

    fun selectJd(jdId: Long?) {
        val selected = _uiState.value.jds.firstOrNull { it.id == jdId }
        _uiState.update {
            it.copy(
                selectedJdId = selected?.id,
                companyName = selected?.companyName ?: appPreferences.snapshot().cachedJdCompanyName,
                positionName = selected?.positionName.orEmpty(),
                jdSummary = selected?.summaryText().orEmpty().ifBlank { appPreferences.snapshot().cachedJdRawText.lineSequence().firstOrNull().orEmpty() },
                statusText = selected?.let { jd -> "已选择岗位：${jd.companyName.ifBlank { "目标公司" }} · ${jd.positionName.ifBlank { "目标岗位" }}" }
                    ?: "已切回最近岗位上下文。"
            )
        }
    }

    fun updatePressureLevel(value: Float) {
        _uiState.update {
            it.copy(
                pressureLevel = value.coerceIn(0f, 1f),
                statusText = "面试官压力强度已调整。"
            )
        }
    }

    fun updateFollowUpDepth(value: Float) {
        _uiState.update {
            it.copy(
                followUpDepth = value.coerceIn(0f, 1f),
                statusText = "追问深度已调整。"
            )
        }
    }

    fun updateFundamentalsWeight(value: Float) {
        _uiState.update {
            it.copy(
                fundamentalsWeight = value.coerceIn(0f, 1f),
                statusText = "八股基础占比已调整。"
            )
        }
    }

    fun updateProjectWeight(value: Float) {
        _uiState.update {
            it.copy(
                projectWeight = value.coerceIn(0f, 1f),
                statusText = "项目经历占比已调整。"
            )
        }
    }

    fun updateAlgorithmWeight(value: Float) {
        _uiState.update {
            it.copy(
                algorithmWeight = value.coerceIn(0f, 1f),
                statusText = "算法题占比已调整。"
            )
        }
    }

    fun updateSystemDesignWeight(value: Float) {
        _uiState.update {
            it.copy(
                systemDesignWeight = value.coerceIn(0f, 1f),
                statusText = "系统设计占比已调整。"
            )
        }
    }

    fun updateBehavioralWeight(value: Float) {
        _uiState.update {
            it.copy(
                behavioralWeight = value.coerceIn(0f, 1f),
                statusText = "行为面占比已调整。"
            )
        }
    }

    fun toggleInstantFeedback() {
        _uiState.update {
            val enabled = !it.instantFeedbackEnabled
            it.copy(
                instantFeedbackEnabled = enabled,
                statusText = if (enabled) "已开启即时点评，面试官会更直接指出回答问题。" else "已关闭即时点评，本轮更接近真实面试节奏。"
            )
        }
    }

    fun toggleCamera() {
        _uiState.update { state ->
            val enabled = !state.isCameraEnabled
            state.copy(
                isCameraEnabled = enabled,
                statusText = if (enabled) {
                    "摄像头已开启，适合进入视频模拟面试。"
                } else {
                    "摄像头已关闭，你仍然可以继续记录本轮回答。"
                }
            )
        }
    }

    fun toggleMic() {
        _uiState.update { state ->
            val enabled = !state.isMicEnabled
            state.copy(
                isMicEnabled = enabled,
                isUserListening = if (enabled) state.isUserListening else false,
                statusText = if (enabled) {
                    "麦克风通道已恢复，后续可以继续接语音输入。"
                } else {
                    "麦克风已静音，当前轮次只保留画面与文字草稿。"
                }
            )
        }
    }

    fun toggleSpeaker() {
        _uiState.update { state ->
            val enabled = !state.isSpeakerEnabled
            state.copy(
                isSpeakerEnabled = enabled,
                statusText = if (enabled) {
                    "面试官语音播报已开启，新问题会自动念出来。"
                } else {
                    "面试官语音播报已关闭，你仍然可以看文字提问。"
                }
            )
        }
    }

    fun updateDraftAnswer(text: String) {
        _uiState.update { it.copy(draftAnswer = text, liveUserTranscript = text) }
    }

    fun updateLiveUserTranscript(text: String) {
        _uiState.update { it.copy(liveUserTranscript = text, draftAnswer = text) }
    }

    fun setUserListening(listening: Boolean) {
        _uiState.update {
            it.copy(
                isUserListening = listening,
                statusText = if (listening) {
                    "正在实时记录你的回答文字。"
                } else {
                    it.statusText
                }
            )
        }
    }

    fun reportVoiceCaptureError(message: String) {
        _uiState.update {
            it.copy(
                isUserListening = false,
                statusText = message
            )
        }
    }

    fun startInterview() {
        val state = _uiState.value
        if (state.isInCall) return

        viewModelScope.launch {
            val persona = state.selectedPersona
            appPreferences.setLastInterviewPersona(persona.name)

            val sessionId = interviewRepository.createSession(
                InterviewSession(
                    personaType = persona,
                    jdRawText = selectedJdText(state),
                    resumeVersionId = state.selectedResumeId,
                    resumeText = selectedResumeText(state)
                )
            )

            interviewRepository.addMessage(
                InterviewMessage(
                    sessionId = sessionId,
                    role = MessageRole.SYSTEM,
                    content = "视频面试已接通，请保持正对镜头。建议先做 60-90 秒自我介绍。",
                    isHint = true
                )
            )
            interviewRepository.incrementQuestionCount(sessionId)
            pushInterviewerQuestion(sessionId, persona, 0)

            _uiState.update {
                it.copy(
                    isInCall = true,
                    activeSessionId = sessionId,
                    elapsedSeconds = 0,
                    questionCount = 1,
                    draftAnswer = "",
                    liveUserTranscript = "",
                    statusText = "已接通 ${persona.displayName} 视频面试，可以开始回答。"
                )
            }

            observeMessages(sessionId)
            startTimer(System.currentTimeMillis())
        }
    }

    fun endInterview() {
        val state = _uiState.value
        val sessionId = state.activeSessionId ?: return

        viewModelScope.launch {
            interviewRepository.addMessage(
                InterviewMessage(
                    sessionId = sessionId,
                    role = MessageRole.SYSTEM,
                    content = "本轮视频面试已结束。你可以回看回答草稿，后续再接复盘分析。",
                    isEvaluation = true
                )
            )
            interviewRepository.endSession(
                sessionId = sessionId,
                overallScore = null,
                improvements = listOf("已完成一轮视频模拟面试")
            )
            stopTimer()
            _uiState.update {
                it.copy(
                    isInCall = false,
                    activeSessionId = null,
                    draftAnswer = "",
                    liveUserTranscript = "",
                    isUserListening = false,
                    statusText = "视频面试已结束，本轮记录已经保留。"
                )
            }
        }
    }

    fun submitAnswer() {
        val state = _uiState.value
        val answer = state.draftAnswer.trim()
        if (answer.isBlank()) return

        submitAnswerText(answer, autoSubmitted = false)
    }

    fun submitRecognizedAnswer(text: String) {
        val answer = text.trim()
        if (answer.isBlank()) return

        _uiState.update { it.copy(liveUserTranscript = answer, draftAnswer = answer) }
        submitAnswerText(answer, autoSubmitted = true)
    }

    private fun submitAnswerText(answer: String, autoSubmitted: Boolean) {
        val state = _uiState.value
        val sessionId = state.activeSessionId ?: return

        viewModelScope.launch {
            interviewRepository.addMessage(
                InterviewMessage(
                    sessionId = sessionId,
                    role = MessageRole.USER,
                    content = answer
                )
            )

            val nextIndex = state.questionCount
            interviewRepository.incrementQuestionCount(sessionId)
            pushInterviewerQuestion(sessionId, state.selectedPersona, nextIndex)

            _uiState.update {
                it.copy(
                    draftAnswer = "",
                    liveUserTranscript = "",
                    isUserListening = false,
                    questionCount = nextIndex + 1,
                    statusText = if (autoSubmitted) {
                        "已自动提交你的回答，面试官正在继续追问。"
                    } else {
                        "回答已记录，继续保持镜头稳定并准备下一问。"
                    }
                )
            }
        }
    }

    private suspend fun hydrateScreen() {
        val savedPersona = runCatching {
            InterviewPersona.valueOf(appPreferences.getLastInterviewPersona())
        }.getOrDefault(InterviewPersona.MILD_TECH)
        val activeSession = interviewRepository.getActiveSession()
        val cachedJd = appPreferences.getCachedJdRawText()
        val cachedCompany = appPreferences.getCachedJdCompanyName()
        val lastResume = appPreferences.getLastResume()

        _uiState.update {
            it.copy(
                selectedPersona = activeSession?.personaType ?: savedPersona,
                companyName = cachedCompany,
                positionName = "",
                jdSummary = cachedJd.lineSequence().firstOrNull().orEmpty(),
                hasResumeContext = lastResume.isNotBlank(),
                isInCall = activeSession != null,
                activeSessionId = activeSession?.id,
                questionCount = activeSession?.questionCount ?: 0,
                elapsedSeconds = activeSession?.createdAt?.let { startedAt ->
                    ((System.currentTimeMillis() - startedAt) / 1000L).toInt().coerceAtLeast(0)
                } ?: 0,
                statusText = if (activeSession != null) {
                    "已恢复上一次未结束的视频面试。"
                } else {
                    "视频面试模式已就绪，开启后会直接进入通话态。"
                },
                isLoading = false
            )
        }

        activeSession?.let {
            observeMessages(it.id)
            startTimer(it.createdAt)
        }
    }

    private fun observeResumeOptions() {
        viewModelScope.launch {
            resumeVersionRepository.getAllFlow().collect { resumes ->
                _uiState.update { current ->
                    val selectedId = current.selectedResumeId
                        ?.takeIf { id -> resumes.any { it.id == id } }
                        ?: resumes.firstOrNull { it.isActive }?.id
                        ?: resumes.firstOrNull()?.id
                    current.copy(
                        resumes = resumes,
                        selectedResumeId = selectedId,
                        hasResumeContext = selectedId != null || current.hasResumeContext
                    )
                }
            }
        }
    }

    private fun observeJdOptions() {
        viewModelScope.launch {
            jdLibraryRepository.getAllFlow().collect { jds ->
                _uiState.update { current ->
                    val selectedId = current.selectedJdId
                        ?.takeIf { id -> jds.any { it.id == id } }
                        ?: jds.firstOrNull()?.id
                    val selected = jds.firstOrNull { it.id == selectedId }
                    current.copy(
                        jds = jds,
                        selectedJdId = selectedId,
                        companyName = selected?.companyName ?: current.companyName,
                        positionName = selected?.positionName ?: current.positionName,
                        jdSummary = selected?.summaryText() ?: current.jdSummary
                    )
                }
            }
        }
    }

    private fun observeMessages(sessionId: Long) {
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            interviewRepository.getMessagesFlow(sessionId).collect { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }
    }

    private fun startTimer(startedAtMs: Long) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                val seconds = ((System.currentTimeMillis() - startedAtMs) / 1000L).toInt().coerceAtLeast(0)
                _uiState.update { current ->
                    if (!current.isInCall) current else current.copy(elapsedSeconds = seconds)
                }
                delay(1000)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private suspend fun pushInterviewerQuestion(
        sessionId: Long,
        persona: InterviewPersona,
        questionIndex: Int
    ) {
        interviewRepository.addMessage(
            InterviewMessage(
                sessionId = sessionId,
                role = MessageRole.INTERVIEWER,
                content = followUpQuestion(persona, questionIndex)
            )
        )
    }

    private fun followUpQuestion(persona: InterviewPersona, questionIndex: Int): String {
        val state = _uiState.value
        val pressurePrefix = when {
            state.pressureLevel >= 0.72f -> "请直接一点，"
            state.pressureLevel <= 0.28f -> "我们慢慢来，"
            else -> ""
        }
        val depthSuffix = when {
            state.followUpDepth >= 0.72f -> "回答时请补充具体场景、你的动作和量化结果。"
            state.followUpDepth <= 0.28f -> "先给一个清晰概要就可以。"
            else -> ""
        }
        val feedbackSuffix = if (state.instantFeedbackEnabled && questionIndex > 0) {
            "我会顺带指出刚才回答里最需要补强的一点。"
        } else {
            ""
        }
        val focusPrompt = state.focusPrompt(questionIndex)
        val prompts = when (persona) {
            InterviewPersona.MILD_TECH -> listOf(
                "先做一个简短自我介绍，再讲一个你最能代表自己的项目。",
                "如果继续深挖，你在这个项目里做过哪些关键技术取舍？",
                "这段经历里，最能体现你成长的一次排障或优化是什么？",
                "如果让你重做一次，你会优先改哪一部分架构？"
            )
            InterviewPersona.PRESSURE -> listOf(
                "两分钟内说清楚你最复杂项目的难点、职责和结果。",
                "别泛泛而谈，给我一个你亲手做出的量化结果。",
                "如果线上出故障，你为什么能证明自己是关键贡献者？",
                "刚才的回答还不够具体，再补一个失败案例和补救动作。"
            )
            InterviewPersona.FOREIGN_HR -> listOf(
                "Please introduce yourself and highlight one story that best fits this role.",
                "Tell me about a time you handled disagreement inside the team.",
                "What was the most stressful deadline you faced, and how did you manage it?",
                "Why do you think this experience makes you a strong fit for the next round?"
            )
            InterviewPersona.STATE_STRUCTURED -> listOf(
                "请先做自我介绍，并说明你为什么适合这个岗位。",
                "请结合一段真实经历，说明你的组织协作能力。",
                "面对突发问题时，你通常如何分析并推动解决？",
                "如果进入岗位，你的前三个月工作重点会是什么？"
            )
            InterviewPersona.CUSTOM -> listOf(
                "先用你最自然的方式完成自我介绍，并说明你最想突出哪项能力。",
                "围绕你刚才的回答，再补充一个更具体的项目细节。",
                "如果把这段经历讲给下一轮面试官，你会强调什么结果？",
                "你觉得哪部分还没说透，可以现在补充。"
            )
        }
        return listOf(pressurePrefix + prompts[questionIndex % prompts.size], focusPrompt, depthSuffix, feedbackSuffix)
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }

    private fun InterviewUiState.focusPrompt(questionIndex: Int): String {
        val focusAreas = listOf(
            "fundamentals" to fundamentalsWeight,
            "project" to projectWeight,
            "algorithm" to algorithmWeight,
            "systemDesign" to systemDesignWeight,
            "behavioral" to behavioralWeight
        ).sortedByDescending { it.second }
        val activeAreas = focusAreas.filter { it.second >= 0.15f }
        val selectedKey = activeAreas.getOrNull(questionIndex % activeAreas.size.coerceAtLeast(1))?.first
            ?: focusAreas.first().first
        return when (selectedKey) {
            "fundamentals" -> "这一题偏八股基础，请围绕原理、关键机制和常见边界情况回答。"
            "project" -> "这一题偏项目经历，请结合你真实做过的项目、职责和结果展开。"
            "algorithm" -> "这一题偏算法思维，请说清思路、复杂度和关键边界。"
            "systemDesign" -> "这一题偏系统设计，请从架构拆分、性能、稳定性和扩展性角度回答。"
            "behavioral" -> "这一题偏行为面，请用 STAR 结构讲清场景、行动和结果。"
            else -> ""
        }
    }

    private suspend fun selectedResumeText(state: InterviewUiState): String {
        val selected = state.selectedResumeId?.let { resumeVersionRepository.getById(it) }
        return selected?.rawText?.ifBlank { selected.cleanedText } ?: appPreferences.getLastResume()
    }

    private fun selectedJdText(state: InterviewUiState): String {
        val selected = state.selectedJdId?.let { id -> state.jds.firstOrNull { it.id == id } }
        return selected?.rawText ?: appPreferences.snapshot().cachedJdRawText
    }

    private fun JdLibraryEntity.summaryText(): String {
        val title = listOf(companyName, positionName).filter { it.isNotBlank() }.joinToString(" · ")
        return title.ifBlank {
            rawText.lineSequence().firstOrNull()?.trim().orEmpty()
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopTimer()
        messagesJob?.cancel()
    }
}
