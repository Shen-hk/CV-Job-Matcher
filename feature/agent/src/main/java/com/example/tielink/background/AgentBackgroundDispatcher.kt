package com.example.tielink.background

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.tielink.data.repository.AgentBackgroundTaskRepository
import com.example.tielink.domain.model.AgentBackgroundTask
import com.example.tielink.domain.model.AgentTeamSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentBackgroundDispatcher @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val tasks: AgentBackgroundTaskRepository
) {
    data class Receipt(val task: AgentBackgroundTask, val started: Boolean)

    suspend fun enqueue(prompt: String, originHistoryId: Long?, sourceTaskId: String? = null, supersedesTaskId: String? = null,
        team: AgentTeamSnapshot? = null, includeHtml: Boolean = team?.includeHtml != false): Receipt {
        val previous = supersedesTaskId?.let { tasks.get(it) }
        val task = tasks.enqueue(prompt, originHistoryId, sourceTaskId = sourceTaskId, supersedesTaskId = supersedesTaskId, team = team, includeHtml = includeHtml)
        if (previous?.status == com.example.tielink.domain.model.AgentBackgroundStatus.RUNNING) sendControl(previous.id)
        return Receipt(task, start(task.id))
    }

    suspend fun resume(id: String): Boolean {
        tasks.initialize()
        val current = tasks.get(id) ?: return false
        if (!current.status.canResume) return false
        tasks.resume(id)
        return start(id)
    }

    suspend fun pause(id: String) {
        tasks.pause(id)
        sendControl(id)
    }

    suspend fun cancel(id: String) {
        tasks.cancel(id)
        sendControl(id)
    }

    private suspend fun start(id: String): Boolean = try {
        ContextCompat.startForegroundService(context, Intent(context, AgentBackgroundService::class.java)
            .setAction(AgentBackgroundService.ACTION_RUN).putExtra(EXTRA_TASK_ID, id))
        true
    } catch (_: RuntimeException) {
        tasks.pause(id)
        false
    }

    private fun sendControl(id: String) {
        // The service normally already exists. The durable epoch is invalidated first,
        // so even a denied control start cannot let a late response commit results.
        runCatching {
            context.startService(Intent(context, AgentBackgroundService::class.java)
                .setAction(AgentBackgroundService.ACTION_CHECK_CONTROL)
                .putExtra(EXTRA_TASK_ID, id))
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "com.example.tielink.BACKGROUND_TASK_ID"
    }
}
