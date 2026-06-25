package com.example.tielink.ui.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tielink.data.repository.TrackingRepository
import com.example.tielink.data.repository.TrackingItem
import com.example.tielink.data.repository.TimelineEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrackingUiState(
    val items: List<TrackingItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val activeFilter: String? = null, // null = all
    val isAddingNew: Boolean = false,
    val newCompany: String = "",
    val newPosition: String = "",
    val newStatus: String = "已投"
)

val STATUS_OPTIONS = listOf("已投", "简历过筛", "待面试", "已面试", "已offer", "已拒")
val STATUS_COLORS = mapOf(
    "已投" to 0xFF2196F3,
    "简历过筛" to 0xFF4CAF50,
    "待面试" to 0xFFFF9800,
    "已面试" to 0xFF9C27B0,
    "已offer" to 0xFF00BCD4,
    "已拒" to 0xFFF44336
)

@HiltViewModel
class TrackingViewModel @Inject constructor(
    private val trackingRepository: TrackingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrackingUiState())
    val uiState: StateFlow<TrackingUiState> = _uiState.asStateFlow()

    init {
        // Observe tracking items
        viewModelScope.launch {
            trackingRepository.getAllFlow().collect { items ->
                _uiState.update { it.copy(items = items) }
            }
        }
    }

    fun setFilter(status: String?) {
        _uiState.update { it.copy(activeFilter = status) }
    }

    val filteredItems: List<TrackingItem>
        get() {
            val items = _uiState.value.items
            val filter = _uiState.value.activeFilter
            return if (filter == null) items else items.filter { it.status == filter }
        }

    fun toggleAddNew() {
        _uiState.update {
            it.copy(isAddingNew = !it.isAddingNew, newCompany = "", newPosition = "")
        }
    }

    fun updateNewCompany(name: String) {
        _uiState.update { it.copy(newCompany = name) }
    }

    fun updateNewPosition(name: String) {
        _uiState.update { it.copy(newPosition = name) }
    }

    fun updateNewStatus(status: String) {
        _uiState.update { it.copy(newStatus = status) }
    }

    fun addTracking() {
        val state = _uiState.value
        if (state.newCompany.isBlank() || state.newPosition.isBlank()) return

        viewModelScope.launch {
            val item = TrackingItem(
                companyName = state.newCompany,
                positionName = state.newPosition,
                status = state.newStatus
            )
            trackingRepository.insert(item)
            _uiState.update {
                it.copy(isAddingNew = false, newCompany = "", newPosition = "", newStatus = "已投")
            }
        }
    }

    fun updateStatus(id: Long, newStatus: String) {
        viewModelScope.launch {
            trackingRepository.updateStatus(id, newStatus)
        }
    }

    fun deleteItem(id: Long) {
        viewModelScope.launch {
            val item = _uiState.value.items.firstOrNull { it.id == id } ?: return@launch
            trackingRepository.delete(item)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
