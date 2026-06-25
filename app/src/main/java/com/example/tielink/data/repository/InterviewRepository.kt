package com.example.tielink.data.repository

import com.example.tielink.data.local.db.dao.InterviewDao
import com.example.tielink.data.local.db.entity.InterviewMessageEntity
import com.example.tielink.data.local.db.entity.InterviewSessionEntity
import com.example.tielink.domain.model.InterviewMessage
import com.example.tielink.domain.model.InterviewPersona
import com.example.tielink.domain.model.InterviewSession
import com.example.tielink.domain.model.MessageRole
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InterviewRepository @Inject constructor(
    private val dao: InterviewDao,
    private val moshi: Moshi
) {
    // ── Sessions ────────────────────────────────────────

    fun getAllSessionsFlow(): Flow<List<InterviewSession>> =
        dao.getAllSessionsFlow().map { list -> list.map { it.toDomain() } }

    suspend fun getSessionById(id: Long): InterviewSession? =
        dao.getSessionById(id)?.toDomain()

    suspend fun getActiveSession(): InterviewSession? =
        dao.getActiveSession()?.toDomain()

    suspend fun createSession(session: InterviewSession): Long {
        dao.deactivateAllSessions()
        return dao.insertSession(session.toEntity())
    }

    suspend fun updateSession(session: InterviewSession) {
        dao.updateSession(session.toEntity())
    }

    suspend fun endSession(sessionId: Long, overallScore: Float?,
                           dimensionScores: Map<String, Float> = emptyMap(),
                           improvements: List<String> = emptyList()) {
        val entity = dao.getSessionById(sessionId) ?: return
        val dimScoresJson = moshi.adapter<Map<String, Float>>(Map::class.java).toJson(dimensionScores)
        val impJson = moshi.adapter<List<String>>(List::class.java).toJson(improvements)
        dao.updateSession(entity.copy(
            isActive = false,
            overallScore = overallScore,
            dimensionScores = dimScoresJson,
            improvements = impJson
        ))
    }

    // ── Messages ────────────────────────────────────────

    fun getMessagesFlow(sessionId: Long): Flow<List<InterviewMessage>> =
        dao.getMessagesFlow(sessionId).map { list -> list.map { it.toDomain() } }

    suspend fun getMessages(sessionId: Long): List<InterviewMessage> =
        dao.getMessages(sessionId).map { it.toDomain() }

    suspend fun addMessage(message: InterviewMessage): Long {
        return dao.insertMessage(message.toEntity())
    }

    suspend fun addMessages(messages: List<InterviewMessage>) {
        dao.insertMessages(messages.map { it.toEntity() })
    }

    suspend fun incrementQuestionCount(sessionId: Long) {
        val entity = dao.getSessionById(sessionId) ?: return
        dao.updateSession(entity.copy(questionCount = entity.questionCount + 1))
    }

    // ── Cleanup ─────────────────────────────────────────

    suspend fun deleteSession(id: Long) {
        val entity = dao.getSessionById(id) ?: return
        dao.deleteSessionWithMessages(entity)
    }

    // ── Mapping helpers ─────────────────────────────────

    private fun InterviewSessionEntity.toDomain(): InterviewSession {
        val dimScores: Map<String, Float> = try {
            moshi.adapter<Map<String, Float>>(Map::class.java).fromJson(dimensionScores) ?: emptyMap()
        } catch (_: Exception) { emptyMap() }
        val imps: List<String> = try {
            moshi.adapter<List<String>>(List::class.java).fromJson(improvements) ?: emptyList()
        } catch (_: Exception) { emptyList() }
        return InterviewSession(
            id = id,
            personaType = try { InterviewPersona.valueOf(personaType) } catch (_: Exception) { InterviewPersona.MILD_TECH },
            jdRawText = jdRawText,
            resumeVersionId = resumeVersionId,
            resumeText = resumeText,
            isActive = isActive,
            questionCount = questionCount,
            overallScore = overallScore,
            dimensionScores = dimScores,
            improvements = imps,
            createdAt = createdAt
        )
    }

    private fun InterviewSession.toEntity(): InterviewSessionEntity {
        val dimScoresJson = moshi.adapter<Map<String, Float>>(Map::class.java).toJson(dimensionScores)
        val impJson = moshi.adapter<List<String>>(List::class.java).toJson(improvements)
        return InterviewSessionEntity(
            id = id, personaType = personaType.name,
            jdRawText = jdRawText, resumeVersionId = resumeVersionId,
            resumeText = resumeText, isActive = isActive,
            questionCount = questionCount, overallScore = overallScore,
            dimensionScores = dimScoresJson, improvements = impJson,
            createdAt = createdAt
        )
    }

    private fun InterviewMessageEntity.toDomain(): InterviewMessage = InterviewMessage(
        id = id, sessionId = sessionId,
        role = try { MessageRole.valueOf(role) } catch (_: Exception) { MessageRole.SYSTEM },
        content = content, timestamp = timestamp,
        isHint = isHint, isEvaluation = isEvaluation
    )

    private fun InterviewMessage.toEntity(): InterviewMessageEntity = InterviewMessageEntity(
        id = id, sessionId = sessionId, role = role.name,
        content = content, timestamp = timestamp,
        isHint = isHint, isEvaluation = isEvaluation
    )
}
