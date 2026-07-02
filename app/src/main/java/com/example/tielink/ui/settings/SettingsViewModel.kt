package com.example.tielink.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tielink.data.local.AppPreferences
import com.example.tielink.data.remote.DeepSeekApiServiceFactory
import com.example.tielink.data.remote.dto.DeepSeekRequest
import com.example.tielink.data.remote.dto.Message
import com.example.tielink.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val apiKey: String = "",
    val model: String = AppPreferences.DEFAULT_MODEL,
    val baseUrl: String = AppPreferences.DEFAULT_BASE_URL,
    val isTesting: Boolean = false,
    val testResult: String? = null,
    val isSaved: Boolean = false,
    val activeProviderName: String? = null,
    val activeModelName: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val apiServiceFactory: DeepSeekApiServiceFactory,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        val snapshot = appPreferences.snapshot()
        _uiState.update {
            it.copy(
                apiKey = snapshot.apiKey,
                model = snapshot.model,
                baseUrl = snapshot.baseUrl,
                activeProviderName = when (snapshot.aiProvider) {
                    "ollama" -> "Ollama"
                    "local" -> "本地嵌入模型"
                    else -> "DeepSeek"
                },
                activeModelName = when (snapshot.aiProvider) {
                    "ollama" -> snapshot.ollamaModel
                    "local" -> "本地嵌入模型"
                    else -> snapshot.model
                }
            )
        }
    }

    fun updateApiKey(value: String) {
        _uiState.update { it.copy(apiKey = value, isSaved = false) }
    }

    fun updateModel(value: String) {
        _uiState.update { it.copy(model = value, isSaved = false) }
    }

    fun updateBaseUrl(value: String) {
        _uiState.update { it.copy(baseUrl = value, isSaved = false) }
    }

    fun saveSettings() {
        viewModelScope.launch {
            val state = _uiState.value
            settingsRepository.setApiKey(state.apiKey)
            settingsRepository.setModel(state.model.ifBlank { AppPreferences.DEFAULT_MODEL })
            settingsRepository.setBaseUrl(state.baseUrl.ifBlank { AppPreferences.DEFAULT_BASE_URL })
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true, testResult = null) }
            try {
                val request = DeepSeekRequest(
                    messages = listOf(
                        Message(role = "user", content = "Hello")
                    ),
                    maxTokens = 10
                )
                val response = apiServiceFactory.create().chatCompletion(request)
                if (response.choices.isNotEmpty()) {
                    _uiState.update { it.copy(testResult = "", isTesting = false) }
                } else {
                    _uiState.update { it.copy(testResult = "杩斿洖缁撴灉涓虹┖", isTesting = false) }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        testResult = "杩炴帴澶辫触: ${e.localizedMessage ?: "鏈煡閿欒"}",
                        isTesting = false
                    )
                }
            }
        }
    }
}

