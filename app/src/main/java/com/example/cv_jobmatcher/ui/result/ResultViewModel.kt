package com.example.cv_jobmatcher.ui.result

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cv_jobmatcher.data.local.db.entity.HistoryEntity
import com.example.cv_jobmatcher.data.repository.CoverLetterRepository
import com.example.cv_jobmatcher.data.repository.HistoryRepository
import com.example.cv_jobmatcher.data.repository.PolishRepository
import com.example.cv_jobmatcher.domain.model.MatchLevel
import com.example.cv_jobmatcher.domain.model.PolishResult
import com.example.cv_jobmatcher.domain.model.ResumeData
import com.example.cv_jobmatcher.domain.nlp.SemanticMatcher
import com.example.cv_jobmatcher.util.DocxFormatter
import com.example.cv_jobmatcher.util.HtmlPdfExporter
import com.example.cv_jobmatcher.util.PdfGenerator
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
import java.io.File
import javax.inject.Inject

data class ResultUiState(
    val polishedResume: String = "",
    val originalResume: String = "",
    val optimizationNote: String = "",
    val jdTitle: String = "",
    val jdRawText: String = "",
    val jdSkills: List<String> = emptyList(),
    // Match analysis
    val matchScore: Int = 0,
    val matchLevel: MatchLevel = MatchLevel.LOW,
    val matchedKeywords: List<String> = emptyList(),
    val missingKeywords: List<String> = emptyList(),
    val suggestions: List<String> = emptyList(),
    // Export
    val selectedDocxTemplate: DocxFormatter.Template = DocxFormatter.Template.CLASSIC,
    val selectedPdfTemplate: PdfGenerator.Template = PdfGenerator.Template.CLASSIC_SINGLE,
    val useHtmlPdf: Boolean = false,
    val isExporting: Boolean = false,
    val exportFile: File? = null,
    // Structured resume data (from JSON)
    val resumeData: ResumeData? = null,
    // Cover Letter
    val isGeneratingCoverLetter: Boolean = false,
    val coverLetter: String? = null,
    // Iterative polish
    val isIterativePolishing: Boolean = false,
    val iterativeHistory: List<String> = emptyList(),
    // State
    val isLoading: Boolean = true,
    val error: String? = null,
    val currentSessionId: Long = -1L
)

