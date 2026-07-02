package com.example.tielink.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.tielink.data.local.db.dao.HistoryDao
import com.example.tielink.data.local.db.dao.InterviewDao
import com.example.tielink.data.local.db.dao.JdLibraryDao
import com.example.tielink.data.local.db.dao.ProviderDao
import com.example.tielink.data.local.db.dao.ResumeVersionDao
import com.example.tielink.data.local.db.dao.TrackingDao
import com.example.tielink.data.local.db.entity.HistoryEntity
import com.example.tielink.data.local.db.entity.InterviewMessageEntity
import com.example.tielink.data.local.db.entity.InterviewSessionEntity
import com.example.tielink.data.local.db.entity.JdLibraryEntity
import com.example.tielink.data.local.db.entity.ProviderEntity
import com.example.tielink.data.local.db.entity.ProviderModelEntity
import com.example.tielink.data.local.db.entity.ResumeVersionEntity
import com.example.tielink.data.local.db.entity.TrackingEntity

@Database(
    entities = [
        HistoryEntity::class,
        ResumeVersionEntity::class,
        TrackingEntity::class,
        InterviewSessionEntity::class,
        InterviewMessageEntity::class,
        JdLibraryEntity::class,
        ProviderEntity::class,
        ProviderModelEntity::class
    ],
    version = 12,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun resumeVersionDao(): ResumeVersionDao
    abstract fun trackingDao(): TrackingDao
    abstract fun interviewDao(): InterviewDao
    abstract fun jdLibraryDao(): JdLibraryDao
    abstract fun providerDao(): ProviderDao

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE history ADD COLUMN resume_json TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Resume versions table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS resume_versions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        raw_text TEXT NOT NULL,
                        cleaned_text TEXT NOT NULL DEFAULT '',
                        jd_matched_with TEXT NOT NULL DEFAULT '',
                        match_score REAL NOT NULL DEFAULT 0,
                        tags TEXT NOT NULL DEFAULT '',
                        is_active INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                """.trimIndent())

                // Tracking table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS tracking (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        company_name TEXT NOT NULL,
                        position_name TEXT NOT NULL,
                        status TEXT NOT NULL DEFAULT '已投',
                        resume_version_id INTEGER,
                        jd_raw_text TEXT NOT NULL DEFAULT '',
                        notes TEXT NOT NULL DEFAULT '',
                        timeline TEXT NOT NULL DEFAULT '[]',
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                """.trimIndent())

                // Interview sessions table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS interview_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        persona_type TEXT NOT NULL,
                        jd_raw_text TEXT NOT NULL DEFAULT '',
                        resume_version_id INTEGER,
                        resume_text TEXT NOT NULL DEFAULT '',
                        is_active INTEGER NOT NULL DEFAULT 1,
                        question_count INTEGER NOT NULL DEFAULT 0,
                        overall_score REAL,
                        dimension_scores TEXT NOT NULL DEFAULT '',
                        improvements TEXT NOT NULL DEFAULT '',
                        created_at INTEGER NOT NULL
                    )
                """.trimIndent())

                // Interview messages table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS interview_messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        session_id INTEGER NOT NULL,
                        role TEXT NOT NULL,
                        content TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        is_hint INTEGER NOT NULL DEFAULT 0,
                        is_evaluation INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (session_id) REFERENCES interview_sessions(id) ON DELETE CASCADE
                    )
                """.trimIndent())

                // Index on session_id for fast message lookups
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_interview_messages_session_id ON interview_messages(session_id)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS jd_library (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        company_name TEXT NOT NULL DEFAULT '',
                        position_name TEXT NOT NULL DEFAULT '',
                        raw_text TEXT NOT NULL,
                        structured_json TEXT NOT NULL DEFAULT '',
                        skills TEXT NOT NULL DEFAULT '',
                        source_type TEXT NOT NULL DEFAULT 'manual',
                        created_at INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE jd_library ADD COLUMN salary TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS providers (
                        providerId INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        baseUrl TEXT NOT NULL,
                        apiKey TEXT NOT NULL,
                        apiFormat TEXT NOT NULL,
                        createTime INTEGER NOT NULL
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS provider_models (
                        modelId INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        providerId INTEGER NOT NULL,
                        modelName TEXT NOT NULL,
                        createTime INTEGER NOT NULL,
                        FOREIGN KEY (providerId) REFERENCES providers(providerId) ON DELETE CASCADE
                    )
                """.trimIndent())

                db.execSQL("CREATE INDEX IF NOT EXISTS idx_provider_models_provider_id ON provider_models(providerId)")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Schema for version 11 was stabilized before the history refinements below.
                // Keep this migration as a bridge so upgrades can move from 10 -> 12 safely.
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(
                    db = db,
                    tableName = "history",
                    columnName = "updated_at",
                    columnSql = "INTEGER NOT NULL DEFAULT 0"
                )
                addColumnIfMissing(
                    db = db,
                    tableName = "history",
                    columnName = "custom_title",
                    columnSql = "TEXT NOT NULL DEFAULT ''"
                )
                addColumnIfMissing(
                    db = db,
                    tableName = "history",
                    columnName = "is_pinned",
                    columnSql = "INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL("UPDATE history SET updated_at = created_at WHERE updated_at = 0")
            }
        }

        private fun addColumnIfMissing(
            db: SupportSQLiteDatabase,
            tableName: String,
            columnName: String,
            columnSql: String
        ) {
            if (hasColumn(db, tableName, columnName)) return
            db.execSQL("ALTER TABLE $tableName ADD COLUMN $columnName $columnSql")
        }

        private fun hasColumn(
            db: SupportSQLiteDatabase,
            tableName: String,
            columnName: String
        ): Boolean {
            db.query("PRAGMA table_info($tableName)").use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == columnName) return true
                }
            }
            return false
        }
    }
}
