package com.example.tielink.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tielink.data.local.AppPreferences
import com.example.tielink.data.remote.DeepSeekApiService
import com.example.tielink.data.remote.dto.DeepSeekRequest
import com.example.tielink.data.remote.dto.Message
import com.example.tielink.data.repository.ProviderRepository
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
    // Active model info (from Room Provider layer)
    val activeProviderName: String? = null,
    val activeModelName: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val apiService: DeepSeekApiService,
    private val appPreferences: AppPreferences,
    private val providerRepository: ProviderRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val key = settingsRepository.getApiKey()
            val model = settingsRepository.getModel()
            val url = settingsRepository.getBaseUrl()
            _uiState.update {
                it.copy(apiKey = key, model = model, baseUrl = url)
            }
            // Load active provider/model display info
            loadActiveModelInfo()
        }
    }

    private suspend fun loadActiveModelInfo() {
        val activeProviderId = appPreferences.getActiveProviderId()
        val activeModelName = appPreferences.getActiveModelName()
        val providerName = if (activeProviderId != null) {
            providerRepository.getProviderById(activeProviderId)?.name
        } else null
        _uiState.update {
            it.copy(
                activeProviderName = providerName,
                activeModelName = activeModelName
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
                val response = apiService.chatCompletion(request)
                if (response.choices.isNotEmpty()) {
                    _uiState.update { it.copy(testResult = "", isTesting = false) }
                } else {
                    _uiState.update { it.copy(testResult = "返回结果为空", isTesting = false) }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        testResult = "连接失败: ${e.localizedMessage ?: "未知错误"}",
                        isTesting = false
                    )
                }
            }
        }
    }
}
