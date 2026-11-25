package com.example.tielink.ui.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tielink.data.repository.AgentTeamRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AgentTeamChange(val version: Long = 0L, val deletedId: String? = null)

@HiltViewModel
class AgentTeamsViewModel @Inject constructor(private val repository: AgentTeamRepository) : ViewModel() {
    private val _loaded = MutableStateFlow(false)
    val loaded = _loaded.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _change = MutableStateFlow(AgentTeamChange())
    val change = _change.asStateFlow()
    val teams = repository.teams.onEach { _loaded.value = true }.catch {
        _error.value = "团队暂时无法读取，请稍后重新进入。"
        _loaded.value = true
        emit(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(id: String?, name: String, brief: String, instructions: String, includeHtml: Boolean,
        onSaved: () -> Unit) = action {
        repository.save(id, name, brief, instructions, includeHtml)
        _change.value = AgentTeamChange(_change.value.version + 1)
        onSaved()
    }

    fun delete(id: String, onDeleted: () -> Unit) = action {
        repository.delete(id)
        _change.value = AgentTeamChange(_change.value.version + 1, id)
        onDeleted()
    }
    fun clearError() { _error.value = null }

    private fun action(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        _error.value = null
        viewModelScope.launch {
            try { block() } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { _error.value = error.message ?: "团队未能保存，请重试。" }
            finally { _busy.value = false }
        }
    }
}
