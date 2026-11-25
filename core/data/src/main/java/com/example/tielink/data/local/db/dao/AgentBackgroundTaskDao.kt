package com.example.tielink.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.tielink.data.local.db.entity.AgentBackgroundTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AgentBackgroundTaskDao {
    @Query("SELECT * FROM agent_background_tasks ORDER BY createdAt DESC, id DESC")
    abstract fun observeAll(): Flow<List<AgentBackgroundTaskEntity>>

    @Query("SELECT * FROM agent_background_tasks WHERE id = :id")
    abstract suspend fun get(id: String): AgentBackgroundTaskEntity?

    @Query("SELECT * FROM agent_background_tasks WHERE requestId = :requestId")
    abstract suspend fun getByRequestId(requestId: String): AgentBackgroundTaskEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(task: AgentBackgroundTaskEntity): Long

    @Query("UPDATE agent_background_tasks SET status = 'SUPERSEDED', epoch = epoch + 1, updatedAt = :now WHERE id = :id AND status NOT IN ('COMPLETED','CANCELED','SUPERSEDED')")
    protected abstract suspend fun supersede(id: String, now: Long)

    @Transaction
    open suspend fun enqueue(task: AgentBackgroundTaskEntity) {
        if (insert(task) != -1L) task.supersedesTaskId?.let { supersede(it, task.createdAt) }
    }

    @Query("SELECT * FROM agent_background_tasks WHERE status = 'QUEUED' ORDER BY createdAt ASC, id ASC LIMIT 1")
    protected abstract suspend fun nextQueued(): AgentBackgroundTaskEntity?

    @Query("SELECT COUNT(*) FROM agent_background_tasks WHERE status = 'RUNNING'")
    protected abstract suspend fun runningCount(): Int

    @Query("UPDATE agent_background_tasks SET status = 'RUNNING', epoch = epoch + 1, error = '', updatedAt = :now WHERE id = :id AND status = 'QUEUED'")
    protected abstract suspend fun claim(id: String, now: Long): Int

    @Transaction
    open suspend fun claimNext(now: Long): AgentBackgroundTaskEntity? {
        if (runningCount() != 0) return null
        val task = nextQueued() ?: return null
        if (claim(task.id, now) == 0) return null
        return get(task.id)
    }

    /** The epoch fences late responses from canceled, paused, or previous-process executions. */
    @Query("UPDATE agent_background_tasks SET stage = :stage, partialText = :partial, document = :document, html = :html, updatedAt = :now WHERE id = :id AND epoch = :epoch AND status = 'RUNNING'")
    abstract suspend fun checkpoint(id: String, epoch: Int, stage: String, partial: String, document: String, html: String, now: Long): Int

    @Query("UPDATE agent_background_tasks SET status = :status, error = :error, updatedAt = :now WHERE id = :id AND epoch = :epoch AND status = 'RUNNING'")
    abstract suspend fun finish(id: String, epoch: Int, status: String, error: String, now: Long): Int

    @Query("UPDATE agent_background_tasks SET status = :status, epoch = epoch + 1, updatedAt = :now WHERE id = :id AND status IN ('RUNNING','QUEUED')")
    abstract suspend fun interrupt(id: String, status: String, now: Long): Int

    @Query("UPDATE agent_background_tasks SET status = 'CANCELED', epoch = epoch + 1, updatedAt = :now WHERE id = :id AND status NOT IN ('COMPLETED','CANCELED','SUPERSEDED')")
    abstract suspend fun cancel(id: String, now: Long): Int

    @Query("UPDATE agent_background_tasks SET status = 'QUEUED', error = '', updatedAt = :now WHERE id = :id AND status IN ('PAUSED','FAILED')")
    abstract suspend fun resume(id: String, now: Long): Int

    @Query("UPDATE agent_background_tasks SET status = 'PAUSED', epoch = epoch + 1, error = '上次执行已中断，内容已保留。可继续生成未完成部分。', updatedAt = :now WHERE status = 'RUNNING'")
    abstract suspend fun recoverInterrupted(now: Long): Int

    @Query("UPDATE agent_background_tasks SET readAt = :now WHERE id = :id AND status = 'COMPLETED'")
    abstract suspend fun markRead(id: String, now: Long)
}
