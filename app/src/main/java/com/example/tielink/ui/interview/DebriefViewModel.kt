package com.example.tielink.ui.interview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tielink.domain.model.SavedJobDescription
import com.example.tielink.data.repository.JdLibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DebriefUiState(
    val recordingName: String = "",
    val recordingMimeType: String = "",
    val recordingUri: String = "",
    val transcript: String = "",
    val jds: List<SavedJobDescription> = emptyList(),
    val selectedJdId: Long? = null,
    val isAnalyzing: Boolean = false,
    val report: DebriefReport? = null,
    val statusText: String = "粘贴面试转写文本生成规则提示；录音/视频目前仅登记材料，不会自动转写或分析。"
)

@HiltViewModel
class DebriefViewModel @Inject constructor(
    private val jdLibraryRepository: JdLibraryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DebriefUiState())
    val uiState: StateFlow<DebriefUiState> = _uiState.asStateFlow()

    init {
        observeOptions()
    }

    fun attachRecording(name: String, mimeType: String?, uri: String) {
        _uiState.update {
            it.copy(
                recordingName = name,
                recordingMimeType = mimeType.orEmpty(),
                recordingUri = uri,
                statusText = "已登记材料：$name，尚未读取音视频内容。请粘贴转写文本进行复盘。"
            )
        }
    }

    fun updateTranscript(text: String) {
        _uiState.update { it.copy(transcript = text, report = null) }
    }

    fun appendRecognizedText(text: String) {
        val cleaned = text.trim()
        if (cleaned.isBlank()) return
        _uiState.update {
            val next = buildString {
                append(it.transcript.trim())
                if (isNotBlank()) append("\n")
                append(cleaned)
            }
            it.copy(transcript = next, report = null, statusText = "已追加一段语音转写。")
        }
    }

    fun selectJd(id: Long?) {
        _uiState.update {
            val selected = it.jds.firstOrNull { jd -> jd.id == id }
            it.copy(
                selectedJdId = selected?.id,
                report = null,
                statusText = selected?.let { jd -> "已关联岗位：${jd.optionLabel()}" } ?: "已取消关联岗位。"
            )
        }
    }

    fun analyzeDebrief() {
        val state = _uiState.value
        val transcript = state.transcript.trim()
        if (transcript.isBlank()) {
            _uiState.update { it.copy(statusText = "需要先提供面试转写文本，才能生成复盘。") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzing = true, statusText = "正在检查转写文本...") }
            val jd = state.selectedJdId?.let { id -> state.jds.firstOrNull { it.id == id } }
            val skills = jd?.skills.orEmpty().split(",", "，", "、", "/", "|")
            val report = DebriefTextAnalyzer.analyze(transcript, skills)
            _uiState.update {
                it.copy(
                    isAnalyzing = false,
                    report = report,
                    statusText = "文本规则提示已生成，请结合原始问答核对。"
                )
            }
        }
    }

    private fun observeOptions() {
        viewModelScope.launch {
            jdLibraryRepository.getAllFlow().collect { jds ->
                _uiState.update { current ->
                    current.copy(
                        jds = jds,
                        selectedJdId = current.selectedJdId?.takeIf { id -> jds.any { it.id == id } }
                            ?: jds.firstOrNull()?.id
                    )
                }
            }
        }
    }

    fun showVoiceError(message: String) {
        _uiState.update { it.copy(statusText = "听写失败：" + message) }
    }

    private fun SavedJobDescription.optionLabel(): String {
        return listOf(companyName, positionName).filter { it.isNotBlank() }.joinToString(" · ")
            .ifBlank { rawText.take(24).ifBlank { "未命名岗位" } }
    }
}
