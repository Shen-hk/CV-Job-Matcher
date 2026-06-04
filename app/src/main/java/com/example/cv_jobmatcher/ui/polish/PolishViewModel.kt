package com.example.cv_jobmatcher.ui.polish

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cv_jobmatcher.data.local.db.entity.HistoryEntity
import com.example.cv_jobmatcher.data.repository.HistoryRepository
import com.example.cv_jobmatcher.data.repository.PolishRepository
import com.example.cv_jobmatcher.domain.model.JobDescription
import com.example.cv_jobmatcher.domain.model.PolishResult
import com.example.cv_jobmatcher.domain.usecase.MatchAnalysisUseCase
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PolishState {
    data object Idle : PolishState
    data object Loading : PolishState
    data class Success(val sessionId: Long) : PolishState
    data class Error(val message: String) : PolishState
}

data class PolishUiState(
    val state: PolishState = PolishState.Idle,
    val progressHint: String = ""
)

@HiltViewModel
class PolishViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val polishRepository: PolishRepository,
    private val historyRepository: HistoryRepository,
    private val matchAnalysisUseCase: MatchAnalysisUseCase,
    private val moshi: Moshi
) : ViewModel() {
    companion object {
        private const val TAG = "PolishVM"
    }

    private val _uiState = MutableStateFlow(PolishUiState())
    val uiState: StateFlow<PolishUiState> = _uiState.asStateFlow()

    private val resumeText: String = savedStateHandle.get<String>("resumeText") ?: ""
    private val jdRawText: String = savedStateHandle.get<String>("jdRawText") ?: ""
    private val jdStructuredJson: String = savedStateHandle.get<String>("jdStructuredJson") ?: ""
    private val templatePath: String? = (savedStateHandle.get<String>("templatePath") ?: "_none_")
        .let { if (it == "_none_") null else it }
    private val sourceType: String = savedStateHandle.get<String>("sourceType") ?: "text"
    private val fullPolish: Boolean = savedStateHandle.get<String>("fullPolish") != "0"

    init {
        Log.d(TAG, "init: resumeLen=${resumeText.length}, jdLen=${jdRawText.length}, fullPolish=$fullPolish")
        startPolish()
    }

    private fun startPolish() {
        if (resumeText.isBlank() || jdRawText.isBlank()) {
            Log.e(TAG, "数据不完整: resumeBlank=${resumeText.isBlank()}, jdBlank=${jdRawText.isBlank()}")
            _uiState.update {
                it.copy(state = PolishState.Error("数据不完整，请返回重新输入"))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(state = PolishState.Loading, progressHint = "正在分析岗位需求...") }
            Log.d(TAG, "开始润色...")

            val result = polishRepository.polishResume(jdRawText, resumeText, fullPolish)

            result.fold(
                onSuccess = { polishedText ->
                    Log.d(TAG, "润色 API 返回成功: ${polishedText.length} 字符")
                    _uiState.update { it.copy(progressHint = "正在保存结果...") }

                    val polishResult = PolishResult.fromLlmOutput(polishedText)

                    val jd = try {
                        moshi.adapter(JobDescription::class.java).fromJson(jdStructuredJson)
                    } catch (_: Exception) { null }

                    val jdSkills = jd?.skills ?: emptyList()
                    val skillsJson = run {
                        val t = Types.newParameterizedType(List::class.java, String::class.java)
                        try { moshi.adapter<List<String>>(t).toJson(jdSkills) } catch (_: Exception) { "[]" }
                    }

                    // ── UseCase: NLP matching pipeline ──────────
                    // Runs TF-IDF cosine similarity + deterministic keyword match,
                    // then blends with the LLM score for the final result.
                    val matchResult = matchAnalysisUseCase.analyze(
                        jdText = jdRawText,
                        resumeText = polishResult.polishedResume,
                        jdSkills = jdSkills,
                        llmScore = polishResult.matchAnalysis.score.takeIf { it > 0 }
                    )
                    val ma = matchResult.analysis
                    val matched = ma.matched
                    val missing = ma.missing

                    Log.d(TAG, "NLP匹配: tfidf=${(matchResult.tfidfScore*100).toInt()}, keyword=${(matchResult.keywordScore*100).toInt()}, final=${ma.score}")
                    Log.d(TAG, "关键词: 已匹配=${matched.size}$matched, 缺失=${missing.size}$missing")

                    val listType = Types.newParameterizedType(List::class.java, String::class.java)
                    val matchedJson = try { moshi.adapter<List<String>>(listType).toJson(matched) } catch (_: Exception) { "[]" }
                    val missingJson = try { moshi.adapter<List<String>>(listType).toJson(missing) } catch (_: Exception) { "[]" }
                    val suggestionsJson = try { moshi.adapter<List<String>>(listType).toJson(ma.suggestions) } catch (_: Exception) { "[]" }

                    Log.d(TAG, "解析结果: body=${polishResult.polishedResume.length} chars, score=${ma.score}, matched=${matched.size}, missing=${missing.size}")

                    val entity = HistoryEntity(
                        createdAt = System.currentTimeMillis(),
                        jdRawText = jdRawText,
                        jdTitle = jd?.jobTitle ?: "未知岗位",
                        originalResume = resumeText,
                        polishedResume = polishResult.polishedResume,
                        jdSkills = skillsJson,
                        matchNote = polishResult.optimizationNote,
                        matchScore = ma.score,
                        matchedKeywords = matchedJson,
                        missingKeywords = missingJson,
                        suggestions = suggestionsJson,
                        originalFilePath = templatePath,
                        sourceType = sourceType
                    )

                    Log.d(TAG, "保存到 Room: sourceType=$sourceType, templatePath=$templatePath")
                    val sessionId = historyRepository.insert(entity)
                    Log.i(TAG, "润色完成: sessionId=$sessionId")
                    _uiState.update {
                        it.copy(state = PolishState.Success(sessionId))
                    }
                },
                onFailure = { e ->
                    Log.e(TAG, "润色失败: ${e.message}", e)
                    _uiState.update {
                        it.copy(state = PolishState.Error(e.localizedMessage ?: "润色失败，请重试"))
                    }
                }
            )
        }
    }

    fun retry() {
        Log.d(TAG, "重试润色")
        startPolish()
    }
}
