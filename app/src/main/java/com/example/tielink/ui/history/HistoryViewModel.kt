package com.example.tielink.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tielink.data.repository.HistoryRepository
import com.example.tielink.domain.model.HistoryItem
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val items: List<HistoryItem> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val moshi: Moshi
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            historyRepository.getAllFlow().collect { entities ->
                val type = Types.newParameterizedType(List::class.java, String::class.java)
                val items = entities.map { entity ->
                    val skills: List<String> = try {
                        moshi.adapter<List<String>>(type).fromJson(entity.jdSkills) ?: emptyList()
                    } catch (_: Exception) { emptyList() }

                    HistoryItem(
                        id = entity.id,
                        createdAt = entity.createdAt,
                        jdTitle = entity.jdTitle,
                        jdRawText = entity.jdRawText,
                        originalResume = entity.originalResume,
                        polishedResume = entity.polishedResume,
                        jdSkills = skills,
                        optimizationNote = entity.matchNote
                    )
                }
                _uiState.update { it.copy(items = items, isLoading = false) }
            }
        }
    }

    fun deleteItem(id: Long) {
        viewModelScope.launch {
            historyRepository.deleteById(id)
        }
    }
}
