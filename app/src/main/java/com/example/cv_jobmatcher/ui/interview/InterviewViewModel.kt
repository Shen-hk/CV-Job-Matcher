package com.example.cv_jobmatcher.ui.interview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cv_jobmatcher.data.local.AppPreferences
import com.example.cv_jobmatcher.data.remote.AiProviderManager
import com.example.cv_jobmatcher.data.remote.InterviewPrompts
import com.example.cv_jobmatcher.data.remote.LlmRequest
import com.example.cv_jobmatcher.data.remote.PromptConfig
import com.example.cv_jobmatcher.data.remote.StreamEvent
import com.example.cv_jobmatcher.data.remote.dto.Message
import com.example.cv_jobmatcher.data.repository.InterviewRepository
import com.example.cv_jobmatcher.data.repository.ResumeVersionRepository
import com.example.cv_jobmatcher.domain.model.InterviewMessage
import com.example.cv_jobmatcher.domain.model.InterviewPersona
import com.example.cv_jobmatcher.domain.model.InterviewResult
import com.example.cv_jobmatcher.domain.model.InterviewSession
import com.example.cv_jobmatcher.domain.model.MessageRole
import com.squareup.moshi.Moshi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InterviewUiState(
    val sessionId: Long = 0,
    val messages: List<InterviewMessage> = emptyList(),
    val persona: InterviewPersona = InterviewPersona.MILD_TECH,
    val isActive: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val questionCount: Int = 0,
    // End state
    val isFinished: Boolean = false,
    val result: InterviewResult? = null,
    // UI toggles
    val showOutline: Boolean = false,
    val showPersonaPicker: Boolean = false
)

