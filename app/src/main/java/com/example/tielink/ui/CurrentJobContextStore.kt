package com.example.tielink.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.example.tielink.data.local.AppContentStore
import com.example.tielink.data.local.CachedJobContent
import com.example.tielink.domain.context.CurrentJobContext
import com.example.tielink.domain.model.GlobalJdState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

val LocalCurrentJobContext = staticCompositionLocalOf<CurrentJobContext> {
    error("CurrentJobContext must be provided at the Activity level")
}

/** Persists the selected JD independently from the navigation back stack. */
@Singleton
class CurrentJobContextStore @Inject constructor(
    private val appContentStore: AppContentStore
) : CurrentJobContext {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(GlobalJdState())
    override val state: StateFlow<GlobalJdState> = _state.asStateFlow()

    init {
        scope.launch {
            runCatching {
                val cached = appContentStore.getCurrentJob()
                val rawText = cached.rawText
                if (rawText.isNotBlank()) {
                    val structuredJson = cached.structuredJson
                    _state.value = GlobalJdState(
                        rawText = rawText,
                        structuredJson = structuredJson,
                        companyName = cached.companyName,
                        positionName = extractPositionName(structuredJson)
                    )
                }
            }
        }
    }

    override fun setJd(rawText: String, structuredJson: String, companyName: String) {
        _state.value = GlobalJdState(
            rawText = rawText,
            structuredJson = structuredJson,
            companyName = companyName,
            positionName = extractPositionName(structuredJson)
        )
        scope.launch { persist(rawText, structuredJson, companyName) }
    }

    override fun setCompanyName(name: String) {
        _state.value = _state.value.copy(companyName = name)
        scope.launch { persist(_state.value.rawText, _state.value.structuredJson, name) }
    }

    override fun clearJd() {
        _state.value = GlobalJdState()
        scope.launch { persist("", "", "") }
    }

    private suspend fun persist(rawText: String, structuredJson: String, companyName: String) {
        runCatching {
            appContentStore.setCurrentJob(CachedJobContent(rawText, structuredJson, companyName))
        }
    }

    private fun extractPositionName(json: String): String {
        val key = "\"job_title\""
        val keyIndex = json.indexOf(key)
        if (keyIndex < 0) return ""
        val colonIndex = json.indexOf(':', keyIndex + key.length)
        val valueStart = json.indexOf('"', colonIndex + 1)
        val valueEnd = json.indexOf('"', valueStart + 1)
        return if (colonIndex >= 0 && valueStart >= 0 && valueEnd > valueStart) {
            json.substring(valueStart + 1, valueEnd)
        } else {
            ""
        }
    }
}
