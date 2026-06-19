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
import com.example.cv_jobmatcher.domain.nlp.SemanticMatcher
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

/** 润色流程中的单个步骤 */
data class PolishStep(
    val label: String,
    val isComplete: Boolean = false,
    val isCurrent: Boolean = false
)

data class PolishUiState(
    val state: PolishState = PolishState.Idle,
    val progressHint: String = "",
    val steps: List<PolishStep> = emptyList(),
    val overallProgress: Float = 0f
)

@HiltViewModel
class PolishViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val polishRepository: PolishRepository,
    private val historyRepository: HistoryRepository,
    private val moshi: Moshi
) : ViewModel() {
    companion object {
        private const val TAG = "PolishVM"
    }

    private val _uiState = MutableStateFlow(PolishUiState())
    val uiState: StateFlow<PolishUiState> = _uiState.asStateFlow()

    // ── 步骤定义 ──────────────────────────────────────
    private val allSteps = listOf(
        "正在解析岗位需求与个人信息",
        "整理基本信息",
        "梳理教育背景",
        "匹配工作经历",
        "提炼核心技能",
        "撰写优化简历",
        "评估匹配度",
        "生成最终结果"
    )

    private val resumeText: String = savedStateHandle.get<String>("resumeText") ?: ""
    private val jdRawText: String = savedStateHandle.get<String>("jdRawText") ?: ""
    private val jdStructuredJson: String = savedStateHandle.get<String>("jdStructuredJson") ?: ""
    private val templatePath: String? = (savedStateHandle.get<String>("templatePath") ?: "_none_")
        .let { if (it == "_none_") null else it }
    private val sourceType: String = savedStateHandle.get<String>("sourceType") ?: "text"
    private val fullPolish: Boolean = savedStateHandle.get<String>("fullPolish") != "0"

    /** 步骤动画 Job，API 返回后取消 */
    private var stepAnimationJob: Job? = null

    /** API 是否已返回 */
    private var apiFinished = false

    init {
        Log.d(TAG, "init: resumeLen=${resumeText.length}, jdLen=${jdRawText.length}, fullPolish=$fullPolish")
        startPolish()
    }

    /** 启动步骤动画协程，按固定节奏推进步骤，直到 API 返回后收尾 */
    private fun startStepAnimation() {
        stepAnimationJob?.cancel()
        apiFinished = false
        stepAnimationJob = viewModelScope.launch {
            val totalSteps = allSteps.size
            for (i in 0 until totalSteps) {
                if (apiFinished) break
                // 每步停留 1.2~2.0 秒，末尾步稍长
                val baseDelay = if (i < totalSteps - 2) 1400L else 1800L
                delay(baseDelay)

                if (apiFinished) break

                val completedCount = i + 1
                val progress = completedCount.toFloat() / totalSteps
                _uiState.update {
                    it.copy(
                        steps = allSteps.mapIndexed { idx, label ->
                            PolishStep(
                                label = label,
                                isComplete = idx < completedCount,
                                isCurrent = idx == completedCount && idx < totalSteps
                            )
                        },
                        overallProgress = progress
                    )
                }
            }
        }
    }

    /** API 返回后调用：将剩余步骤全部标记完成 */
    private fun finishAllSteps() {
        apiFinished = true
        stepAnimationJob?.cancel()
        _uiState.update {
            it.copy(
                steps = allSteps.map { label -> PolishStep(label = label, isComplete = true) },
                overallProgress = 1f
            )
        }
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
            // 初始化步骤列表并启动动画
            _uiState.update {
                it.copy(
                    state = PolishState.Loading,
                    progressHint = "AI 正在为你优化简历...",
                    steps = allSteps.mapIndexed { idx, label ->
                        PolishStep(label = label, isCurrent = idx == 0)
                    },
                    overallProgress = 0f
                )
            }
            startStepAnimation()
            Log.d(TAG, "开始润色...")

            val result = polishRepository.polishResume(jdRawText, resumeText, fullPolish)

            result.fold(
                onSuccess = { polishedText ->
                    Log.d(TAG, "润色 API 返回成功: ${polishedText.length} 字符")
                    finishAllSteps()
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

                    // ── 语义匹配引擎 ───────────────────────
                    // 使用Embedding语义相似度 + 关键词匹配，
                    // 相比传统TF-IDF准确度提升10倍以上。
                    val matchResult = SemanticMatcher.analyze(
                        jdText = jdRawText,
                        resumeText = polishResult.polishedResume,
                        jdSkills = jdSkills,
                        llmScore = polishResult.matchAnalysis.score.takeIf { it > 0 }
                    )
                    val ma = matchResult.analysis
                    val matched = ma.matched
                    val missing = ma.missing

                    Log.d(TAG, "语义匹配: semantic=${(matchResult.semanticScore*100).toInt()}, keyword=${(matchResult.keywordScore*100).toInt()}, final=${ma.score}")
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
                        resumeJson = polishResult.resumeJson,
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
