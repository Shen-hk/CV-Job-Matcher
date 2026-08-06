package com.example.tielink.ui.history

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tielink.data.local.AppPreferences
import com.example.tielink.data.repository.HistoryRepository
import com.example.tielink.domain.model.HistoryItem
import com.example.tielink.domain.model.displayTitle
import com.example.tielink.domain.model.isAgentChat
import com.example.tielink.domain.model.matches
import com.example.tielink.domain.model.previewText
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.DecimalFormat
import javax.inject.Inject

enum class HistoryDateFilter(val label: String, val days: Int?) {
    ALL("全部", null),
    DAYS_7("7天内", 7),
    DAYS_30("30天内", 30)
}

data class HistoryUiState(
    val items: List<HistoryItem> = emptyList(),
    val filteredItems: List<HistoryItem> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val bulkMode: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
    val dateFilter: HistoryDateFilter = HistoryDateFilter.ALL,
    val modelSummary: String = "模型未配置",
    val syncSummary: String = "仅本地历史",
    val storageSummary: String = "0 KB"
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val moshi: Moshi,
    private val appPreferences: AppPreferences,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        observeHistory()
        observeModelSummary()
    }

    private fun observeHistory() {
        viewModelScope.launch {
            historyRepository.getAllFlow().collect { entities ->
                val type = Types.newParameterizedType(List::class.java, String::class.java)
                val items = entities.map { entity ->
                    val skills: List<String> = try {
                        moshi.adapter<List<String>>(type).fromJson(entity.jdSkills) ?: emptyList()
                    } catch (_: Exception) {
                        emptyList()
                    }

                    HistoryItem(
                        id = entity.id,
                        createdAt = entity.createdAt,
                        updatedAt = entity.updatedAt,
                        jdTitle = entity.jdTitle,
                        customTitle = entity.customTitle,
                        jdRawText = entity.jdRawText,
                        originalResume = entity.originalResume,
                        polishedResume = entity.polishedResume,
                        jdSkills = skills,
                        optimizationNote = entity.matchNote,
                        isPinned = entity.isPinned,
                        sourceType = entity.sourceType,
                        chatDraftJson = entity.resumeJson
                    )
                }
                _uiState.update {
                    val selectedIds = it.selectedIds.intersect(items.map { item -> item.id }.toSet())
                    val next = it.copy(items = items, selectedIds = selectedIds, isLoading = false)
                    next.withFiltering()
                }
            }
        }
    }

    private fun observeModelSummary() {
        viewModelScope.launch {
            combine(
                appPreferences.getAiProviderFlow(),
                appPreferences.getActiveModelNameFlow(),
                appPreferences.getModelFlow(),
                appPreferences.getOllamaModelFlow()
            ) { provider, activeModel, deepseekModel, ollamaModel ->
                when (provider) {
                    "ollama" -> "当前模型：${activeModel ?: ollamaModel}"
                    "local" -> "当前模型：${activeModel ?: "本地模型"}"
                    else -> "当前模型：${activeModel ?: deepseekModel}"
                }
            }.collect { summary ->
                _uiState.update {
                    val next = it.copy(modelSummary = summary)
                    next.withFiltering()
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query).withFiltering() }
    }

    fun setDateFilter(filter: HistoryDateFilter) {
        _uiState.update { it.copy(dateFilter = filter).withFiltering() }
    }

    fun setBulkMode(enabled: Boolean) {
        _uiState.update {
            it.copy(
                bulkMode = enabled,
                selectedIds = if (enabled) it.selectedIds else emptySet()
            ).withFiltering()
        }
    }

    fun toggleSelection(id: Long) {
        _uiState.update {
            val next = it.selectedIds.toMutableSet()
            if (!next.add(id)) next.remove(id)
            it.copy(selectedIds = next).withFiltering()
        }
    }

    fun selectAllFiltered() {
        _uiState.update { it.copy(selectedIds = it.filteredItems.map { item -> item.id }.toSet()).withFiltering() }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet()).withFiltering() }
    }

    fun renameItem(id: Long, title: String) {
        viewModelScope.launch {
            historyRepository.rename(id, title.trim())
        }
    }

    fun deleteItem(id: Long) {
        viewModelScope.launch {
            historyRepository.deleteById(id)
        }
    }

    fun deleteSelected() {
        val ids = _uiState.value.selectedIds.toList()
        viewModelScope.launch {
            historyRepository.deleteByIds(ids)
            _uiState.update { it.copy(selectedIds = emptySet(), bulkMode = false).withFiltering() }
        }
    }

    fun setPinned(id: Long, pinned: Boolean) {
        viewModelScope.launch {
            historyRepository.updatePinned(id, pinned)
        }
    }

    fun setPinnedSelected(pinned: Boolean) {
        val ids = _uiState.value.selectedIds.toList()
        viewModelScope.launch {
            historyRepository.updatePinnedByIds(ids, pinned)
            _uiState.update { it.copy(selectedIds = emptySet(), bulkMode = false).withFiltering() }
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            historyRepository.deleteAll()
            _uiState.update { it.copy(selectedIds = emptySet(), bulkMode = false).withFiltering() }
        }
    }

    fun exportItem(id: Long) {
        val item = _uiState.value.items.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            val exportDir = File(appContext.cacheDir, "history_exports").apply { mkdirs() }
            val exportFile = File(exportDir, buildExportName(item))
            exportFile.writeText(buildExportContent(item))

            val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", exportFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, item.displayTitle)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(Intent.createChooser(intent, "导出对话记录").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    fun buildBranchPrompt(item: HistoryItem): String {
        return "基于《${item.displayTitle}》新建分支会话，请继续围绕这份润色记录给我建议。\n\n摘要：${item.previewText}"
    }

    private fun HistoryUiState.withFiltering(): HistoryUiState {
        val now = System.currentTimeMillis()
        val filtered = items.filter { item ->
            val matchesQuery = item.matches(searchQuery)
            val matchesDate = when (dateFilter.days) {
                null -> true
                else -> now - item.updatedAt <= dateFilter.days * 24L * 60L * 60L * 1000L
            }
            matchesQuery && matchesDate
        }
        return copy(
            filteredItems = filtered,
            storageSummary = formatStorage(items.sumOf { estimateItemBytes(it) })
        )
    }

    private fun estimateItemBytes(item: HistoryItem): Long {
        return (
            item.displayTitle.length +
                item.jdRawText.length +
                item.originalResume.length +
                item.polishedResume.length +
                item.optimizationNote.length +
                item.chatDraftJson.length +
                item.jdSkills.sumOf { it.length }
            ).toLong() * 2L
    }

    private fun formatStorage(bytes: Long): String {
        if (bytes < 1024) return "${bytes} B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "${DecimalFormat("#.0").format(kb)} KB"
        return "${DecimalFormat("#.0").format(kb / 1024.0)} MB"
    }

    private fun buildExportName(item: HistoryItem): String {
        val safeTitle = item.displayTitle.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        return "${safeTitle.ifBlank { "history_${item.id}" }}.txt"
    }

    private fun buildExportContent(item: HistoryItem): String {
        if (item.isAgentChat) {
            return buildString {
                appendLine(item.displayTitle)
                appendLine("更新时间：${item.updatedAt}")
                appendLine()
                appendLine(item.polishedResume)
            }
        }
        return buildString {
            appendLine(item.displayTitle)
            appendLine("更新时间：${item.updatedAt}")
            appendLine()
            appendLine("优化说明")
            appendLine(item.optimizationNote)
            appendLine()
            appendLine("JD 标题")
            appendLine(item.jdTitle)
            appendLine()
            appendLine("JD 原文")
            appendLine(item.jdRawText)
            appendLine()
            appendLine("优化后简历")
            appendLine(item.polishedResume)
        }
    }
}
