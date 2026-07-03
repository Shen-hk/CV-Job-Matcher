package com.example.tielink.ui.resumeoptimize

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tielink.data.local.AppPreferences
import com.example.tielink.data.repository.PolishRepository
import com.example.tielink.data.repository.ResumeVersionRepository
import com.example.tielink.domain.model.ResumeVersion
import com.example.tielink.domain.model.SkillGap
import com.example.tielink.domain.model.SkillImportance
import com.example.tielink.domain.usecase.MatchAnalysisUseCase
import com.example.tielink.domain.usecase.MatchScoreDetailUseCase
import com.example.tielink.domain.usecase.QuantifyAssistant
import com.example.tielink.domain.usecase.QuantifySuggestion
import com.example.tielink.domain.usecase.SkillGapAnalyzer
import com.example.tielink.domain.usecase.StarFormatter
import com.example.tielink.domain.usecase.StarResult
import com.example.tielink.util.FileParser
import com.example.tielink.util.OriginalResumeFileStore
import com.example.tielink.util.TextCleaner
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

// Preset version tags
val VERSION_TAG_PRESETS = listOf("技术岗", "产品岗", "外企岗", "管理岗", "实习")

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
    // Match dimension scores (Sprint 2.1)
    val keywordCoverage: Float = 0f,
    val skillFit: Float = 0f,
    val experienceRelevance: Float = 0f,
    val educationMatch: Float = 0f,
    // Skill gaps (Sprint 2.2)
    val skillGaps: List<SkillGap> = emptyList(),
    val showSkillGaps: Boolean = false,
    // Quantify suggestions (Sprint 2.3)
    val quantifySuggestions: List<QuantifySuggestion> = emptyList(),
    val isQuantifying: Boolean = false,
    val showQuantifySuggestions: Boolean = false,
    // STAR format (Sprint 2.4)
    val starResult: StarResult? = null,
    val isStarFormatting: Boolean = false,
    val showStarResult: Boolean = false,
    // Version tags (Sprint 2.5)
    val selectedTags: List<String> = emptyList(),
    // UI mode
    val isVersionSelectorOpen: Boolean = false,
    val showMatchDetail: Boolean = false,
    val showVersionCompare: Boolean = false,
    val compareVersion: ResumeVersion? = null,
    // Legacy (kept for v1 MatchScoreCard)
    val missingSkills: List<String> = emptyList(),
    // Source type & file
    val sourceType: String = "text",
    val isFileProcessing: Boolean = false,
    val fileName: String? = null,
    val originalFilePath: String = "",
    val originalMimeType: String = ""
)

