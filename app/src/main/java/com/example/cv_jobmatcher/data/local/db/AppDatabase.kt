package com.example.cv_jobmatcher.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.cv_jobmatcher.data.local.db.dao.HistoryDao
import com.example.cv_jobmatcher.data.local.db.entity.HistoryEntity

@Database(
    entities = [HistoryEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
}
