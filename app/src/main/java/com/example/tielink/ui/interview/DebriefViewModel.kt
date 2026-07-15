package com.example.tielink.ui.interview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tielink.data.local.db.entity.JdLibraryEntity
import com.example.tielink.data.repository.JdLibraryRepository
import com.example.tielink.data.repository.ResumeVersionRepository
import com.example.tielink.domain.model.ResumeVersion
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
    val jds: List<JdLibraryEntity> = emptyList(),
    val resumes: List<ResumeVersion> = emptyList(),
    val selectedJdId: Long? = null,
    val selectedResumeId: Long? = null,
    val isAnalyzing: Boolean = false,
    val report: DebriefReport? = null,
    val statusText: String = "上传真实面试录音/视频，或直接粘贴转写文本开始复盘。"
)

data class DebriefReport(
    val expressionScore: Int,
    val contentScore: Int,
    val behaviorScore: Int,
    val summary: String,
    val questions: List<DebriefQuestionSlice>,
    val expressionFindings: List<String>,
    val contentRisks: List<String>,
    val behaviorNotes: List<String>,
    val nextPracticeFocus: List<String>,
    val resumeSuggestions: List<String>,
    val greetingSuggestions: List<String>
)

data class DebriefQuestionSlice(
    val title: String,
    val evidence: String,
    val suggestion: String
)

