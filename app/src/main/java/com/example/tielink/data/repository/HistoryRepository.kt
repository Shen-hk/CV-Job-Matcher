package com.example.tielink.data.repository

import android.util.Log
import com.example.tielink.data.local.db.dao.HistoryDao
import com.example.tielink.data.local.db.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepository @Inject constructor(
    private val historyDao: HistoryDao
) {
    companion object {
        private const val TAG = "HistoryRepo"
    }

    fun getAllFlow(): Flow<List<HistoryEntity>> = historyDao.getAllFlow()

    suspend fun getById(id: Long): HistoryEntity? {
        Log.d(TAG, "getById: id=$id")
        return historyDao.getById(id)
    }

    suspend fun insert(entity: HistoryEntity): Long {
        val id = historyDao.insert(entity)
        Log.i(TAG, "insert: id=$id, title=${entity.jdTitle}, sourceType=${entity.sourceType}, templatePath=${entity.originalFilePath}")
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
}
