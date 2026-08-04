package com.example.tielink.ui.jdlist

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tielink.data.repository.AgentContextRepository
import com.example.tielink.domain.context.CurrentJobContext
import com.example.tielink.domain.model.SavedJobDescription
import com.example.tielink.domain.usecase.JdLibraryUseCase
import com.example.tielink.util.FileParser
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
    val jdList: List<SavedJobDescription> = emptyList(),
    val isLoading: Boolean = true,
    val isProcessing: Boolean = false,
    val error: String? = null,
    val currentJdId: Long? = null
)

@HiltViewModel
class JdListViewModel @Inject constructor(
    private val jdLibraryUseCase: JdLibraryUseCase,
    private val agentContextRepository: AgentContextRepository,
    private val currentJobContext: CurrentJobContext
) : ViewModel() {
    private companion object {
        const val TAG = "JdListVM"
    }

    private val _uiState = MutableStateFlow(JdListUiState())
    val uiState: StateFlow<JdListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            jdLibraryUseCase.observeAll().collect { list ->
                _uiState.update { it.copy(jdList = list, isLoading = false) }
            }
        }
        viewModelScope.launch {
            val currentJdId = agentContextRepository.getAgentContext().currentJdId
            _uiState.update { it.copy(currentJdId = currentJdId) }
        }
    }

    fun deleteJd(jd: SavedJobDescription) {
        viewModelScope.launch { jdLibraryUseCase.delete(jd.id) }
    }

    fun addFromText(rawText: String) {
        if (rawText.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null) }
            runCatching { jdLibraryUseCase.saveFromText(rawText, sourceType = "manual") }
                .onFailure { error ->
                    Log.e(TAG, "Unable to save JD", error)
                    _uiState.update { it.copy(error = "保存职位描述失败：${error.localizedMessage}") }
                }
            _uiState.update { it.copy(isProcessing = false) }
        }
    }

    fun addFromImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null) }
            try {
                val text = withContext(Dispatchers.IO) {
                    FileParser.extractText(context, uri, "image/*").getOrThrow()
                }
                if (text.isBlank()) {
                    _uiState.update { it.copy(error = "未能从图片中识别文字") }
                } else {
                    jdLibraryUseCase.saveFromText(text, sourceType = "ocr")
                }
            } catch (error: Exception) {
                Log.e(TAG, "Unable to import JD image", error)
                _uiState.update { it.copy(error = "识别或保存失败：${error.localizedMessage}") }
            }
            _uiState.update { it.copy(isProcessing = false) }
        }
    }

    fun selectJdForAgent(jdId: Long, onSelected: () -> Unit = {}) {
        viewModelScope.launch {
            val jd = jdLibraryUseCase.getById(jdId) ?: return@launch
            agentContextRepository.updateAgentContext(
                currentJdId = jd.id,
                currentJdText = jd.rawText,
                currentJdCompany = jd.companyName
            )
            currentJobContext.setJd(jd.rawText, jd.structuredJson, jd.companyName)
            _uiState.update { it.copy(currentJdId = jd.id) }
            onSelected()
        }
    }
}
