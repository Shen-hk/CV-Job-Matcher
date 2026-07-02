package com.example.tielink.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tielink.data.local.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelConfigUiState(
    val aiProvider: String = "deepseek",
    val deepSeekApiKey: String = "",
    val deepSeekBaseUrl: String = AppPreferences.DEFAULT_BASE_URL,
    val deepSeekModel: String = AppPreferences.DEFAULT_MODEL,
    val ollamaBaseUrl: String = "http://10.0.2.2:11434",
    val ollamaModel: String = "qwen2.5:7b",
    val ollamaEmbedModel: String = "nomic-embed-text",
    val isSaving: Boolean = false,
    val saveMessage: String? = null
)

@HiltViewModel
class ModelConfigViewModel @Inject constructor(
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelConfigUiState())
    val uiState: StateFlow<ModelConfigUiState> = _uiState.asStateFlow()

    init {
        val snapshot = appPreferences.snapshot()
        _uiState.update {
            it.copy(
                aiProvider = snapshot.aiProvider,
                deepSeekApiKey = snapshot.apiKey,
                deepSeekBaseUrl = snapshot.baseUrl,
                deepSeekModel = snapshot.model,
                ollamaBaseUrl = snapshot.ollamaBaseUrl,
                ollamaModel = snapshot.ollamaModel,
                ollamaEmbedModel = snapshot.ollamaEmbedModel
            )
        }
    }

    fun updateAiProvider(value: String) {
        _uiState.update { it.copy(aiProvider = value, saveMessage = null) }
    }

    fun updateDeepSeekApiKey(value: String) {
        _uiState.update { it.copy(deepSeekApiKey = value, saveMessage = null) }
    }

    fun updateDeepSeekBaseUrl(value: String) {
        _uiState.update { it.copy(deepSeekBaseUrl = value, saveMessage = null) }
    }

    fun updateDeepSeekModel(value: String) {
        _uiState.update { it.copy(deepSeekModel = value, saveMessage = null) }
    }

    fun updateOllamaBaseUrl(value: String) {
        _uiState.update { it.copy(ollamaBaseUrl = value, saveMessage = null) }
    }

    fun updateOllamaModel(value: String) {
        _uiState.update { it.copy(ollamaModel = value, saveMessage = null) }
    }

    fun updateOllamaEmbedModel(value: String) {
        _uiState.update { it.copy(ollamaEmbedModel = value, saveMessage = null) }
    }

    fun saveConfig() {
        viewModelScope.launch {
            val state = _uiState.value
            _uiState.update { it.copy(isSaving = true, saveMessage = null) }

            val provider = when (state.aiProvider) {
                "ollama", "local" -> state.aiProvider
                else -> "deepseek"
            }

            appPreferences.setAiProvider(provider)
            appPreferences.setApiKey(state.deepSeekApiKey.trim())
            appPreferences.setBaseUrl(state.deepSeekBaseUrl.trim().ifBlank { AppPreferences.DEFAULT_BASE_URL })
            appPreferences.setModel(state.deepSeekModel.trim().ifBlank { AppPreferences.DEFAULT_MODEL })
            appPreferences.setOllamaBaseUrl(state.ollamaBaseUrl.trim().ifBlank { "http://10.0.2.2:11434" })
            appPreferences.setOllamaModel(state.ollamaModel.trim().ifBlank { "qwen2.5:7b" })
            appPreferences.setOllamaEmbedModel(state.ollamaEmbedModel.trim().ifBlank { "nomic-embed-text" })

            _uiState.update {
                it.copy(
                    isSaving = false,
                    saveMessage = when (provider) {
                        "ollama" -> "已保存并切换到 Ollama"
                        "local" -> "已保存并切换到本地模式"
                        else -> "已保存并切换到 DeepSeek"
                    }
                )
            }
        }
    }
}
