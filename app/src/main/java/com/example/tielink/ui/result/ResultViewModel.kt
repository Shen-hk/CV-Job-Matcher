package com.example.tielink.ui.result

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tielink.data.local.db.entity.HistoryEntity
import com.example.tielink.data.repository.CoverLetterRepository
import com.example.tielink.data.repository.HistoryRepository
import com.example.tielink.data.repository.PolishRepository
import com.example.tielink.domain.model.MatchLevel
import com.example.tielink.domain.model.PolishResult
import com.example.tielink.domain.model.ResumeData
import com.example.tielink.domain.nlp.SemanticMatcher
import com.example.tielink.util.HtmlPdfExporter
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
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

data class ChatMessage(
    val role: String,   // "user" | "assistant"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

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
    val useVibeTemplate: Boolean = false,
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
    val isHistoryExpanded: Boolean = false,
    val polishMode: String = "ai", // "ai" or "manual"
    // State
    val isLoading: Boolean = true,
    val error: String? = null,
    val currentSessionId: Long = -1L,
    // ── 内容编辑 ──
    val selectedTab: String = "edit",   // "edit" | "flow_agent"
    val expandedSection: String? = null, // "personal" | "projects" | "education" | "experience" | "skills"
    // ── AI 聊天 ──
    val aiChatMessages: List<ChatMessage> = emptyList(),
    val isAiProcessing: Boolean = false,
    val showCompletionAnimation: Boolean = false
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

                val resumeData = (if (e.resumeJson.isNotBlank()) {
                    ResumeData.fromJsonString(e.resumeJson)
                } else {
                    null
                })?.withAutoDetectedLinks()

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
                        val newResumeData = (if (polishResult.resumeJson.isNotBlank()) {
                            ResumeData.fromJsonString(polishResult.resumeJson)
                        } else {
                            ResumeData.fromPolishedText(polishResult.polishedResume)
                        })?.withAutoDetectedLinks()
                        // 如果 AI 没返回链接，保留用户之前手动填的
                        val mergedData = if (newResumeData?.links.isNullOrEmpty() && state.resumeData?.links?.isNotEmpty() == true) {
                            newResumeData?.copy(links = state.resumeData!!.links)
                        } else newResumeData

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
                                resumeData = mergedData,
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

    fun toggleVibeTemplate(use: Boolean) {
        _uiState.update { it.copy(useVibeTemplate = use) }
    }

    fun setPolishMode(mode: String) {
        _uiState.update { it.copy(polishMode = mode) }
    }

    fun toggleHistoryExpanded() {
        _uiState.update { it.copy(isHistoryExpanded = !it.isHistoryExpanded) }
    }

    fun updatePolishedResume(text: String) {
        _uiState.update { it.copy(polishedResume = text) }
    }

    fun exportPdf() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            val state = _uiState.value

            Log.i(TAG, "exportPdf (HTML): useVibeTemplate=${state.useVibeTemplate}")
            try {
                val resumeData = state.resumeData ?: ResumeData.fromPolishedText(state.polishedResume)
                val config = HtmlPdfExporter.HtmlConfig(useVibeTemplate = state.useVibeTemplate)
                val file = withContext(Dispatchers.Main) {
                    HtmlPdfExporter.exportPdf(application, resumeData, config)
                }
                Log.i(TAG, "PDF导出成功: ${file.absolutePath} (${file.length() / 1024}KB)")
                _uiState.update { it.copy(isExporting = false, exportFile = file) }
            } catch (e: Exception) {
                Log.e(TAG, "PDF导出失败: ${e.message}", e)
                _uiState.update { it.copy(isExporting = false) }
                Toast.makeText(application, "PDF导出失败: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
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

    // ═══════════════════════════════════════════════════════
    //  内容编辑 — UI 控制
    // ═══════════════════════════════════════════════════════

    fun setSelectedTab(tab: String) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun setExpandedSection(section: String?) {
        _uiState.update { it.copy(expandedSection = section) }
    }

    /** 发送 AI 聊天消息，自动修改简历 */
    fun sendAiChatMessage(instruction: String) {
        if (instruction.isBlank()) return
        viewModelScope.launch {
            val state = _uiState.value
            val userMsg = ChatMessage(role = "user", content = instruction)
            _uiState.update {
                it.copy(
                    aiChatMessages = it.aiChatMessages + userMsg,
                    isAiProcessing = true
                )
            }

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
                        val newResumeData = (if (polishResult.resumeJson.isNotBlank()) {
                            ResumeData.fromJsonString(polishResult.resumeJson)
                        } else {
                            ResumeData.fromPolishedText(polishResult.polishedResume)
                        })?.withAutoDetectedLinks()
                        // 如果 AI 没返回链接，保留用户之前手动填的
                        val mergedData = if (newResumeData?.links.isNullOrEmpty() && state.resumeData?.links?.isNotEmpty() == true) {
                            newResumeData?.copy(links = state.resumeData!!.links)
                        } else newResumeData

                        val replyContent = polishResult.optimizationNote.ifBlank {
                            "已根据你的需求完成修改 ✅"
                        }
                        val assistantMsg = ChatMessage(role = "assistant", content = replyContent)

                        _uiState.update {
                            it.copy(
                                resumeData = mergedData,
                                polishedResume = polishResult.polishedResume,
                                aiChatMessages = it.aiChatMessages + assistantMsg,
                                isAiProcessing = false,
                                showCompletionAnimation = true
                            )
                        }
                        Log.i(TAG, "AI聊天修改完成")
                    },
                    onFailure = { e ->
                        Log.e(TAG, "AI聊天修改失败: ${e.message}", e)
                        val errorMsg = ChatMessage(
                            role = "assistant",
                            content = "抱歉，修改失败：${e.localizedMessage}"
                        )
                        _uiState.update {
                            it.copy(
                                aiChatMessages = it.aiChatMessages + errorMsg,
                                isAiProcessing = false
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "AI聊天异常: ${e.message}", e)
                val errorMsg = ChatMessage(
                    role = "assistant",
                    content = "抱歉，出了点问题：${e.localizedMessage}"
                )
                _uiState.update {
                    it.copy(
                        aiChatMessages = it.aiChatMessages + errorMsg,
                        isAiProcessing = false
                    )
                }
            }
        }
    }

    fun dismissCompletionAnimation() {
        _uiState.update { it.copy(showCompletionAnimation = false) }
    }

    /** 整体替换 ResumeData（用于字段级编辑后提交） */
    fun updateResumeData(newData: ResumeData) {
        _uiState.update { it.copy(resumeData = newData) }
    }

    /** 更新个人信息字段（含照片 + 社交链接） */
    fun updatePersonalInfo(
        name: String? = null,
        position: String? = null,
        phone: String? = null,
        email: String? = null,
        photoUri: Uri? = null,
        links: List<ResumeData.SocialLink>? = null
    ) {
        // 如果提供了新照片，先转换成 Base64
        val newPhotoBase64 = if (photoUri != null) {
            uriToBase64(photoUri)
        } else null  // null = 保持原值

        _uiState.update { state ->
            val current = state.resumeData ?: return@update state
            state.copy(
                resumeData = current.copy(
                    name = name ?: current.name,
                    targetPosition = position ?: current.targetPosition,
                    contact = buildContactString(current.contact, phone, email),
                    photoBase64 = newPhotoBase64 ?: current.photoBase64,
                    links = links ?: current.links
                )
            )
        }
    }

    /** 将 content:// URI 转为 JPEG Base64 字符串（已压缩到 300px 宽） */
    private fun uriToBase64(uri: Uri): String? {
        return try {
            val inputStream = application.contentResolver.openInputStream(uri)
                ?: return null
            val sourceBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (sourceBitmap == null) return null

            // 缩放到最大 300px 宽，保持比例
            val maxWidth = 300
            val bitmap = if (sourceBitmap.width > maxWidth) {
                val ratio = maxWidth.toFloat() / sourceBitmap.width
                val newHeight = (sourceBitmap.height * ratio).toInt()
                Bitmap.createScaledBitmap(sourceBitmap, maxWidth, newHeight, true)
            } else sourceBitmap

            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val bytes = outputStream.toByteArray()
            outputStream.close()

            if (bitmap !== sourceBitmap) bitmap.recycle()
            sourceBitmap.recycle()

            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "照片转换失败: ${e.message}", e)
            null
        }
    }

    /** 更新某条工作经历 */
    fun updateExperience(index: Int, exp: ResumeData.Experience) {
        _uiState.update { state ->
            val current = state.resumeData ?: return@update state
            val list = current.experiences.toMutableList()
            if (index in list.indices) list[index] = exp
            state.copy(resumeData = current.copy(experiences = list))
        }
    }

    fun addExperience() {
        _uiState.update { state ->
            val current = state.resumeData ?: return@update state
            state.copy(resumeData = current.copy(
                experiences = current.experiences + ResumeData.Experience("", "", "", "")
            ))
        }
    }

    fun removeExperience(index: Int) {
        _uiState.update { state ->
            val current = state.resumeData ?: return@update state
            state.copy(resumeData = current.copy(
                experiences = current.experiences.toMutableList().also { if (index in it.indices) it.removeAt(index) }
            ))
        }
    }

    /** 更新某条教育经历 */
    fun updateEducation(index: Int, edu: ResumeData.Education) {
        _uiState.update { state ->
            val current = state.resumeData ?: return@update state
            val list = current.education.toMutableList()
            if (index in list.indices) list[index] = edu
            state.copy(resumeData = current.copy(education = list))
        }
    }

    fun addEducation() {
        _uiState.update { state ->
            val current = state.resumeData ?: return@update state
            state.copy(resumeData = current.copy(
                education = current.education + ResumeData.Education("", "", "")
            ))
        }
    }

    fun removeEducation(index: Int) {
        _uiState.update { state ->
            val current = state.resumeData ?: return@update state
            state.copy(resumeData = current.copy(
                education = current.education.toMutableList().also { if (index in it.indices) it.removeAt(index) }
            ))
        }
    }

    /** 更新某条项目经历 */
    fun updateProject(index: Int, proj: ResumeData.Project) {
        _uiState.update { state ->
            val current = state.resumeData ?: return@update state
            val list = current.projects.toMutableList()
            if (index in list.indices) list[index] = proj
            state.copy(resumeData = current.copy(projects = list))
        }
    }

    fun addProject() {
        _uiState.update { state ->
            val current = state.resumeData ?: return@update state
            state.copy(resumeData = current.copy(
                projects = current.projects + ResumeData.Project("", "", "", emptyList())
            ))
        }
    }

    fun removeProject(index: Int) {
        _uiState.update { state ->
            val current = state.resumeData ?: return@update state
            state.copy(resumeData = current.copy(
                projects = current.projects.toMutableList().also { if (index in it.indices) it.removeAt(index) }
            ))
        }
    }

    /** 整体替换技能列表 */
    fun updateSkills(skills: List<String>) {
        _uiState.update { state ->
            val current = state.resumeData ?: return@update state
            state.copy(resumeData = current.copy(skills = skills))
        }
    }

    fun addSkill(skill: String) {
        if (skill.isBlank()) return
        _uiState.update { state ->
            val current = state.resumeData ?: return@update state
            if (current.skills.contains(skill)) return@update state
            state.copy(resumeData = current.copy(skills = current.skills + skill))
        }
    }

    fun removeSkill(skill: String) {
        _uiState.update { state ->
            val current = state.resumeData ?: return@update state
            state.copy(resumeData = current.copy(skills = current.skills - skill))
        }
    }
}

// ═══════════════════════════════════════════════════════════
//  辅助函数
// ═══════════════════════════════════════════════════════════

/** 从现有 contact 字符串中提取并更新 phone / email */
private fun buildContactString(currentContact: String, phone: String?, email: String?): String {
    val parts = mutableListOf<String>()
    if (phone != null) parts.add(phone)
    else {
        val existingPhone = extractPhone(currentContact)
        if (existingPhone != null) parts.add(existingPhone)
    }
    if (email != null) parts.add(email)
    else {
        val existingEmail = extractEmail(currentContact)
        if (existingEmail != null) parts.add(existingEmail)
    }
    return parts.joinToString(" | ")
}

private fun extractPhone(text: String): String? {
    val match = Regex("""[\d\-\s()]{7,}""").find(text)
    return match?.value?.trim()
}

private fun extractEmail(text: String): String? {
    val match = Regex("""[\w.\-]+@[\w.\-]+\.\w+""").find(text)
    return match?.value?.trim()
}