@HiltViewModel
class DebriefViewModel @Inject constructor(
    private val jdLibraryRepository: JdLibraryRepository,
    private val resumeVersionRepository: ResumeVersionRepository
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
                statusText = "已选择材料：$name。请补充转写文本后生成复盘。"
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
                statusText = selected?.let { jd -> "已关联岗位：${jd.optionLabel()}" } ?: "已取消关联岗位。"
            )
        }
    }

    fun selectResume(id: Long?) {
        _uiState.update {
            val selected = it.resumes.firstOrNull { resume -> resume.id == id }
            it.copy(
                selectedResumeId = selected?.id,
                statusText = selected?.let { resume -> "已关联简历：${resume.name}" } ?: "已取消关联简历。"
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
            _uiState.update { it.copy(isAnalyzing = true, statusText = "正在分析真实面试表现...") }
            val report = buildReport(state, transcript)
            _uiState.update {
                it.copy(
                    isAnalyzing = false,
                    report = report,
                    statusText = "复盘已生成：优先看风险表达和下一轮训练重点。"
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
        viewModelScope.launch {
            resumeVersionRepository.getAllFlow().collect { resumes ->
                _uiState.update { current ->
                    current.copy(
                        resumes = resumes,
                        selectedResumeId = current.selectedResumeId?.takeIf { id -> resumes.any { it.id == id } }
                            ?: resumes.firstOrNull { it.isActive }?.id
                            ?: resumes.firstOrNull()?.id
                    )
                }
            }
        }
    }

    private fun buildReport(state: DebriefUiState, transcript: String): DebriefReport {
        val fillerWords = listOf("嗯", "呃", "那个", "就是", "然后", "其实", "可能", "大概")
        val fillerCount = fillerWords.sumOf { word -> Regex(Regex.escape(word)).findAll(transcript).count() }
        val riskWords = listOf("不知道", "不会", "没做过", "随便", "应该吧", "差不多", "不清楚", "可能吧")
        val risks = riskWords.filter { transcript.contains(it) }
        val numericSupport = Regex("""\d+[%\w万千kK]?""").findAll(transcript).count()
        val questions = extractQuestions(transcript)
        val jd = state.selectedJdId?.let { id -> state.jds.firstOrNull { it.id == id } }
        val jdSkills = jd?.skills.orEmpty().split(",", "，", "、", "/", "|").map { it.trim() }.filter { it.length >= 2 }
        val skillHits = jdSkills.count { transcript.contains(it, ignoreCase = true) }

        val expressionScore = (82 - fillerCount * 2 + numericSupport).coerceIn(35, 95)
        val contentScore = (72 + skillHits * 3 + numericSupport * 2 - risks.size * 6).coerceIn(35, 96)
        val behaviorScore = when {
            state.recordingMimeType.startsWith("video/") -> 70
            state.recordingMimeType.startsWith("audio/") -> 66
            else -> 62
        }

        val expressionFindings = buildList {
            add("口头禅/缓冲词约 $fillerCount 次，${if (fillerCount >= 6) "建议压缩停顿前的填充词" else "整体可控"}。")
            add("量化信息出现 $numericSupport 次，${if (numericSupport >= 3) "能支撑结果表达" else "建议补更多数据、规模、耗时或转化结果"}。")
            add("回答长度约 ${transcript.length} 字，复盘时可按“问题-动作-结果”拆成更短段落。")
        }
        val contentRisks = buildList {
            if (risks.isEmpty()) {
                add("未检测到明显高风险否定表达。")
            } else {
                add("检测到风险表达：${risks.joinToString("、")}，建议改成“我当时的处理边界是...”这类可解释表述。")
            }
            if (jdSkills.isNotEmpty()) {
                add("岗位关键词命中 $skillHits/${jdSkills.size} 个，后续回答可主动贴合 ${jdSkills.take(4).joinToString("、")}。")
            } else {
                add("未关联明确 JD 技能，内容风险只能按通用面试标准判断。")
            }
        }
        val behaviorNotes = buildList {
            add(if (state.recordingMimeType.startsWith("video/")) "已登记视频材料；首版只做提醒级结论，画面行为分析后续接入。" else "当前没有视频画面，行为维度仅基于表达节奏做提醒。")
            add("建议回看片段时重点观察：是否频繁低头、长停顿、回答时视线游离。")
        }
        val nextPracticeFocus = listOf(
            "把高频问题整理成 3 个 STAR 案例，每个案例保留 1 个量化结果。",
            "针对最薄弱问题做一轮 5 分钟限时复答。",
            "下一轮模拟面试优先练：${questions.firstOrNull()?.title ?: "项目深挖和基础原理"}。"
        )
        val resumeSuggestions = listOf(
            "把面试中被追问最多的项目补进简历亮点，尤其是你的具体动作和结果。",
            "如果回答里缺少数据，简历对应经历也应补充规模、指标或业务影响。",
            "把和 JD 命中的技能放到更靠前的位置，减少面试官反复确认。"
        )
        val greetingSuggestions = listOf(
            "打招呼语里突出真实被追问过、且回答稳定的项目亮点。",
            "避免使用泛泛的“熟悉/了解”，改成“做过什么、解决什么问题”。"
        )

        return DebriefReport(
            expressionScore = expressionScore,
            contentScore = contentScore,
            behaviorScore = behaviorScore,
            summary = "识别到 ${questions.size} 个候选问题，表达分 $expressionScore，内容分 $contentScore。下一步建议先补强量化结果和 JD 关键词贴合。",
            questions = questions,
            expressionFindings = expressionFindings,
            contentRisks = contentRisks,
            behaviorNotes = behaviorNotes,
            nextPracticeFocus = nextPracticeFocus,
            resumeSuggestions = resumeSuggestions,
            greetingSuggestions = greetingSuggestions
        )
    }

    private fun extractQuestions(transcript: String): List<DebriefQuestionSlice> {
        val lines = transcript.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
        val questionKeywords = listOf("介绍", "说说", "讲讲", "为什么", "怎么", "如何", "项目", "难点", "原理", "优化", "离职", "期望")
        val candidates = lines.filter { line ->
            line.endsWith("？") || line.endsWith("?") || questionKeywords.any { line.contains(it) }
        }.ifEmpty {
            transcript.split("。", "？", "?", "\n").map { it.trim() }.filter { it.length >= 12 }
        }.take(8)

        return candidates.mapIndexed { index, text ->
            DebriefQuestionSlice(
                title = "问题 ${index + 1}",
                evidence = text.take(120),
                suggestion = when {
                    text.contains("项目") || text.contains("难点") -> "用 STAR 结构补齐背景、你的动作和量化结果。"
                    text.contains("原理") || text.contains("优化") -> "先讲机制，再讲权衡，最后落到真实项目。"
                    text.contains("离职") || text.contains("期望") -> "控制情绪评价，转成职业目标和岗位匹配。"
                    else -> "回答时先给结论，再补 1 个具体例子。"
                }
            )
        }
    }

    private fun JdLibraryEntity.optionLabel(): String {
        return listOf(companyName, positionName).filter { it.isNotBlank() }.joinToString(" · ")
            .ifBlank { rawText.take(24).ifBlank { "未命名岗位" } }
    }
}