@HiltViewModel
class ResumeOptimizeViewModel @Inject constructor(
    private val polishRepository: PolishRepository,
    private val resumeVersionRepository: ResumeVersionRepository,
    private val matchAnalysisUseCase: MatchAnalysisUseCase,
    private val matchScoreDetailUseCase: MatchScoreDetailUseCase,
    private val skillGapAnalyzer: SkillGapAnalyzer,
    private val quantifyAssistant: QuantifyAssistant,
    private val starFormatter: StarFormatter,
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
            refreshVersions()
        }
    }

    fun updateResumeText(text: String) {
        _uiState.update { it.copy(resumeText = text, cleanedText = textCleaner.clean(text)) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // ── AI Polish ──────────────────────────────────────────────

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

    // ── Match Analysis (Sprint 2.1) ────────────────────────────

    fun analyzeMatch(jdRawText: String, jdStructuredJson: String, jdSkills: List<String> = emptyList()) {
        viewModelScope.launch {
            val state = _uiState.value
            val text = state.polishedText.ifBlank { state.resumeText }
            if (text.isBlank() || jdRawText.isBlank()) return@launch

            try {
                // Base match analysis (keyword + TF-IDF)
                val result = matchAnalysisUseCase.analyze(
                    jdText = jdRawText,
                    resumeText = text,
                    jdSkills = jdSkills
                )

                // Enrich with v2 dimension scores
                val enriched = matchScoreDetailUseCase.enrich(
                    existing = result.analysis,
                    jdText = jdRawText,
                    resumeText = text
                )

                // Skill gap analysis (Sprint 2.2)
                val skillGaps = skillGapAnalyzer.analyze(
                    jdText = jdRawText,
                    resumeText = text,
                    jdSkills = result.analysis.missing + result.analysis.matched
                )

                _uiState.update {
                    it.copy(
                        matchScore = enriched.score,
                        keywordCoverage = enriched.keywordCoverage,
                        skillFit = enriched.skillFit,
                        experienceRelevance = enriched.experienceRelevance,
                        educationMatch = enriched.educationMatch,
                        missingSkills = enriched.missing,
                        skillGaps = skillGaps,
                        suggestions = enriched.suggestions,
                        showMatchDetail = true,
                        showSkillGaps = skillGaps.isNotEmpty()
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

    fun toggleSkillGaps() {
        _uiState.update { it.copy(showSkillGaps = !it.showSkillGaps) }
    }

    // Sprint 2.2: Add a missing skill to resume text
    fun addSkillToResume(skill: String) {
        val state = _uiState.value
        val currentText = state.resumeText
        val updated = if (currentText.contains("技能") || currentText.contains("Skills")) {
            // Insert before the last occurrence of skill section end
            currentText.trimEnd() + "\n$skill"
        } else {
            currentText.trimEnd() + "\n\n技能：$skill"
        }
        _uiState.update {
            it.copy(
                resumeText = updated,
                skillGaps = it.skillGaps.filterNot { gap -> gap.skill == skill }
            )
        }
    }

    // ── Quantify Assistant (Sprint 2.3) ────────────────────────

    fun detectAndSuggestQuantification() {
        val text = _uiState.value.polishedText.ifBlank { _uiState.value.resumeText }
        if (text.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isQuantifying = true, error = null) }
            try {
                val suggestions = quantifyAssistant.analyzeAndSuggest(text)
                _uiState.update {
                    it.copy(
                        isQuantifying = false,
                        quantifySuggestions = suggestions,
                        showQuantifySuggestions = suggestions.isNotEmpty(),
                        error = if (suggestions.isEmpty()) "未检测到可量化的模糊描述" else null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isQuantifying = false, error = "量化分析失败: ${e.message}") }
            }
        }
    }

    // Apply a quantify suggestion by replacing original text with quantified version
    fun applyQuantifySuggestion(suggestion: QuantifySuggestion) {
        val state = _uiState.value
        val targetText = state.polishedText.ifBlank { state.resumeText }
        val updated = targetText.replace(suggestion.original, suggestion.quantified)
        val remainingSuggestions = state.quantifySuggestions.filterNot { it == suggestion }
        if (state.polishedText.isNotBlank()) {
            _uiState.update {
                it.copy(
                    polishedText = updated,
                    quantifySuggestions = remainingSuggestions,
                    showQuantifySuggestions = remainingSuggestions.isNotEmpty()
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    resumeText = updated,
                    quantifySuggestions = remainingSuggestions,
                    showQuantifySuggestions = remainingSuggestions.isNotEmpty()
                )
            }
        }
    }

    fun dismissQuantifySuggestion(suggestion: QuantifySuggestion) {
        val remaining = _uiState.value.quantifySuggestions.filterNot { it == suggestion }
        _uiState.update { it.copy(quantifySuggestions = remaining, showQuantifySuggestions = remaining.isNotEmpty()) }
    }

    fun dismissAllQuantifySuggestions() {
        _uiState.update { it.copy(quantifySuggestions = emptyList(), showQuantifySuggestions = false) }
    }

    // ── STAR Formatter (Sprint 2.4) ────────────────────────────

    fun formatAsStar(experienceText: String) {
        if (experienceText.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isStarFormatting = true, error = null) }
            try {
                val result = starFormatter.format(experienceText)
                if (result != null) {
                    _uiState.update {
                        it.copy(
                            isStarFormatting = false,
                            starResult = result,
                            showStarResult = true
                        )
                    }
                } else {
                    _uiState.update { it.copy(isStarFormatting = false, error = "STAR格式化失败，请重试") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isStarFormatting = false, error = "STAR格式化失败: ${e.message}") }
            }
        }
    }

    fun applyStarResult() {
        val state = _uiState.value
        val starResult = state.starResult ?: return
        val targetText = state.polishedText.ifBlank { state.resumeText }
        val withStar = targetText.trimEnd() + "\n\n${starResult.formatted}"
        if (state.polishedText.isNotBlank()) {
            _uiState.update { it.copy(polishedText = withStar, starResult = null, showStarResult = false) }
        } else {
            _uiState.update { it.copy(resumeText = withStar, starResult = null, showStarResult = false) }
        }
    }

    fun dismissStarResult() {
        _uiState.update { it.copy(starResult = null, showStarResult = false) }
    }

    // ── Version Management (Sprint 2.5) ────────────────────────

    fun saveVersion(name: String, tags: List<String> = emptyList()) {
        viewModelScope.launch {
            val state = _uiState.value
            val text = state.polishedText.ifBlank { state.resumeText }
            if (text.isBlank()) return@launch

            val version = ResumeVersion(
                name = name.ifBlank { "未命名版本" },
                rawText = state.resumeText,
                cleanedText = text,
                matchScore = state.matchScore.toFloat(),
                tags = tags,
                originalFilePath = state.originalFilePath,
                originalMimeType = state.originalMimeType,
                isPolished = state.polishedText.isNotBlank() || state.originalFilePath.isBlank()
            )
            resumeVersionRepository.save(version)
            refreshVersions()
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
                    originalFilePath = version.originalFilePath,
                    originalMimeType = version.originalMimeType,
                    fileName = version.name.takeIf { version.originalFilePath.isNotBlank() },
                    sourceType = when (version.originalMimeType) {
                        "application/pdf" -> "pdf"
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
                        else -> "text"
                    },
                    isVersionSelectorOpen = false
                )
            }
        }
    }

    fun toggleVersionSelector() {
        _uiState.update { it.copy(isVersionSelectorOpen = !it.isVersionSelectorOpen) }
    }

    fun toggleSelectedTag(tag: String) {
        val current = _uiState.value.selectedTags.toMutableList()
        if (current.contains(tag)) current.remove(tag) else current.add(tag)
        _uiState.update { it.copy(selectedTags = current) }
    }

    fun clearSelectedTags() {
        _uiState.update { it.copy(selectedTags = emptyList()) }
    }

    fun showVersionCompare(compareWith: ResumeVersion) {
        _uiState.update { it.copy(showVersionCompare = true, compareVersion = compareWith) }
    }

    fun dismissVersionCompare() {
        _uiState.update { it.copy(showVersionCompare = false, compareVersion = null) }
    }

    // ── File Upload ────────────────────────────────────────────

    fun processFile(context: Context, uri: Uri, mimeType: String?, fileName: String?) {
        viewModelScope.launch {
            Log.d(TAG, "processFile: uri=$uri, fileName=$fileName")
            _uiState.update { it.copy(isFileProcessing = true, error = null) }

            val result = withContext(Dispatchers.IO) {
                OriginalResumeFileStore.copyFromUri(
                    context,
                    uri,
                    fileName ?: "我的简历"
                )
            }

            result.fold(
                onSuccess = { storedFile ->
                    val sourceType = when {
                        mimeType?.contains("pdf") == true || fileName?.lowercase()?.endsWith(".pdf") == true -> "pdf"
                        mimeType?.contains("docx") == true || fileName?.lowercase()?.endsWith(".docx") == true -> "docx"
                        else -> "text"
                    }
                    val versionId = resumeVersionRepository.insertAndActivate(
                        ResumeVersion(
                            name = fileName?.substringBeforeLast(".").orEmpty()
                                .ifBlank { "我的简历" },
                            rawText = "",
                            originalFilePath = storedFile.absolutePath,
                            originalMimeType = mimeType.orEmpty(),
                            isPolished = false
                        )
                    )
                    val savedVersion = resumeVersionRepository.getById(versionId)
                    _uiState.update {
                        it.copy(
                            resumeText = "",
                            cleanedText = "",
                            isFileProcessing = false,
                            fileName = fileName,
                            sourceType = sourceType,
                            originalFilePath = storedFile.absolutePath,
                            originalMimeType = mimeType.orEmpty(),
                            currentVersion = savedVersion
                        )
                    }
                    refreshVersions()
                    Log.i(TAG, "原文件保存完成: sourceType=$sourceType")
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
                sourceType = "text",
                originalFilePath = "",
                originalMimeType = "",
                skillGaps = emptyList(),
                quantifySuggestions = emptyList(),
                starResult = null
            )
        }
    }

    fun openOriginalFile(context: Context) {
        val state = _uiState.value
        OriginalResumeFileStore.open(
            context,
            state.originalFilePath,
            state.originalMimeType
        ).onFailure { error ->
            _uiState.update { it.copy(error = "无法打开原文件: ${error.message}") }
        }
    }

    fun prepareForPolish(context: Context, onReady: (String) -> Unit) {
        val state = _uiState.value
        if (state.originalFilePath.isBlank()) {
            if (state.resumeText.isNotBlank()) onReady(state.resumeText)
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isFileProcessing = true, error = null) }
            val result = withContext(Dispatchers.IO) {
                FileParser.extractText(
                    context,
                    Uri.fromFile(java.io.File(state.originalFilePath)),
                    state.originalMimeType
                )
            }
            result.fold(
                onSuccess = { text ->
                    _uiState.update {
                        it.copy(
                            resumeText = text,
                            cleanedText = TextCleaner.clean(text),
                            isFileProcessing = false
                        )
                    }
                    onReady(text)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isFileProcessing = false,
                            error = "读取原文件失败: ${error.message}"
                        )
                    }
                }
            )
        }
    }

    // ── Helpers ────────────────────────────────────────────────

    private suspend fun refreshVersions() {
        val versions = resumeVersionRepository.getAll()
        val active = versions.firstOrNull { it.isActive }
        _uiState.update {
            it.copy(versions = versions, currentVersion = active ?: versions.firstOrNull())
        }
    }

    /**
     * Tries to parse the polish result JSON, falls back to using the raw text.
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

            val sb = StringBuilder()
            map["name"]?.let { sb.appendLine(it) }
            map["targetPosition"]?.let { sb.appendLine("目标：$it") }
            map["contact"]?.let { sb.appendLine(it) }
            sb.appendLine()
            map["summary"]?.let { sb.appendLine(it); sb.appendLine() }

            (map["experiences"] as? List<*>)?.forEach { exp ->
                val e = exp as? Map<*, *> ?: return@forEach
                sb.appendLine("${e["title"]} @ ${e["company"]} (${e["period"]})")
                (e["highlights"] as? List<*>)?.forEach { h -> sb.appendLine("  - $h") }
                sb.appendLine()
            }

            (map["education"] as? List<*>)?.forEach { edu ->
                val e = edu as? Map<*, *> ?: return@forEach
                sb.appendLine("${e["school"]} - ${e["degree"]} (${e["period"]})")
            }
            sb.appendLine()

            (map["projects"] as? List<*>)?.forEach { proj ->
                val p = proj as? Map<*, *> ?: return@forEach
                sb.appendLine("${p["name"]} (${p["period"]})")
                sb.appendLine("  ${p["description"]}")
                (p["technologies"] as? List<*>)?.joinToString(", ")
                    ?.let { sb.appendLine("  技术栈: $it") }
                sb.appendLine()
            }

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

private data class Quadruple(
    val polishedText: String,
    val matchScore: Int?,
    val optimizationNote: String?,
    val suggestions: List<String>?
)