@HiltViewModel
class ResultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val historyRepository: HistoryRepository,
    private val polishRepository: PolishRepository,
    private val coverLetterRepository: CoverLetterRepository,
    private val moshi: Moshi,
    private val application: Application
) : ViewModel() {
    companion object { private const val TAG = "ResultVM" }

    private val _uiState = MutableStateFlow(ResultUiState())
    val uiState: StateFlow<ResultUiState> = _uiState.asStateFlow()

    init {
        val sessionId = savedStateHandle.get<Long>("sessionId") ?: -1L
        Log.d(TAG, "init: sessionId=$sessionId")
        if (sessionId > 0) loadResult(sessionId)
        else _uiState.update { it.copy(isLoading = false, error = "未找到润色结果") }
    }

    private fun loadResult(sessionId: Long) {
        viewModelScope.launch {
            try {
                val e = historyRepository.getById(sessionId) ?: run {
                    Log.e(TAG, "记录未找到 id=$sessionId")
                    _uiState.update { it.copy(isLoading = false, error = "记录未找到") }
                    return@launch
                }

                val listType = Types.newParameterizedType(List::class.java, String::class.java)
                val adapter = moshi.adapter<List<String>>(listType)
                val jdSkills = try { adapter.fromJson(e.jdSkills) ?: emptyList() } catch (_: Exception) { emptyList<String>() }
                val matched = try { adapter.fromJson(e.matchedKeywords) ?: emptyList() } catch (_: Exception) { emptyList<String>() }
                val missing = try { adapter.fromJson(e.missingKeywords) ?: emptyList() } catch (_: Exception) { emptyList<String>() }
                val suggestions = try { adapter.fromJson(e.suggestions) ?: emptyList() } catch (_: Exception) { emptyList<String>() }

                val level = when {
                    e.matchScore >= 80 -> MatchLevel.HIGH
                    e.matchScore >= 50 -> MatchLevel.MEDIUM
                    else -> MatchLevel.LOW
                }

                Log.i(TAG, "loadResult: score=${e.matchScore}, level=$level, matched=${matched.size}, missing=${missing.size}")

                val resumeData = if (e.resumeJson.isNotBlank()) {
                    ResumeData.fromJsonString(e.resumeJson)
                } else {
                    null
                }

                _uiState.update {
                    it.copy(
                        polishedResume = e.polishedResume,
                        originalResume = e.originalResume,
                        optimizationNote = e.matchNote,
                        jdTitle = e.jdTitle,
                        jdRawText = e.jdRawText,
                        jdSkills = jdSkills,
                        matchScore = e.matchScore,
                        matchLevel = level,
                        matchedKeywords = matched,
                        missingKeywords = missing,
                        suggestions = suggestions,
                        resumeData = resumeData,
                        currentSessionId = sessionId,
                        isLoading = false
                    )
                }
            } catch (ex: Exception) {
                Log.e(TAG, "loadResult 失败: ${ex.message}", ex)
                _uiState.update { it.copy(isLoading = false, error = "加载失败: ${ex.localizedMessage}") }
            }
        }
    }

    fun iterativePolish(instruction: String) {
        if (instruction.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isIterativePolishing = true) }
            val state = _uiState.value
            Log.d(TAG, "iterativePolish: instruction=${instruction.take(50)}")

            try {
                val resumeJson = state.resumeData?.let { data ->
                    moshi.adapter(ResumeData::class.java).toJson(data)
                } ?: ""

                val result = polishRepository.iterativePolish(
                    jdText = state.jdRawText,
                    currentResumeJson = resumeJson,
                    instruction = instruction
                )

                result.fold(
                    onSuccess = { rawOutput ->
                        val polishResult = PolishResult.fromLlmOutput(rawOutput)
                        val newResumeData = if (polishResult.resumeJson.isNotBlank()) {
                            ResumeData.fromJsonString(polishResult.resumeJson)
                        } else {
                            ResumeData.fromPolishedText(polishResult.polishedResume)
                        }

                        val matchResult = SemanticMatcher.analyze(
                            jdText = state.jdRawText,
                            resumeText = polishResult.polishedResume,
                            jdSkills = state.jdSkills,
                            llmScore = polishResult.matchAnalysis.score.takeIf { it > 0 }
                        )
                        val ma = matchResult.analysis
                        val level = when {
                            ma.score >= 80 -> MatchLevel.HIGH
                            ma.score >= 50 -> MatchLevel.MEDIUM
                            else -> MatchLevel.LOW
                        }

                        val listType = Types.newParameterizedType(List::class.java, String::class.java)
                        val matchedJson = try { moshi.adapter<List<String>>(listType).toJson(ma.matched) } catch (_: Exception) { "[]" }
                        val missingJson = try { moshi.adapter<List<String>>(listType).toJson(ma.missing) } catch (_: Exception) { "[]" }
                        val suggestionsJson = try { moshi.adapter<List<String>>(listType).toJson(ma.suggestions) } catch (_: Exception) { "[]" }
                        val skillsJson = try { moshi.adapter<List<String>>(listType).toJson(state.jdSkills) } catch (_: Exception) { "[]" }

                        val entity = HistoryEntity(
                            createdAt = System.currentTimeMillis(),
                            jdRawText = state.jdRawText,
                            jdTitle = state.jdTitle,
                            originalResume = state.originalResume,
                            polishedResume = polishResult.polishedResume,
                            resumeJson = polishResult.resumeJson,
                            jdSkills = skillsJson,
                            matchNote = polishResult.optimizationNote,
                            matchScore = ma.score,
                            matchedKeywords = matchedJson,
                            missingKeywords = missingJson,
                            suggestions = suggestionsJson,
                            originalFilePath = null,
                            sourceType = "iterative"
                        )
                        val newSessionId = historyRepository.insert(entity)

                        val newHistory = state.iterativeHistory + "→ $instruction"

                        _uiState.update {
                            it.copy(
                                polishedResume = polishResult.polishedResume,
                                resumeData = newResumeData,
                                optimizationNote = polishResult.optimizationNote,
                                matchScore = ma.score,
                                matchLevel = level,
                                matchedKeywords = ma.matched,
                                missingKeywords = ma.missing,
                                suggestions = ma.suggestions,
                                currentSessionId = newSessionId,
                                isIterativePolishing = false,
                                iterativeHistory = newHistory
                            )
                        }
                        Log.i(TAG, "迭代润色完成: sessionId=$newSessionId, score=${ma.score}")
                    },
                    onFailure = { e ->
                        Log.e(TAG, "迭代润色失败: ${e.message}", e)
                        _uiState.update { it.copy(isIterativePolishing = false) }
                        Toast.makeText(application, "迭代润色失败: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "迭代润色异常: ${e.message}", e)
                _uiState.update { it.copy(isIterativePolishing = false) }
            }
        }
    }

    fun selectDocxTemplate(template: DocxFormatter.Template) {
        _uiState.update { it.copy(selectedDocxTemplate = template) }
    }

    fun selectPdfTemplate(template: PdfGenerator.Template) {
        _uiState.update { it.copy(selectedPdfTemplate = template) }
    }

    fun toggleHtmlPdf(use: Boolean) {
        _uiState.update { it.copy(useHtmlPdf = use) }
    }

    fun exportDocx() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            val state = _uiState.value
            Log.i(TAG, "exportDocx: template=${state.selectedDocxTemplate.key}")

            try {
                val fileName = "润色简历_${state.jdTitle.ifBlank { "output" }}"
                val file = withContext(Dispatchers.IO) {
                    val data = state.resumeData ?: ResumeData.fromPolishedText(state.polishedResume)
                    DocxFormatter.exportFromData(
                        resumeData = data,
                        outputFileName = fileName,
                        context = application,
                        template = state.selectedDocxTemplate
                    )
                }
                Log.i(TAG, "DOCX导出成功: ${file.absolutePath} (${file.length()} bytes)")
                _uiState.update { it.copy(isExporting = false, exportFile = file) }
            } catch (e: Exception) {
                Log.e(TAG, "DOCX导出失败: ${e.message}", e)
                _uiState.update { it.copy(isExporting = false) }
                Toast.makeText(application, "DOCX导出失败: ${e.localizedMessage ?: "未知错误"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun exportPdf() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            val state = _uiState.value

            if (state.useHtmlPdf) {
                exportHtmlPdf(state)
            } else {
                exportNativePdf(state)
            }
        }
    }

    private suspend fun exportHtmlPdf(state: ResultUiState) {
        Log.i(TAG, "exportPdf (HTML): template=webview")
        try {
            val resumeData = state.resumeData ?: ResumeData.fromPolishedText(state.polishedResume)
            val file = withContext(Dispatchers.Main) {
                HtmlPdfExporter.exportPdf(application, resumeData)
            }
            Log.i(TAG, "HTML PDF导出成功: ${file.absolutePath} (${file.length() / 1024}KB)")
            _uiState.update { it.copy(isExporting = false, exportFile = file) }
        } catch (e: Exception) {
            Log.e(TAG, "HTML PDF导出失败: ${e.message}", e)
            _uiState.update { it.copy(isExporting = false) }
            Toast.makeText(application, "HTML PDF导出失败: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private suspend fun exportNativePdf(state: ResultUiState) {
        Log.i(TAG, "exportPdf (Native): template=${state.selectedPdfTemplate.label}")
        try {
            val resumeData = state.resumeData ?: ResumeData.fromPolishedText(state.polishedResume)
            val config = PdfGenerator.PdfConfig(template = state.selectedPdfTemplate)

            val file = withContext(Dispatchers.IO) {
                PdfGenerator.generate(
                    context = application,
                    resumeData = resumeData,
                    config = config
                )
            }

            Log.i(TAG, "PDF导出成功: ${file.absolutePath} (${file.length() / 1024}KB)")
            _uiState.update { it.copy(isExporting = false, exportFile = file) }
        } catch (e: Exception) {
            Log.e(TAG, "PDF导出失败: ${e.javaClass.simpleName}: ${e.message}", e)
            _uiState.update { it.copy(isExporting = false) }
            val detail = e.message ?: e.javaClass.simpleName ?: "未知异常"
            Toast.makeText(application, "PDF导出失败: $detail", Toast.LENGTH_LONG).show()
        }
    }

    fun generateCoverLetter() {
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingCoverLetter = true) }
            val state = _uiState.value

            try {
                Log.d(TAG, "生成求职信...")
                val result = coverLetterRepository.generateCoverLetter(
                    jdText = state.jdRawText,
                    resumeText = state.polishedResume
                )

                result.fold(
                    onSuccess = { letter ->
                        Log.i(TAG, "求职信生成成功: ${letter.length}字符")
                        _uiState.update {
                            it.copy(
                                isGeneratingCoverLetter = false,
                                coverLetter = letter
                            )
                        }
                    },
                    onFailure = { e ->
                        Log.e(TAG, "求职信生成失败: ${e.message}", e)
                        _uiState.update { it.copy(isGeneratingCoverLetter = false) }
                        Toast.makeText(application, "求职信生成失败: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "求职信生成异常: ${e.message}", e)
                _uiState.update { it.copy(isGeneratingCoverLetter = false) }
            }
        }
    }

    fun getTemplateSuggestions(): List<String> {
        val state = _uiState.value
        return coverLetterRepository.generateTemplateSuggestions(state.jdRawText)
    }

    fun shareFile(context: Context) {
        val file = _uiState.value.exportFile ?: return
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val mimeType = when (file.extension.lowercase()) {
                "pdf" -> "application/pdf"
                "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                else -> "application/octet-stream"
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "分享简历"))
        } catch (e: Exception) {
            Log.e(TAG, "分享失败: ${e.message}", e)
            Toast.makeText(context, "分享失败: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
