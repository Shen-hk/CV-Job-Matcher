package com.example.tielink.data.repository

import android.util.Log
import com.example.tielink.data.local.db.dao.HistoryDao
import com.example.tielink.data.local.db.entity.HistoryEntity
import com.example.tielink.domain.model.HistoryRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepository @Inject constructor(
    private val historyDao: HistoryDao
) {
    companion object {
        private const val TAG = "HistoryRepo"
    }

    fun getAllFlow(): Flow<List<HistoryRecord>> = historyDao.getAllFlow().map { entities ->
        entities.map(::toDomain)
    }

    suspend fun getAll(): List<HistoryRecord> = historyDao.getAllFlow().first().map(::toDomain)

    suspend fun getById(id: Long): HistoryRecord? {
        Log.d(TAG, "getById: id=$id")
        return historyDao.getById(id)?.let(::toDomain)
    }

    suspend fun insert(record: HistoryRecord): Long {
        val id = historyDao.insert(record.toEntity())
        Log.i(TAG, "insert: id=$id, title=${record.jdTitle}, sourceType=${record.sourceType}, templatePath=${record.originalFilePath}")
        return id
    }

    suspend fun deleteById(id: Long) {
        Log.d(TAG, "deleteById: id=$id")
        historyDao.deleteById(id)
    }

    suspend fun deleteByIds(ids: List<Long>) {
        if (ids.isEmpty()) return
        Log.d(TAG, "deleteByIds: count=${ids.size}")
        historyDao.deleteByIds(ids)
    }

    suspend fun rename(id: Long, title: String) {
        Log.d(TAG, "rename: id=$id, title=$title")
        historyDao.updateTitle(id, title, System.currentTimeMillis())
    }

    suspend fun updatePinned(id: Long, isPinned: Boolean) {
        Log.d(TAG, "updatePinned: id=$id, isPinned=$isPinned")
        historyDao.updatePinned(id, isPinned, System.currentTimeMillis())
    }

    suspend fun updatePinnedByIds(ids: List<Long>, isPinned: Boolean) {
        if (ids.isEmpty()) return
        Log.d(TAG, "updatePinnedByIds: count=${ids.size}, isPinned=$isPinned")
        historyDao.updatePinnedByIds(ids, isPinned, System.currentTimeMillis())
    }

    suspend fun deleteAll() {
        Log.d(TAG, "deleteAll")
        historyDao.deleteAll()
    }

    private fun toDomain(entity: HistoryEntity) = HistoryRecord(
        id = entity.id,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
        jdRawText = entity.jdRawText,
        jdTitle = entity.jdTitle,
        customTitle = entity.customTitle,
        originalResume = entity.originalResume,
        polishedResume = entity.polishedResume,
        resumeJson = entity.resumeJson,
        jdSkills = entity.jdSkills,
        matchNote = entity.matchNote,
        matchScore = entity.matchScore,
        matchedKeywords = entity.matchedKeywords,
        missingKeywords = entity.missingKeywords,
        suggestions = entity.suggestions,
        originalFilePath = entity.originalFilePath,
        sourceType = entity.sourceType,
        templateStyle = entity.templateStyle,
        isPinned = entity.isPinned
    )

    private fun HistoryRecord.toEntity() = HistoryEntity(
        id = id,
        createdAt = createdAt,
        updatedAt = updatedAt,
        jdRawText = jdRawText,
        jdTitle = jdTitle,
        customTitle = customTitle,
        originalResume = originalResume,
        polishedResume = polishedResume,
        resumeJson = resumeJson,
        jdSkills = jdSkills,
        matchNote = matchNote,
        matchScore = matchScore,
        matchedKeywords = matchedKeywords,
        missingKeywords = missingKeywords,
        suggestions = suggestions,
        originalFilePath = originalFilePath,
        sourceType = sourceType,
        templateStyle = templateStyle,
        isPinned = isPinned
    )
}
