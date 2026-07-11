package com.example.tielink.ui.jdlist

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tielink.data.local.AppPreferences
import com.example.tielink.data.local.db.entity.JdLibraryEntity
import com.example.tielink.data.remote.AiProviderManager
import com.example.tielink.data.remote.LlmRequest
import com.example.tielink.data.remote.dto.Message
import com.example.tielink.data.repository.AgentContextRepository
import com.example.tielink.data.repository.JdLibraryRepository
import com.example.tielink.util.FileParser
import com.squareup.moshi.Moshi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class JdListUiState(
    val jdList: List<JdLibraryEntity> = emptyList(),
    val isLoading: Boolean = true,
    val isProcessing: Boolean = false,
    val error: String? = null,
    val currentJdId: Long? = null
)

@HiltViewModel
class JdListViewModel @Inject constructor(
    private val jdLibraryRepository: JdLibraryRepository,
    private val aiProviderManager: AiProviderManager,
    private val moshi: Moshi,
    private val agentContextRepository: AgentContextRepository,
    private val appPreferences: AppPreferences
) : ViewModel() {
    companion object { private const val TAG = "JdListVM" }

    private val _uiState = MutableStateFlow(JdListUiState())
    val uiState: StateFlow<JdListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            jdLibraryRepository.getAllFlow().collect { list ->
                _uiState.update { it.copy(jdList = list, isLoading = false) }
            }
        }
        viewModelScope.launch {
            val currentJdId = agentContextRepository.getAgentContext().currentJdId
            _uiState.update { it.copy(currentJdId = currentJdId) }
        }
    }

    fun deleteJd(entity: JdLibraryEntity) {
        viewModelScope.launch {
            jdLibraryRepository.delete(entity)
        }
    }

    fun addFromText(rawText: String) {
        if (rawText.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            try {
                val jd = withContext(Dispatchers.IO) { aiExtractJd(rawText) }
                val entity = JdLibraryEntity(
                    companyName = jd.company,
                    positionName = jd.position,
                    salary = jd.salary,
                    rawText = rawText,
                    skills = jd.skills.joinToString(","),
                    sourceType = "manual"
                )
                jdLibraryRepository.insert(entity)
            } catch (e: Exception) {
                Log.e(TAG, "提取 JD 失败: ${e.message}", e)
                // fallback: save raw text only
                jdLibraryRepository.insert(
                    JdLibraryEntity(rawText = rawText, sourceType = "manual")
                )
            }
            _uiState.update { it.copy(isProcessing = false) }
        }
    }

    fun addFromImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null) }
            try {
                // Step 1: OCR
                val result = withContext(Dispatchers.IO) { FileParser.extractText(context, uri, "image/*") }
                val text = result.getOrThrow()
                if (text.isBlank()) {
                    _uiState.update { it.copy(isProcessing = false, error = "未能从图片中识别文字") }
                    return@launch
                }
                Log.d(TAG, "OCR 识别成功: ${text.take(100)}...")

                // Step 2: AI 提取
                try {
                    val jd = withContext(Dispatchers.IO) { aiExtractJd(text) }
                    val entity = JdLibraryEntity(
                        companyName = jd.company,
                        positionName = jd.position,
                        salary = jd.salary,
                        rawText = text,
                        skills = jd.skills.joinToString(","),
                        sourceType = "ocr"
                    )
                    jdLibraryRepository.insert(entity)
                } catch (e: Exception) {
                    Log.e(TAG, "AI 提取失败，保存原始 OCR 文本: ${e.message}", e)
                    jdLibraryRepository.insert(
                        JdLibraryEntity(rawText = text, sourceType = "ocr")
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "OCR 识别失败: ${e.message}", e)
                _uiState.update { it.copy(isProcessing = false, error = "识别失败: ${e.localizedMessage}") }
                return@launch
            }
            _uiState.update { it.copy(isProcessing = false) }
        }
    }

    fun selectJdForAgent(jdId: Long, onSelected: () -> Unit = {}) {
        viewModelScope.launch {
            val jd = jdLibraryRepository.getById(jdId) ?: return@launch
            agentContextRepository.updateAgentContext(
                currentJdId = jd.id,
                currentJdText = jd.rawText,
                currentJdCompany = jd.companyName
            )
            appPreferences.setCachedJdRawText(jd.rawText)
            appPreferences.setCachedJdStructuredJson(jd.structuredJson)
            appPreferences.setCachedJdCompanyName(jd.companyName)
            _uiState.update { it.copy(currentJdId = jd.id) }
            onSelected()
        }
    }

    private suspend fun aiExtractJd(text: String): JdExtractResult {
        val prompt = """你是一位招聘专家。请从以下岗位描述中提取信息，只返回JSON格式：
{"company":"公司名（如未提及则为空字符串）","position":"职位名称（如未提及则为空字符串）","salary":"薪资范围（如20k-40k，未提及则为空字符串）","skills":["技能1","技能2","技能3"]}"""
        val request = LlmRequest(
            messages = listOf(
                Message("system", prompt),
                Message("user", "请提取: ${text.take(2000)}")
            ),
            temperature = 0.3,
            maxTokens = 300
        )
        val response = aiProviderManager.chatWithFallback(request)
        val json = response.content
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()

        Log.d(TAG, "AI 提取原始响应: ${response.content.take(200)}")
        Log.d(TAG, "清理后 JSON: $json")

        return try {
            val adapter = moshi.adapter(JdExtractResult::class.java)
            val result = adapter.fromJson(json)
            if (result != null) {
                Log.i(TAG, "AI 提取成功: company=${result.company}, position=${result.position}, salary=${result.salary}, skills=${result.skills}")
                result
            } else {
                Log.w(TAG, "Moshi 解析返回 null，JSON 为: $json")
                JdExtractResult()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Moshi 解析失败: ${e.message}，JSON 为: $json", e)
            JdExtractResult()
        }
    }
}

@com.squareup.moshi.JsonClass(generateAdapter = false)
data class JdExtractResult(
    val company: String = "",
    val position: String = "",
    val salary: String = "",
    val skills: List<String> = emptyList()
)
