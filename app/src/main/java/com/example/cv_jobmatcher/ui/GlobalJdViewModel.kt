package com.example.cv_jobmatcher.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.example.cv_jobmatcher.data.local.AppPreferences
import com.example.cv_jobmatcher.domain.model.GlobalJdState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CompositionLocal for accessing the globally shared JD state holder.
 * Provided at Activity level so all screens share the same instance.
 */
val LocalGlobalJdViewModel = staticCompositionLocalOf<GlobalJdStateHolder> {
    error("GlobalJdStateHolder not provided — make sure it's provided at the Activity level")
}

/**
 * 全局JD状态持有者 — @Singleton 普通类（非 ViewModel），通过 Hilt 注入到 Activity。
 * 一份JD输入，全产品复用，消除 URL-encoded NavArgs 传递。
 *
 * 注意：不使用 @HiltViewModel + hiltViewModel()，因为在 Activity 级调用 hiltViewModel()
 * 可能因 ViewModelStoreOwner 上下文问题导致崩溃。改用 @Singleton + 字段注入更安全。
 */
@Singleton
class GlobalJdStateHolder @Inject constructor(
    private val appPreferences: AppPreferences
) {
    // 使用 SupervisorJob 避免子协程异常导致整个 scope 取消
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(GlobalJdState())
    val state: StateFlow<GlobalJdState> = _state.asStateFlow()

    init {
        // 从缓存恢复JD状态
        scope.launch {
            try {
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
            } catch (_: Exception) {
                // 缓存读取失败不影响使用
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
        scope.launch {
            try {
                appPreferences.setCachedJdRawText(rawText)
                appPreferences.setCachedJdStructuredJson(structuredJson)
                appPreferences.setCachedJdCompanyName(companyName)
            } catch (_: Exception) { }
        }
    }

    fun setCompanyName(name: String) {
        _state.value = _state.value.copy(companyName = name)
        scope.launch {
            try { appPreferences.setCachedJdCompanyName(name) } catch (_: Exception) { }
        }
    }

    fun clearJd() {
        _state.value = GlobalJdState()
        scope.launch {
            try {
                appPreferences.setCachedJdRawText("")
                appPreferences.setCachedJdStructuredJson("")
                appPreferences.setCachedJdCompanyName("")
            } catch (_: Exception) { }
        }
    }

    private fun extractPositionName(json: String): String {
        if (json.isBlank()) return ""
        return try {
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
