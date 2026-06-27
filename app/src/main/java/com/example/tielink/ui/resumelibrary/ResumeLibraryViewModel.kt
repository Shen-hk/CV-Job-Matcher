package com.example.tielink.ui.resumelibrary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tielink.data.local.db.entity.HistoryEntity
import com.example.tielink.data.local.db.entity.ResumeVersionEntity
import com.example.tielink.data.local.db.dao.ResumeVersionDao
import com.example.tielink.domain.model.ResumeLibraryItem
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.squareup.moshi.Types
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ResumeLibraryUiState(
    val items: List<ResumeLibraryItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class ResumeLibraryViewModel @Inject constructor(
    private val historyDao: com.example.tielink.data.local.db.dao.HistoryDao,
    private val resumeVersionDao: ResumeVersionDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResumeLibraryUiState())
    val uiState: StateFlow<ResumeLibraryUiState> = _uiState.asStateFlow()

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    init {
        viewModelScope.launch {
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
                            title = historyTitle(h.jdTitle, h.polishedResume),
                            subtitle = if (h.jdTitle.isNotBlank()) "关联JD：${h.jdTitle}" else "未关联JD",
                            matchScore = h.matchScore,
                            createdAt = h.createdAt
                        )
                    )
                }
                versionItems.forEach { v ->
                    val tagList = try { stringListAdapter.fromJson(v.tags) ?: emptyList() } catch (_: Exception) { emptyList<String>() }
                    add(
                        ResumeLibraryItem(
                            id = v.id,
                            type = "version",
                            title = v.name,
                            subtitle = tagList.joinToString(" · "),
                            matchScore = v.matchScore.toInt(),
                            createdAt = v.createdAt
                        )
                    )
                }
            }.sortedByDescending { it.createdAt }

            _uiState.update { it.copy(items = merged, isLoading = false) }
        }
    }

    private fun historyTitle(jdTitle: String, polishedResume: String): String {
        if (jdTitle.isNotBlank()) return "${jdTitle} · 润色版"
        val firstLine = polishedResume.lines().firstOrNull { it.isNotBlank() }?.take(30) ?: "简历"
        return "$firstLine..."
    }
}
