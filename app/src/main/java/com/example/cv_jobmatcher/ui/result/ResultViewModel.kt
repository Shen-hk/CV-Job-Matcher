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
import com.example.cv_jobmatcher.data.repository.HistoryRepository
import com.example.cv_jobmatcher.domain.model.MatchLevel
import com.example.cv_jobmatcher.util.DocxFormatter
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
    val jdSkills: List<String> = emptyList(),
    // Match analysis
    val matchScore: Int = 0,
    val matchLevel: MatchLevel = MatchLevel.LOW,
    val matchedKeywords: List<String> = emptyList(),
    val missingKeywords: List<String> = emptyList(),
    val suggestions: List<String> = emptyList(),
    // Export
    val selectedTemplate: DocxFormatter.Template = DocxFormatter.Template.CLASSIC,
    val isExporting: Boolean = false,
    val exportFile: File? = null,
    // State
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class ResultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val historyRepository: HistoryRepository,
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

                _uiState.update {
                    it.copy(
                        polishedResume = e.polishedResume,
                        originalResume = e.originalResume,
                        optimizationNote = e.matchNote,
                        jdTitle = e.jdTitle,
                        jdSkills = jdSkills,
                        matchScore = e.matchScore,
                        matchLevel = level,
                        matchedKeywords = matched,
                        missingKeywords = missing,
                        suggestions = suggestions,
                        isLoading = false
                    )
                }
            } catch (ex: Exception) {
                Log.e(TAG, "loadResult 失败: ${ex.message}", ex)
                _uiState.update { it.copy(isLoading = false, error = "加载失败: ${ex.localizedMessage}") }
            }
        }
    }

    fun selectTemplate(template: DocxFormatter.Template) {
        _uiState.update { it.copy(selectedTemplate = template) }
    }

    fun exportDocx() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            val state = _uiState.value
            Log.i(TAG, "exportDocx: template=${state.selectedTemplate.key}")

            try {
                val fileName = "润色简历_${state.jdTitle.ifBlank { "output" }}"
                val file = withContext(Dispatchers.IO) {
                    DocxFormatter.export(
                        polishedText = state.polishedResume,
                        outputFileName = fileName,
                        context = application,
                        template = state.selectedTemplate
                    )
                }
                Log.i(TAG, "导出成功: ${file.absolutePath} (${file.length()} bytes)")
                _uiState.update { it.copy(isExporting = false, exportFile = file) }
            } catch (e: Exception) {
                Log.e(TAG, "导出失败: ${e.message}", e)
                _uiState.update { it.copy(isExporting = false) }
                Toast.makeText(application, "导出失败: ${e.localizedMessage ?: "未知错误"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun shareFile(context: Context) {
        val file = _uiState.value.exportFile ?: return
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "分享简历"))
        } catch (e: Exception) {
            Toast.makeText(context, "分享失败: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
