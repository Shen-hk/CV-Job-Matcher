package com.example.cv_jobmatcher.ui.resumeoptimize

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cv_jobmatcher.data.local.AppPreferences
import com.example.cv_jobmatcher.data.remote.PromptRegistry
import com.example.cv_jobmatcher.data.repository.PolishRepository
import com.example.cv_jobmatcher.data.repository.ResumeVersionRepository
import com.example.cv_jobmatcher.domain.model.MatchAnalysis
import com.example.cv_jobmatcher.domain.model.ResumeVersion
import com.example.cv_jobmatcher.domain.model.SkillGap
import com.example.cv_jobmatcher.domain.model.SkillImportance
import com.example.cv_jobmatcher.domain.usecase.MatchAnalysisUseCase
import com.example.cv_jobmatcher.util.FileParser
import com.example.cv_jobmatcher.util.TextCleaner
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ResumeOptimizeUiState(
    val resumeText: String = "",
    val cleanedText: String = "",
    val isProcessing: Boolean = false,
    val error: String? = null,
    val currentVersion: ResumeVersion? = null,
    val versions: List<ResumeVersion> = emptyList(),
    // AI polish result
    val polishedText: String = "",
    val matchScore: Int = 0,
    val suggestions: List<String> = emptyList(),
    val optimizationNote: String = "",
    // Match analysis detail
    val keywordCoverage: Float = 0f,
    val skillFit: Float = 0f,
    val experienceRelevance: Float = 0f,
    val missingSkills: List<String> = emptyList(),
    // UI mode
    val isVersionSelectorOpen: Boolean = false,
    val showMatchDetail: Boolean = false,
    // Source type & file
    val sourceType: String = "text",
    val isFileProcessing: Boolean = false,
    val fileName: String? = null
)

