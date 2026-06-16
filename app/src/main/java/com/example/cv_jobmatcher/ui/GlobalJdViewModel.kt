package com.example.cv_jobmatcher.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cv_jobmatcher.data.local.AppPreferences
import com.example.cv_jobmatcher.domain.model.GlobalJdState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * CompositionLocal for accessing the globally shared JD ViewModel.
 * Provided at Activity level so all screens share the same instance.
 */
val LocalGlobalJdViewModel = staticCompositionLocalOf<GlobalJdViewModel> {
    error("GlobalJdViewModel not provided — make sure it's provided at the Activity level")
}

/**
 * 全局JD状态持有者 — Hilt @Singleton ViewModel，被所有模块注入共享。
 * 一份JD输入，全产品复用，消除 URL-encoded NavArgs 传递。
 */
@HiltViewModel
class GlobalJdViewModel @Inject constructor(
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(GlobalJdState())
    val state: StateFlow<GlobalJdState> = _state.asStateFlow()

    init {
        // 从缓存恢复JD状态
        viewModelScope.launch {
            val rawText = appPreferences.getCachedJdRawText()
            val json = appPreferences.getCachedJdStructuredJson()
            val company = appPreferences.getCachedJdCompanyName()
            if (rawText.isNotBlank()) {
                _state.value = GlobalJdState(
                    rawText = rawText,
                    structuredJson = json,
                    companyName = company,
                    positionName = extractPositionName(json)
                )
            }
        }
    }

    fun setJd(rawText: String, structuredJson: String = "", companyName: String = "") {
        _state.value = GlobalJdState(
            rawText = rawText,
            structuredJson = structuredJson,
            companyName = companyName,
            positionName = extractPositionName(structuredJson)
        )
        // 持久化缓存
        viewModelScope.launch {
            appPreferences.setCachedJdRawText(rawText)
            appPreferences.setCachedJdStructuredJson(structuredJson)
            appPreferences.setCachedJdCompanyName(companyName)
        }
    }

    fun setCompanyName(name: String) {
        _state.value = _state.value.copy(companyName = name)
        viewModelScope.launch {
            appPreferences.setCachedJdCompanyName(name)
        }
    }

    fun clearJd() {
        _state.value = GlobalJdState()
        viewModelScope.launch {
            appPreferences.setCachedJdRawText("")
            appPreferences.setCachedJdStructuredJson("")
            appPreferences.setCachedJdCompanyName("")
        }
    }

    /**
     * 从结构化的JD JSON中提取岗位名称。
     */
    private fun extractPositionName(json: String): String {
        if (json.isBlank()) return ""
        return try {
            // 简单的 job_title 字段提取（不依赖完整JSON解析）
            val key = "\"job_title\""
            val keyIdx = json.indexOf(key)
            if (keyIdx < 0) return ""
            val colonIdx = json.indexOf(':', keyIdx + key.length)
            if (colonIdx < 0) return ""
            val valueStart = json.indexOf('"', colonIdx + 1)
            if (valueStart < 0) return ""
            val valueEnd = json.indexOf('"', valueStart + 1)
            if (valueEnd < 0) return ""
            json.substring(valueStart + 1, valueEnd)
        } catch (_: Exception) { "" }
    }
}
