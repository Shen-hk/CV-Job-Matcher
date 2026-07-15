package com.example.tielink.ui.resumeinput

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tielink.data.local.db.entity.HistoryEntity
import com.example.tielink.data.repository.HistoryRepository
import com.example.tielink.data.repository.ResumeRepository
import com.example.tielink.domain.model.JobDescription
import com.example.tielink.domain.usecase.MatchAnalysisUseCase
import com.example.tielink.util.FileParser
import com.example.tielink.util.OriginalResumeFileStore
import com.example.tielink.util.TextCleaner
import com.squareup.moshi.Moshi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ResumeInputUiState(
    val resumeText: String = "",
    val jdRawText: String = "",
    val jdStructured: JobDescription? = null,
    val isLoaded: Boolean = false,
    val isFileProcessing: Boolean = false,
    val fileName: String? = null,
    val templatePath: String? = null,
    val sourceType: String = "text",
    val fullPolish: Boolean = true,  // true=全篇优化, false=部分优化
    val error: String? = null,
    // ── JD优化 mode ──
    val flowMode: String = "legacy",  // "legacy" | "jd_optimize"
    val showHistoryPicker: Boolean = false,
    val historyItems: List<HistoryEntity> = emptyList(),
    val showMatchDialog: Boolean = false,
    val matchScore: Int = 0,
    val matchedKeywords: List<String> = emptyList(),
    val missingKeywords: List<String> = emptyList(),
    val matchSuggestions: List<String> = emptyList(),
    val isAnalyzing: Boolean = false
)

