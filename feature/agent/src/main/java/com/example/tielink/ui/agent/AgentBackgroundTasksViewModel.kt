package com.example.tielink.ui.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tielink.background.AgentBackgroundDispatcher
import com.example.tielink.data.repository.AgentBackgroundTaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AgentBackgroundTasksViewModel @Inject constructor(
    private val repository: AgentBackgroundTaskRepository,
    private val dispatcher: AgentBackgroundDispatcher
) : ViewModel() {
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _loaded = MutableStateFlow(false)
    val loaded = _loaded.asStateFlow()
    val tasks = repository.tasks.onEach { _loaded.value = true }.catch { error ->
        _error.value = error.message ?: "任务列表暂时无法读取"
        _loaded.value = true
        emit(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init { action { repository.initialize() } }
    fun pause(id: String) = action { dispatcher.pause(id) }
    fun cancel(id: String) = action { dispatcher.cancel(id) }
    fun resume(id: String) = action {
        if (!dispatcher.resume(id)) _error.value = "系统暂未允许后台执行，任务已保留。请保持应用在前台后再试。"
    }
    fun markRead(id: String) = action { repository.markRead(id) }
    fun clearError() { _error.value = null }

    private fun action(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) { _error.value = error.message ?: "任务操作未完成" }
        }
    }
}
