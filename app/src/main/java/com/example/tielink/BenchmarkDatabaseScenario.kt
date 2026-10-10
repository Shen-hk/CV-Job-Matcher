package com.example.tielink

import android.util.Log
import androidx.room.withTransaction
import com.example.tielink.data.local.db.AppDatabase
import com.example.tielink.data.local.db.entity.TrackingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Local, explicit benchmark path. It is run only when MainActivity receives its test extra. */
object BenchmarkDatabaseScenario {
    private const val TAG = "BenchmarkDatabase"

    suspend fun run(database: AppDatabase) = withContext(Dispatchers.IO) {
        database.withTransaction {
            database.trackingDao().deleteAll()
            database.trackingDao().insertAll(
                List(20_000) { index ->
                    TrackingEntity(
                        companyName = "公司$index",
                        positionName = "Android 工程师",
                        status = if (index % 5 == 0) "待面试" else "已投",
                        updatedAt = 2_000_000_000_000L - index
                    )
                }
            )
        }
        val db = database.openHelper.writableDatabase
        fun measure(): Long {
            val start = System.nanoTime()
            repeat(40) {
                db.query("SELECT * FROM tracking WHERE status = '待面试' ORDER BY updated_at DESC").use { cursor ->
                    while (cursor.moveToNext()) Unit
                }
            }
            return (System.nanoTime() - start) / 1_000_000
        }
        db.execSQL("DROP INDEX IF EXISTS index_tracking_status_updated_at")
        val withoutIndexMs = measure()
        db.execSQL("CREATE INDEX index_tracking_status_updated_at ON tracking(status, updated_at)")
        val withIndexMs = measure()
        Log.i(TAG, "rows=20000 queries=40 without_index_ms=$withoutIndexMs with_index_ms=$withIndexMs")
    }
}
