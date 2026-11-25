package com.example.tielink

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.tielink.data.local.db.AppDatabase
import com.example.tielink.data.repository.ResumeVersionRepository
import com.example.tielink.data.repository.TrackingItem
import com.example.tielink.data.repository.TrackingRepository
import com.example.tielink.domain.agent.CareerInputIdentity
import com.example.tielink.domain.model.ResumeVersion
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AgentOrchestrationPersistenceTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var resumes: ResumeVersionRepository
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        resumes = ResumeVersionRepository(database.resumeVersionDao(), moshi, context, database)
    }

    @After fun cleanup() { database.close() }

    @Test fun repeatedConfirmationCreatesOnlyOneVersion() = runBlocking {
        val source = ResumeVersion(name = "原始简历", rawText = "Kotlin Android")
        val sourceId = resumes.insertAndActivate(source)
        val inputs = CareerInputIdentity.snapshot(1, "JD", sourceId, source.cleanedText)
        val candidate = source.copy(name = "定向简历", cleanedText = "Kotlin Android Compose")
        val first = resumes.insertAndActivateOnce("adopt-1", candidate, inputs)
        val second = resumes.insertAndActivateOnce("adopt-1", candidate, inputs)
        assertEquals(first, second)
        assertEquals(2, resumes.getAll().size)
        assertEquals(first, resumes.getActive()?.id)
        assertEquals(first, resumes.committedVersionId("adopt-1"))
    }

    @Test fun failedTransactionRollsBackBothVersionAndReceipt() = runBlocking {
        val source = ResumeVersion(name = "原始简历", rawText = "Android")
        val sourceId = resumes.insertAndActivate(source)
        val inputs = CareerInputIdentity.snapshot(1, "JD", sourceId, source.cleanedText)
        try {
            database.withTransaction {
                resumes.insertAndActivateOnce("failed-adoption", source.copy(name = "候选稿"), inputs)
                error("模拟保存事务中断")
            }
            fail("Expected rollback")
        } catch (_: IllegalStateException) { }
        assertEquals(1, resumes.getAll().size)
        assertEquals(sourceId, resumes.getActive()?.id)
        assertNull(resumes.committedVersionId("failed-adoption"))
    }

    @Test fun switchingSourceInvalidatesAnUncommittedCandidate() = runBlocking {
        val source = ResumeVersion(name = "原始简历", rawText = "Android")
        val sourceId = resumes.insertAndActivate(source)
        val inputs = CareerInputIdentity.snapshot(1, "JD", sourceId, source.cleanedText)
        val anotherId = resumes.insertAndActivate(source.copy(name = "另一版本"))
        try {
            resumes.insertAndActivateOnce("stale-adoption", source.copy(name = "旧候选稿"), inputs)
            fail("Old candidate must be rejected")
        } catch (_: IllegalArgumentException) { }
        assertEquals(anotherId, resumes.getActive()?.id)
        assertNull(resumes.committedVersionId("stale-adoption"))
    }

    @Test fun replayingApplicationCreationReusesTheBusinessRecord() = runBlocking {
        val tracking = TrackingRepository(database.trackingDao(), moshi, database)
        val item = TrackingItem(companyName = "示例公司", positionName = "Android", status = "待投", jdRawText = "JD-1")
        val first = tracking.insertOnce("application-1", item)
        assertEquals(first, tracking.insertOnce("application-1", item))
        assertEquals(first, tracking.insertOnce("application-retry", item))
        assertEquals(1, tracking.getAll().size)
        assertEquals("待投", tracking.getById(first)?.status)
    }

    @Test fun applicationRecordsKeepTheirResumeVersionIdentityAndExplicitStatus() = runBlocking {
        val tracking = TrackingRepository(database.trackingDao(), moshi, database)
        val item = TrackingItem(companyName = "示例公司", positionName = "Android", status = "待投", jdRawText = "JD-1", resumeVersionId = 1)
        val first = tracking.insertOnce("original", item)
        assertEquals(first, tracking.insertOnce("report-submitted", item.copy(status = "已投")))
        assertEquals("已投", tracking.getById(first)?.status)
        val second = tracking.insertOnce("another-version", item.copy(resumeVersionId = 2))
        assertNotEquals(first, second)
        assertEquals(2L, tracking.getById(second)?.resumeVersionId)
        assertEquals("待投", tracking.getById(second)?.status)
    }

    @Test fun migration14To15PreservesExistingResumes() = runBlocking {
        database.close()
        val name = "orchestration-migration-${UUID.randomUUID()}.db"
        try {
            database = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            val dao = database.resumeVersionDao()
            val id = dao.insert(com.example.tielink.data.local.db.entity.ResumeVersionEntity(name = "保留数据", rawText = "Android"))
            database.close()
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { sqlite ->
                sqlite.execSQL("DROP TABLE agent_operation_receipts")
                sqlite.execSQL("DROP TABLE agent_background_tasks")
                sqlite.execSQL("DROP TABLE agent_teams")
                sqlite.version = 14
            }
            database = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(AppDatabase.MIGRATION_14_15, AppDatabase.MIGRATION_15_16, AppDatabase.MIGRATION_16_17).build()
            assertEquals("保留数据", database.resumeVersionDao().getById(id)?.name)
            assertNull(database.agentOperationReceiptDao().get("missing"))
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
