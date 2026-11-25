package com.example.tielink.data.repository

import com.example.tielink.data.local.db.AppDatabase
import com.example.tielink.data.local.db.entity.AgentBackgroundTaskEntity
import com.example.tielink.domain.agent.BackgroundDocumentPolicy
import com.example.tielink.domain.agent.AgentTeamPolicy
import com.example.tielink.domain.model.AgentTeamSnapshot
import com.example.tielink.domain.model.AgentBackgroundStatus
import com.example.tielink.domain.model.AgentBackgroundTask
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentBackgroundTaskRepository @Inject constructor(database: AppDatabase) {
    private val dao = database.agentBackgroundTaskDao()
    private val recoveryMutex = Mutex()
    private var recovered = false
    val tasks = dao.observeAll().map { rows -> rows.map { it.toModel() } }

    suspend fun initialize() = recoveryMutex.withLock {
        if (!recovered) {
            dao.recoverInterrupted(System.currentTimeMillis())
            recovered = true
        }
    }

    suspend fun enqueue(prompt: String, originHistoryId: Long?, requestId: String = UUID.randomUUID().toString(), sourceTaskId: String? = null,
        supersedesTaskId: String? = null, team: AgentTeamSnapshot? = null,
        includeHtml: Boolean = team?.includeHtml != false): AgentBackgroundTask {
        initialize()
        require(prompt.isNotBlank() && prompt.length <= BackgroundDocumentPolicy.MAX_PROMPT_LENGTH) { "任务内容为空或超过 12000 字符" }
        team?.let(AgentTeamPolicy::validate)
        if (sourceTaskId != null) require(get(sourceTaskId)?.hasDocument == true) { "原稿尚未完成，无法开始修订" }
        val now = System.currentTimeMillis()
        val task = AgentBackgroundTaskEntity(UUID.randomUUID().toString(), requestId, originHistoryId,
            BackgroundDocumentPolicy.title(prompt), prompt, createdAt = now, updatedAt = now, sourceTaskId = sourceTaskId,
            supersedesTaskId = supersedesTaskId, teamId = team?.id, teamName = team?.name.orEmpty(),
            teamBrief = team?.brief.orEmpty(), teamInstructions = team?.instructions.orEmpty(), includeHtml = includeHtml)
        dao.enqueue(task)
        val saved = requireNotNull(dao.getByRequestId(requestId))
        // The task snapshot records its effective tool selection, including an explicit per-task override.
        require(saved.prompt == prompt && saved.originHistoryId == originHistoryId && saved.sourceTaskId == sourceTaskId && saved.supersedesTaskId == supersedesTaskId &&
            saved.toModel().team == team?.copy(includeHtml = includeHtml) && saved.includeHtml == includeHtml) {
            "相同请求标识已用于另一项任务，请重新发送。"
        }
        return saved.toModel()
    }

    suspend fun get(id: String): AgentBackgroundTask? = dao.get(id)?.toModel()
    suspend fun claimNext(): AgentBackgroundTask? { initialize(); return dao.claimNext(System.currentTimeMillis())?.toModel() }
    suspend fun checkpoint(task: AgentBackgroundTask, stage: String, partial: String = task.partialText,
        document: String = task.document, html: String = task.html): Boolean =
        dao.checkpoint(task.id, task.epoch, stage, partial, document, html, System.currentTimeMillis()) != 0

    suspend fun finish(task: AgentBackgroundTask, status: AgentBackgroundStatus, error: String = ""): Boolean {
        require(status in setOf(AgentBackgroundStatus.COMPLETED, AgentBackgroundStatus.FAILED, AgentBackgroundStatus.PAUSED))
        if (status == AgentBackgroundStatus.COMPLETED) {
            val current = get(task.id) ?: return false
            require(BackgroundDocumentPolicy.mayComplete(current.document, current.html, current.includeHtml)) { "交付物尚未保存" }
        }
        return dao.finish(task.id, task.epoch, status.name, error, System.currentTimeMillis()) != 0
    }

    suspend fun pause(id: String) = dao.interrupt(id, "PAUSED", System.currentTimeMillis())
    suspend fun cancel(id: String) = dao.cancel(id, System.currentTimeMillis())
    suspend fun resume(id: String) = dao.resume(id, System.currentTimeMillis())
    suspend fun markRead(id: String) = dao.markRead(id, System.currentTimeMillis())
}
