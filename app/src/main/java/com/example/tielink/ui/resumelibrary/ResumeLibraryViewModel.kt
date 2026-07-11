package com.example.tielink.ui.resumelibrary

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tielink.data.local.AppPreferences
import com.example.tielink.data.local.db.dao.ResumeVersionDao
import com.example.tielink.data.local.db.entity.HistoryEntity
import com.example.tielink.data.local.db.entity.ResumeVersionEntity
import com.example.tielink.data.repository.HistoryRepository
import com.example.tielink.data.repository.ResumeVersionRepository
import com.example.tielink.domain.model.ResumeLibraryItem
import com.example.tielink.domain.model.ResumeVersion
import com.example.tielink.util.FileParser
import com.example.tielink.util.OriginalResumeFileStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
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

data class ResumeLibraryUiState(
    val items: List<ResumeLibraryItem> = emptyList(),
    val isLoading: Boolean = true,
    val isUploading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ResumeLibraryViewModel @Inject constructor(
    private val historyDao: com.example.tielink.data.local.db.dao.HistoryDao,
    private val resumeVersionDao: ResumeVersionDao,
    private val historyRepository: HistoryRepository,
    private val resumeVersionRepository: ResumeVersionRepository,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResumeLibraryUiState())
    val uiState: StateFlow<ResumeLibraryUiState> = _uiState.asStateFlow()

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    init {
        viewModelScope.launch {
            refreshItems()
        }
    }

    private fun historyTitle(jdTitle: String, polishedResume: String): String {
        if (jdTitle.isNotBlank()) return "$jdTitle · 润色记录"
        val firstLine = polishedResume.lines().firstOrNull { it.isNotBlank() }?.take(30) ?: "简历"
        return "$firstLine..."
    }

    fun selectResumeForOptimize(versionId: Long) {
        viewModelScope.launch {
            runCatching {
                resumeVersionRepository.setActive(versionId)
                appPreferences.setResumeOptimizeContinue(true)
                refreshItems()
            }.onFailure { error ->
                _uiState.update { it.copy(error = "选择简历失败: ${error.message}") }
            }
        }
    }

    fun activateVersion(versionId: Long) {
        viewModelScope.launch {
            runCatching {
                resumeVersionRepository.setActive(versionId)
                refreshItems()
            }.onFailure { error ->
                _uiState.update { it.copy(error = "设为当前失败: ${error.message}") }
            }
        }
    }

    fun renameItem(item: ResumeLibraryItem, newTitle: String) {
        val title = newTitle.trim()
        if (title.isBlank()) {
            _uiState.update { it.copy(error = "名称不能为空") }
            return
        }

        viewModelScope.launch {
            runCatching {
                if (item.type == "history") {
                    historyRepository.rename(item.id, title)
                } else {
                    val version = resumeVersionRepository.getById(item.id)
                        ?: error("简历版本不存在")
                    resumeVersionRepository.save(
                        version.copy(
                            name = title,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
                refreshItems()
            }.onFailure { error ->
                _uiState.update { it.copy(error = "重命名失败: ${error.message}") }
            }
        }
    }

    fun deleteItem(item: ResumeLibraryItem) {
        viewModelScope.launch {
            runCatching {
                if (item.type == "history") {
                    historyRepository.deleteById(item.id)
                } else {
                    val version = resumeVersionRepository.getById(item.id)
                        ?: error("简历版本不存在")
                    val isActive = version.isActive
                    resumeVersionRepository.delete(version)
                    if (isActive) {
                        val remaining = resumeVersionRepository.getAll()
                        if (remaining.isNotEmpty() && remaining.none { it.isActive }) {
                            resumeVersionRepository.setActive(remaining.first().id)
                        }
                    }
                }
                refreshItems()
            }.onFailure { error ->
                _uiState.update { it.copy(error = "删除失败: ${error.message}") }
            }
        }
    }

    fun uploadResume(
        context: Context,
        uri: Uri,
        mimeType: String?,
        fileName: String?,
        onUploaded: (Long) -> Unit = {}
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, error = null) }

            val storedFile = withContext(Dispatchers.IO) {
                OriginalResumeFileStore.copyFromUri(
                    context,
                    uri,
                    fileName ?: "我的简历"
                )
            }.getOrElse { error ->
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        error = "上传失败: ${error.message}"
                    )
                }
                return@launch
            }

            val extractedText = withContext(Dispatchers.IO) {
                FileParser.extractTextFromFile(
                    context = context,
                    filePath = storedFile.absolutePath,
                    mimeType = mimeType
                )
            }.getOrElse { error ->
                storedFile.delete()
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        error = "简历内容提取失败: ${error.message}"
                    )
                }
                return@launch
            }.trim()

            if (extractedText.isBlank()) {
                storedFile.delete()
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        error = "没有从简历中提取到可用内容，请检查文件是否清晰"
                    )
                }
                return@launch
            }

            val versionId = resumeVersionRepository.insertAndActivate(
                ResumeVersion(
                    name = fileName?.substringBeforeLast(".").orEmpty()
                        .ifBlank { "我的简历" },
                    rawText = extractedText,
                    cleanedText = extractedText,
                    originalFilePath = storedFile.absolutePath,
                    originalMimeType = mimeType.orEmpty(),
                    isPolished = false
                )
            )
            refreshItems()
            _uiState.update { it.copy(isUploading = false) }
            onUploaded(versionId)
        }
    }

    private suspend fun refreshItems() {
        val historyItems: List<HistoryEntity> = historyDao.getAllFlow().first()
        val versionItems: List<ResumeVersionEntity> = resumeVersionDao.getAll()

        val listType = Types.newParameterizedType(List::class.java, String::class.java)
        val stringListAdapter = moshi.adapter<List<String>>(listType)

        val merged = buildList {
            historyItems.forEach { h ->
                add(
                    ResumeLibraryItem(
                        id = h.id,
                        type = "history",
                        title = h.customTitle.ifBlank { historyTitle(h.jdTitle, h.polishedResume) },
                        subtitle = if (h.jdTitle.isNotBlank()) "关联JD：${h.jdTitle}" else "未关联JD",
                        matchScore = h.matchScore,
                        createdAt = h.createdAt
                    )
                )
            }
            versionItems.forEach { v ->
                val tagList = try {
                    stringListAdapter.fromJson(v.tags) ?: emptyList()
                } catch (_: Exception) {
                    emptyList<String>()
                }
                add(
                    ResumeLibraryItem(
                        id = v.id,
                        type = "version",
                        title = v.name,
                        subtitle = if (!v.isPolished && v.originalFilePath.isNotBlank()) {
                            val format = when {
                                v.originalMimeType.contains("pdf", ignoreCase = true) -> "PDF"
                                v.originalMimeType.contains("word", ignoreCase = true) -> "DOCX"
                                else -> "文件"
                            }
                            "原始 $format · 未润色"
                        } else {
                            tagList.joinToString(" · ")
                        },
                        matchScore = v.matchScore.toInt(),
                        createdAt = v.createdAt,
                        isActive = v.isActive
                    )
                )
            }
        }.sortedByDescending { it.createdAt }

        _uiState.update { it.copy(items = merged, isLoading = false, error = null) }
    }
}