@HiltViewModel
class ResumeOptimizeViewModel @Inject constructor(
    private val polishRepository: PolishRepository,
    private val resumeVersionRepository: ResumeVersionRepository,
    private val matchAnalysisUseCase: MatchAnalysisUseCase,
    private val promptRegistry: PromptRegistry,
    private val textCleaner: TextCleaner,
    private val appPreferences: AppPreferences,
    private val moshi: Moshi
) : ViewModel() {

    companion object {
        private const val TAG = "ResumeOptimizeVM"
    }

    private val _uiState = MutableStateFlow(ResumeOptimizeUiState())
    val uiState: StateFlow<ResumeOptimizeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val lastResume = appPreferences.getLastResume()
            if (lastResume.isNotBlank()) {
                _uiState.update { it.copy(resumeText = lastResume, cleanedText = lastResume) }
            }
            val versions = resumeVersionRepository.getAll()
            val active = versions.firstOrNull { it.isActive }
            _uiState.update {
                it.copy(versions = versions, currentVersion = active ?: versions.firstOrNull())
            }
        }
    }

    fun updateResumeText(text: String) {
        _uiState.update { it.copy(resumeText = text, cleanedText = textCleaner.clean(text)) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // ── AI Polish ──────────────────────────────────────────

    fun polishResume(jdRawText: String, jdStructuredJson: String, fullPolish: Boolean = true) {
        val state = _uiState.value
        if (state.resumeText.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null) }
            try {
                val result = polishRepository.polishResume(
                    jdText = jdRawText,
                    resumeText = state.resumeText,
                    fullPolish = fullPolish
                )
                result.onSuccess { jsonContent ->
                    Log.d(TAG, "Polish success: ${jsonContent.length} chars")
                    val parsed = parsePolishResult(jsonContent)
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            polishedText = parsed.polishedText,
                            matchScore = parsed.matchScore ?: it.matchScore,
                            optimizationNote = parsed.optimizationNote ?: "",
                            suggestions = parsed.suggestions ?: emptyList()
                        )
                    }
                }.onFailure { e ->
                    _uiState.update { it.copy(isProcessing = false, error = "AI润色失败: ${e.message}") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isProcessing = false, error = "AI润色失败: ${e.message}") }
            }
        }
    }

    // ── Match Analysis ─────────────────────────────────────

    fun analyzeMatch(jdRawText: String, jdStructuredJson: String, jdSkills: List<String> = emptyList()) {
        viewModelScope.launch {
            val state = _uiState.value
            val text = state.polishedText.ifBlank { state.resumeText }
            if (text.isBlank() || jdRawText.isBlank()) return@launch

            try {
                val result = matchAnalysisUseCase.analyze(
                    jdText = jdRawText,
                    resumeText = text,
                    jdSkills = jdSkills
                )
                _uiState.update {
                    it.copy(
                        matchScore = result.analysis.score,
                        keywordCoverage = result.keywordScore.toFloat(),
                        skillFit = result.tfidfScore.toFloat(),
                        experienceRelevance = 0f, // Not separately computed yet
                        suggestions = result.analysis.suggestions,
                        missingSkills = result.analysis.missing,
                        showMatchDetail = true
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "匹配分析失败: ${e.message}") }
            }
        }
    }

    fun toggleMatchDetail() {
        _uiState.update { it.copy(showMatchDetail = !it.showMatchDetail) }
    }

    // ── Version Management ─────────────────────────────────

    fun saveVersion(name: String) {
        viewModelScope.launch {
            val state = _uiState.value
            val text = state.polishedText.ifBlank { state.resumeText }
            if (text.isBlank()) return@launch

            val version = ResumeVersion(
                name = name.ifBlank { "未命名版本" },
                rawText = state.resumeText,
                cleanedText = text,
                matchScore = state.matchScore.toFloat()
            )
            resumeVersionRepository.save(version)
            val versions = resumeVersionRepository.getAll()
            val active = versions.firstOrNull { it.isActive }
            _uiState.update { it.copy(versions = versions, currentVersion = active ?: versions.firstOrNull()) }
        }
    }

    fun loadVersion(id: Long) {
        viewModelScope.launch {
            val version = resumeVersionRepository.getById(id) ?: return@launch
            _uiState.update {
                it.copy(
                    resumeText = version.rawText,
                    cleanedText = version.cleanedText,
                    currentVersion = version,
                    isVersionSelectorOpen = false
                )
            }
        }
    }

    fun toggleVersionSelector() {
        _uiState.update { it.copy(isVersionSelectorOpen = !it.isVersionSelectorOpen) }
    }

    // ── File Upload ────────────────────────────────────────

    fun processFile(context: Context, uri: Uri, mimeType: String?, fileName: String?) {
        viewModelScope.launch {
            Log.d(TAG, "processFile: uri=$uri, fileName=$fileName")
            _uiState.update { it.copy(isFileProcessing = true, error = null) }

            val result = withContext(Dispatchers.IO) {
                FileParser.extractText(context, uri, mimeType)
            }

            result.fold(
                onSuccess = { text ->
                    val sourceType = when {
                        mimeType?.contains("pdf") == true || fileName?.lowercase()?.endsWith(".pdf") == true -> "pdf"
                        mimeType?.contains("docx") == true || fileName?.lowercase()?.endsWith(".docx") == true -> "docx"
                        else -> "text"
                    }
                    _uiState.update {
                        it.copy(
                            resumeText = if (it.resumeText.isBlank()) text else "${it.resumeText}\n\n$text",
                            cleanedText = TextCleaner.clean(text),
                            isFileProcessing = false,
                            fileName = fileName,
                            sourceType = sourceType
                        )
                    }
                    appPreferences.setLastResume(text)
                    Log.i(TAG, "文件处理完成: sourceType=$sourceType, textLen=${text.length}")
                },
                onFailure = { e ->
                    Log.e(TAG, "文件处理失败: ${e.message}", e)
                    _uiState.update { it.copy(isFileProcessing = false, error = "文件解析失败: ${e.message}") }
                }
            )
        }
    }

    fun clearResume() {
        _uiState.update {
            it.copy(
                resumeText = "",
                cleanedText = "",
                polishedText = "",
                matchScore = 0,
                suggestions = emptyList(),
                optimizationNote = "",
                currentVersion = null,
                fileName = null,
                sourceType = "text"
            )
        }
    }

    // ── Helpers ────────────────────────────────────────────

    /**
     * Tries to parse the polish result JSON, falls back to using the raw text as the polished result.
     * Returns: (polishedText, matchScore?, optimizationNote?, suggestions?)
     */
    private fun parsePolishResult(jsonString: String): Quadruple {
        return try {
            val cleaned = jsonString
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()
            val mapType = Types.newParameterizedType(
                Map::class.java, String::class.java, Any::class.java
            )
            val map: Map<String, Any> = moshi.adapter<Map<String, Any>>(mapType).fromJson(cleaned)
                ?: return Quadruple(jsonString, null, null, null)

            // Build a formatted text from the structured JSON
            val sb = StringBuilder()
            map["name"]?.let { sb.appendLine(it) }
            map["targetPosition"]?.let { sb.appendLine("目标：$it") }
            map["contact"]?.let { sb.appendLine(it) }
            sb.appendLine()
            map["summary"]?.let { sb.appendLine(it); sb.appendLine() }

            // Experiences
            (map["experiences"] as? List<*>)?.forEach { exp ->
                val e = exp as? Map<*, *> ?: return@forEach
                sb.appendLine("${e["title"]} @ ${e["company"]} (${e["period"]})")
                (e["highlights"] as? List<*>)?.forEach { h -> sb.appendLine("  - $h") }
                sb.appendLine()
            }

            // Education
            (map["education"] as? List<*>)?.forEach { edu ->
                val e = edu as? Map<*, *> ?: return@forEach
                sb.appendLine("${e["school"]} - ${e["degree"]} (${e["period"]})")
            }
            sb.appendLine()

            // Projects
            (map["projects"] as? List<*>)?.forEach { proj ->
                val p = proj as? Map<*, *> ?: return@forEach
                sb.appendLine("${p["name"]} (${p["period"]})")
                sb.appendLine("  ${p["description"]}")
                (p["technologies"] as? List<*>)?.joinToString(", ")
                    ?.let { sb.appendLine("  技术栈: $it") }
                sb.appendLine()
            }

            // Skills
            (map["skills"] as? List<*>)?.joinToString(", ")?.let {
                sb.appendLine("技能: $it")
            }

            val matchScore = (map["matchScore"] as? Number)?.toInt()
            val optimizationNote = map["optimizationNote"] as? String
            val suggestions = (map["suggestions"] as? List<*>)?.mapNotNull { it as? String }

            Quadruple(sb.toString().trim(), matchScore, optimizationNote, suggestions)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse polish result: ${e.message}, using raw text")
            Quadruple(jsonString, null, null, null)
        }
    }
}

/** Simple 4-tuple for inner use. */
private data class Quadruple(
    val polishedText: String,
    val matchScore: Int?,
    val optimizationNote: String?,
    val suggestions: List<String>?
)