@HiltViewModel
class InterviewViewModel @Inject constructor(
    private val aiProviderManager: AiProviderManager,
    private val interviewPrompts: InterviewPrompts,
    private val interviewRepository: InterviewRepository,
    private val resumeVersionRepository: ResumeVersionRepository,
    private val appPreferences: AppPreferences,
    private val moshi: Moshi
) : ViewModel() {

    private val _uiState = MutableStateFlow(InterviewUiState())
    val uiState: StateFlow<InterviewUiState> = _uiState.asStateFlow()

    private var currentJob: Job? = null

    init {
        // Restore last persona preference
        viewModelScope.launch {
            val personaStr = appPreferences.getLastInterviewPersona()
            try {
                val persona = InterviewPersona.valueOf(personaStr)
                _uiState.update { it.copy(persona = persona) }
            } catch (_: Exception) { }
        }
    }

    // ── Persona ────────────────────────────────────────────

    fun setPersona(persona: InterviewPersona) {
        _uiState.update { it.copy(persona = persona) }
        viewModelScope.launch {
            appPreferences.setLastInterviewPersona(persona.name)
        }
    }

    fun togglePersonaPicker() {
        _uiState.update { it.copy(showPersonaPicker = !it.showPersonaPicker) }
    }

    // ── Start Interview ────────────────────────────────────

    fun startInterview(jdRawText: String, resumeText: String = "") {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val persona = _uiState.value.persona

                // Create session
                val session = InterviewSession(
                    personaType = persona,
                    jdRawText = jdRawText,
                    resumeText = resumeText
                )
                val sessionId = interviewRepository.createSession(session)
                _uiState.update { it.copy(sessionId = sessionId, isActive = true) }

                // Build opening message
                val promptConfig = interviewPrompts.getSystemPrompt(persona)
                val context = interviewPrompts.buildOpeningContext(persona, jdRawText, resumeText)

                val messages = listOf(
                    Message(role = "system", content = promptConfig.system),
                    Message(role = "user", content = context)
                )

                val streamFlow = aiProviderManager.chatWithFallbackStream(
                    LlmRequest(
                        messages = messages,
                        temperature = promptConfig.temperature,
                        maxTokens = promptConfig.maxTokens
                    )
                )

                var fullContent = ""
                var streamError: String? = null

                streamFlow.collect { event ->
                    when (event) {
                        is StreamEvent.Start -> { }
                        is StreamEvent.Content -> {
                            fullContent += event.text
                            _uiState.update {
                                val msgs = it.messages.toMutableList()
                                val lastMsg = msgs.lastOrNull()
                                if (lastMsg?.role == MessageRole.INTERVIEWER && lastMsg.id == 0L) {
                                    msgs[msgs.lastIndex] = lastMsg.copy(content = fullContent)
                                } else {
                                    msgs.add(InterviewMessage(
                                        sessionId = sessionId,
                                        role = MessageRole.INTERVIEWER,
                                        content = fullContent
                                    ))
                                }
                                it.copy(messages = msgs)
                            }
                        }
                        is StreamEvent.Done -> { }
                        is StreamEvent.Error -> {
                            streamError = event.message
                        }
                    }
                }

                if (streamError != null) {
                    throw Exception(streamError)
                }

                val interviewerMsg = InterviewMessage(
                    sessionId = sessionId,
                    role = MessageRole.INTERVIEWER,
                    content = fullContent
                )
                interviewRepository.addMessage(interviewerMsg)

                _uiState.update {
                    val msgs = it.messages.toMutableList()
                    val lastIdx = msgs.indexOfLast { m -> m.role == MessageRole.INTERVIEWER && m.id == 0L }
                    if (lastIdx >= 0) {
                        msgs[lastIdx] = interviewerMsg
                    }
                    it.copy(
                        isLoading = false,
                        messages = msgs
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "启动面试失败: ${e.message}") }
            }
        }
    }

    // ── Send Answer ────────────────────────────────────────

    fun sendAnswer(answer: String) {
        val state = _uiState.value
        if (state.isLoading || !state.isActive) return

        // Save user message
        val userMsg = InterviewMessage(
            sessionId = state.sessionId,
            role = MessageRole.USER,
            content = answer
        )

        _uiState.update {
            it.copy(
                messages = it.messages + userMsg,
                isLoading = true,
                error = null
            )
        }

        currentJob = viewModelScope.launch {
            try {
                interviewRepository.addMessage(userMsg)

                val promptConfig = interviewPrompts.getSystemPrompt(state.persona)
                val conversationMessages = buildConversationMessages(state, answer, promptConfig)

                val streamFlow = aiProviderManager.chatWithFallbackStream(
                    LlmRequest(
                        messages = conversationMessages,
                        temperature = promptConfig.temperature,
                        maxTokens = promptConfig.maxTokens
                    )
                )

                var fullContent = ""
                var streamError: String? = null

                streamFlow.collect { event ->
                    when (event) {
                        is StreamEvent.Start -> { /* streaming started */ }
                        is StreamEvent.Content -> {
                            fullContent += event.text
                            _uiState.update {
                                val msgs = it.messages.toMutableList()
                                val lastMsg = msgs.lastOrNull()
                                if (lastMsg?.role == MessageRole.INTERVIEWER && lastMsg.id == 0L) {
                                    msgs[msgs.lastIndex] = lastMsg.copy(content = fullContent)
                                } else {
                                    msgs.add(InterviewMessage(
                                        sessionId = state.sessionId,
                                        role = MessageRole.INTERVIEWER,
                                        content = fullContent
                                    ))
                                }
                                it.copy(messages = msgs)
                            }
                        }
                        is StreamEvent.Done -> { /* streaming complete */ }
                        is StreamEvent.Error -> {
                            streamError = event.message
                        }
                    }
                }

                if (streamError != null) {
                    throw Exception(streamError)
                }

                val interviewerMsg = InterviewMessage(
                    sessionId = state.sessionId,
                    role = MessageRole.INTERVIEWER,
                    content = fullContent
                )
                val msgId = interviewRepository.addMessage(interviewerMsg)
                interviewRepository.incrementQuestionCount(state.sessionId)

                _uiState.update {
                    val msgs = it.messages.toMutableList()
                    val lastIdx = msgs.indexOfLast { m -> m.role == MessageRole.INTERVIEWER && m.id == 0L }
                    if (lastIdx >= 0) {
                        msgs[lastIdx] = interviewerMsg.copy(id = msgId)
                    }
                    it.copy(
                        isLoading = false,
                        messages = msgs,
                        questionCount = it.questionCount + 1
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "发送失败: ${e.message}") }
            }
        }
    }

    // ── End Interview ──────────────────────────────────────

    fun endInterview() {
        val state = _uiState.value
        if (!state.isActive) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // Build evaluation transcript
                val transcript = state.messages.joinToString("\n") { msg ->
                    val roleLabel = when (msg.role) {
                        MessageRole.INTERVIEWER -> "面试官"
                        MessageRole.USER -> "候选人"
                        MessageRole.SYSTEM -> "系统"
                    }
                    "$roleLabel: ${msg.content}"
                }

                val evalConfig = interviewPrompts.getEvaluationPrompt()
                val evalMessages = listOf(
                    Message(role = "system", content = evalConfig.system),
                    Message(role = "user", content = "以下是面试记录，请评估：\n\n$transcript")
                )

                val response = aiProviderManager.chatWithFallback(
                    LlmRequest(
                        messages = evalMessages,
                        temperature = evalConfig.temperature,
                        maxTokens = evalConfig.maxTokens
                    )
                )

                // Parse evaluation result
                val result = parseEvaluationResult(response.content)

                // Update session
                interviewRepository.endSession(
                    sessionId = state.sessionId,
                    overallScore = result.overallScore,
                    dimensionScores = result.dimensionScores.associate { it.name to it.score },
                    improvements = result.improvements
                )

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isActive = false,
                        isFinished = true,
                        result = result
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "评估失败: ${e.message}") }
            }
        }
    }

    // ── Toolbar Actions ────────────────────────────────────

    fun requestHint() {
        val state = _uiState.value
        if (!state.isActive || state.messages.isEmpty()) return

        viewModelScope.launch {
            val lastQuestion = state.messages.lastOrNull { it.role == MessageRole.INTERVIEWER }
                ?: return@launch

            val hintMsg = InterviewMessage(
                sessionId = state.sessionId,
                role = MessageRole.SYSTEM,
                content = "💡 提示：思考一下相关的项目经验，用STAR法则（情境-任务-行动-结果）组织你的回答。",
                isHint = true
            )
            interviewRepository.addMessage(hintMsg)
            _uiState.update { it.copy(messages = it.messages + hintMsg) }
        }
    }

    fun skipQuestion() {
        val state = _uiState.value
        if (!state.isActive) return

        viewModelScope.launch {
            // Send a system skip message and request next question
            val skipMsg = InterviewMessage(
                sessionId = state.sessionId,
                role = MessageRole.SYSTEM,
                content = "候选人选择跳过此题，请换一个问题。",
                isHint = true
            )

            // Get next question from AI
            try {
                val promptConfig = interviewPrompts.getSystemPrompt(state.persona)
                val messages = buildList {
                    add(Message(role = "system", content = promptConfig.system))
                    add(Message(role = "user", content = "候选人选择跳过当前问题，请换一个不同方向的问题。"))
                }

                val response = aiProviderManager.chatWithFallback(
                    LlmRequest(messages = messages, temperature = promptConfig.temperature, maxTokens = promptConfig.maxTokens)
                )

                val interviewerMsg = InterviewMessage(
                    sessionId = state.sessionId,
                    role = MessageRole.INTERVIEWER,
                    content = response.content
                )
                interviewRepository.addMessage(skipMsg)
                interviewRepository.addMessage(interviewerMsg)

                _uiState.update {
                    it.copy(messages = it.messages + listOf(skipMsg, interviewerMsg))
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "跳过失败: ${e.message}") }
            }
        }
    }

    fun toggleOutline() {
        _uiState.update { it.copy(showOutline = !it.showOutline) }
    }

    fun restartInterview() {
        currentJob?.cancel()
        _uiState.value = InterviewUiState(persona = _uiState.value.persona)
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // ── Helpers ────────────────────────────────────────────

    private fun buildConversationMessages(
        state: InterviewUiState,
        latestAnswer: String,
        promptConfig: PromptConfig
    ): List<Message> {
        val messages = mutableListOf<Message>()
        messages.add(Message(role = "system", content = promptConfig.system))

        // Add last N messages for context (keep context window manageable)
        val recentMessages = state.messages.takeLast(10)
        for (msg in recentMessages) {
            val role = when (msg.role) {
                MessageRole.INTERVIEWER -> "assistant"
                MessageRole.USER -> "user"
                MessageRole.SYSTEM -> "system"
            }
            messages.add(Message(role = role, content = msg.content))
        }
        // Add current answer
        messages.add(Message(role = "user", content = latestAnswer))

        return messages
    }

    private fun parseEvaluationResult(jsonString: String): InterviewResult {
        return try {
            val cleaned = jsonString
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val mapType = com.squareup.moshi.Types.newParameterizedType(
                Map::class.java, String::class.java, Any::class.java
            )
            val map: Map<String, Any> = moshi.adapter<Map<String, Any>>(mapType).fromJson(cleaned)
                ?: return InterviewResult()

            val dimScores = (map["dimension_scores"] as? List<*>)?.mapNotNull { dim ->
                val d = dim as? Map<*, *> ?: return@mapNotNull null
                com.example.cv_jobmatcher.domain.model.DimensionScore(
                    name = d["name"] as? String ?: "",
                    score = ((d["score"] as? Number)?.toFloat() ?: 0f),
                    comment = d["comment"] as? String ?: ""
                )
            } ?: emptyList()

            val keyMoments = (map["key_moments"] as? List<*>)?.mapNotNull { km ->
                val k = km as? Map<*, *> ?: return@mapNotNull null
                val typeStr = k["type"] as? String ?: "IMPROVE"
                com.example.cv_jobmatcher.domain.model.KeyMoment(
                    messageIndex = ((k["message_index"] as? Number)?.toInt() ?: 0),
                    type = try {
                        com.example.cv_jobmatcher.domain.model.MomentType.valueOf(typeStr)
                    } catch (_: Exception) { com.example.cv_jobmatcher.domain.model.MomentType.IMPROVE },
                    description = k["description"] as? String ?: "",
                    suggestion = k["suggestion"] as? String ?: ""
                )
            } ?: emptyList()

            InterviewResult(
                overallScore = ((map["overall_score"] as? Number)?.toFloat() ?: 0f),
                dimensionScores = dimScores,
                improvements = (map["improvements"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                highlights = (map["highlights"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                keyMoments = keyMoments,
                recommendedResumeEdits = (map["recommended_resume_edits"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            )
        } catch (e: Exception) {
            InterviewResult(
                overallScore = 0f,
                improvements = listOf("评估解析失败: ${e.message}")
            )
        }
    }
}
