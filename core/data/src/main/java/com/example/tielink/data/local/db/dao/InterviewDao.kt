package com.example.tielink.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.tielink.data.local.db.entity.InterviewMessageEntity
import com.example.tielink.data.local.db.entity.InterviewSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InterviewDao {

    // ── Session ─────────────────────────────────────────

    @Query("SELECT * FROM interview_sessions ORDER BY created_at DESC")
    fun getAllSessionsFlow(): Flow<List<InterviewSessionEntity>>

    @Query("SELECT * FROM interview_sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): InterviewSessionEntity?

    @Query("SELECT * FROM interview_sessions WHERE is_active = 1 LIMIT 1")
    suspend fun getActiveSession(): InterviewSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(entity: InterviewSessionEntity): Long

    @Update
    suspend fun updateSession(entity: InterviewSessionEntity)

    @Delete
    suspend fun deleteSession(entity: InterviewSessionEntity)

    @Query("UPDATE interview_sessions SET is_active = 0")
    suspend fun deactivateAllSessions()

    // ── Messages ────────────────────────────────────────

    @Query("SELECT * FROM interview_messages WHERE session_id = :sessionId ORDER BY timestamp ASC")
    fun getMessagesFlow(sessionId: Long): Flow<List<InterviewMessageEntity>>

    @Query("SELECT * FROM interview_messages WHERE session_id = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessages(sessionId: Long): List<InterviewMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(entity: InterviewMessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(entities: List<InterviewMessageEntity>)

    @Query("DELETE FROM interview_messages WHERE session_id = :sessionId")
    suspend fun deleteMessagesBySession(sessionId: Long)

    // ── Transaction ─────────────────────────────────────

    @Transaction
    suspend fun endSession(session: InterviewSessionEntity) {
        updateSession(session.copy(isActive = false))
    }

    @Transaction
    suspend fun deleteSessionWithMessages(session: InterviewSessionEntity) {
        deleteMessagesBySession(session.id)
        deleteSession(session)
    }
}
