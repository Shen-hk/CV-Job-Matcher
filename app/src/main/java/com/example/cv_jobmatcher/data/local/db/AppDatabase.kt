package com.example.cv_jobmatcher.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.cv_jobmatcher.data.local.db.dao.HistoryDao
import com.example.cv_jobmatcher.data.local.db.entity.HistoryEntity

@Database(
    entities = [HistoryEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE history ADD COLUMN resume_json TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