@HiltViewModel
class ResumeInputViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val resumeRepository: ResumeRepository,
    private val moshi: Moshi,
    private val historyRepository: HistoryRepository,
    private val matchAnalysisUseCase: MatchAnalysisUseCase
) : ViewModel() {
    companion object {
        private const val TAG = "ResumeInputVM"
    }

    private val _uiState = MutableStateFlow(ResumeInputUiState())
    val uiState: StateFlow<ResumeInputUiState> = _uiState.asStateFlow()

    init {
        val jdRawText = savedStateHandle.get<String>("jdRawText") ?: ""
        val jdStructuredJson = savedStateHandle.get<String>("jdStructuredJson") ?: ""
        Log.d(TAG, "init: jdLen=${jdRawText.length}")

        val jdStructured = if (jdStructuredJson.isNotBlank()) {
            try {
                moshi.adapter(JobDescription::class.java).fromJson(jdStructuredJson)
            } catch (_: Exception) { null }
        } else null

        _uiState.update { it.copy(jdRawText = jdRawText, jdStructured = jdStructured) }

        viewModelScope.launch {
            val lastResume = resumeRepository.getLastResume()
            if (lastResume.isNotBlank()) {
                Log.d(TAG, "加载上次简历缓存: ${lastResume.length} 字符")
                _uiState.update { it.copy(resumeText = lastResume, isLoaded = true) }
            }
        }
    }

    fun updateResumeText(text: String) {
        _uiState.update { it.copy(resumeText = text, error = null) }
    }

    fun appendResumeText(text: String) {
        val cleaned = TextCleaner.clean(text)
        if (cleaned.isBlank()) return

        _uiState.update { state ->
            val combined = if (state.resumeText.isBlank()) cleaned else "${state.resumeText}\n$cleaned"
            state.copy(resumeText = combined, error = null)
        }
    }

    fun setError(message: String?) {
        _uiState.update { it.copy(error = message) }
    }

    fun clearResume() {
        Log.d(TAG, "清空简历")
        _uiState.update { it.copy(
            resumeText = "",
            isLoaded = false,
            fileName = null,
            templatePath = null,
            sourceType = "text"
        )}
    }

    fun processFile(context: Context, uri: Uri, mimeType: String?, fileName: String?) {
        viewModelScope.launch {
            Log.d(TAG, "processFile: uri=$uri, mimeType=$mimeType, fileName=$fileName")
            _uiState.update { it.copy(isFileProcessing = true, error = null) }

            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val storedFile = OriginalResumeFileStore.copyFromUri(
                        context,
                        uri,
                        fileName ?: "我的简历"
                    ).getOrThrow()
                    val text = FileParser.extractText(context, uri, mimeType).getOrThrow()
                    storedFile to text
                }
            }

            result.fold(
                onSuccess = { (storedFile, text) ->
                    val isDocx = mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
                            fileName?.lowercase()?.endsWith(".docx") == true
                    val isPdf = mimeType == "application/pdf" ||
                            fileName?.lowercase()?.endsWith(".pdf") == true

                    val sourceType = when {
                        isDocx -> "docx"
                        isPdf -> "pdf"
                        else -> "text"
                    }

                    _uiState.update {
                        it.copy(
                            resumeText = text,
                            isFileProcessing = false,
                            fileName = fileName,
                            templatePath = storedFile.absolutePath,
                            sourceType = sourceType,
                            isLoaded = true
                        )
                    }
                    Log.i(TAG, "文件处理完成: sourceType=$sourceType, textLen=${text.length}")
                },
                onFailure = { e ->
                    Log.e(TAG, "文件处理失败: ${e.message}", e)
                    _uiState.update {
                        it.copy(isFileProcessing = false, error = e.localizedMessage ?: "文件解析失败")
                    }
                }
            )
        }
    }

    fun togglePolishMode() { _uiState.update { it.copy(fullPolish = !it.fullPolish) } }

    fun submitResume(
        onSuccess: (resumeText: String, jdRawText: String, jdStructuredJson: String,
                     templatePath: String?, sourceType: String, fullPolish: Boolean) -> Unit
    ) {
        val resumeText = _uiState.value.resumeText.trim()
        if (resumeText.isBlank()) {
            _uiState.update { it.copy(error = "请粘贴简历文本或上传 PDF/DOCX 文件") }
            return
        }

        val state = _uiState.value
        Log.i(TAG, "submitResume: textLen=${resumeText.length}, sourceType=${state.sourceType}, templatePath=${state.templatePath}")

        viewModelScope.launch {
            val cleanedResume = TextCleaner.clean(resumeText)
            resumeRepository.saveResume(cleanedResume)

            val jdJson = state.jdStructured?.let {
                moshi.adapter(JobDescription::class.java).toJson(it)
            } ?: ""

            onSuccess(cleanedResume, state.jdRawText, jdJson, state.templatePath, state.sourceType, state.fullPolish)
        }
    }

    // ═══════════════════════════════════════════════════════
    //  JD优化 mode — 历史版本选择
    // ═══════════════════════════════════════════════════════

    fun loadHistoryVersions() {
        viewModelScope.launch {
            try {
                val items = historyRepository.getAllFlow().first()
                _uiState.update { it.copy(historyItems = items, showHistoryPicker = true) }
            } catch (e: Exception) {
                Log.e(TAG, "加载历史版本失败: ${e.message}", e)
            }
        }
    }

    fun selectHistoryVersion(entityId: Long) {
        viewModelScope.launch {
            try {
                val entity = historyRepository.getById(entityId)
                if (entity != null) {
                    _uiState.update {
                        it.copy(
                            resumeText = entity.polishedResume,
                            showHistoryPicker = false,
                            isLoaded = true,
                            sourceType = "history"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "选择历史版本失败: ${e.message}", e)
            }
        }
    }

    fun dismissHistoryPicker() {
        _uiState.update { it.copy(showHistoryPicker = false) }
    }

    // ═══════════════════════════════════════════════════════
    //  JD优化 mode — 匹配分析 + 优化确认
    // ═══════════════════════════════════════════════════════

    fun analyzeAndPrompt(jdSkills: List<String> = emptyList()) {
        val state = _uiState.value
        val resumeText = state.resumeText.trim()
        if (resumeText.isBlank() || state.jdRawText.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzing = true, error = null) }
            try {
                val result = matchAnalysisUseCase.analyze(
                    jdText = state.jdRawText,
                    resumeText = resumeText,
                    jdSkills = jdSkills.ifEmpty { state.jdStructured?.skills ?: emptyList() }
                )
                _uiState.update {
                    it.copy(
                        isAnalyzing = false,
                        showMatchDialog = true,
                        matchScore = result.analysis.score,
                        matchedKeywords = result.analysis.matched,
                        missingKeywords = result.analysis.missing,
                        matchSuggestions = result.analysis.suggestions
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "匹配分析失败: ${e.message}", e)
                _uiState.update { it.copy(isAnalyzing = false, error = "匹配分析失败: ${e.message}") }
            }
        }
    }

    fun dismissMatchDialog() {
        _uiState.update { it.copy(showMatchDialog = false) }
    }

    /** 用户在匹配弹窗点"开始优化"后，继续走润色流程 */
    fun confirmPolish(
        onSuccess: (resumeText: String, jdRawText: String, jdStructuredJson: String,
                     templatePath: String?, sourceType: String, fullPolish: Boolean) -> Unit
    ) {
        _uiState.update { it.copy(showMatchDialog = false) }
        submitResume(onSuccess)
    }
}
