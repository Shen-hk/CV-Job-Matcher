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
}
