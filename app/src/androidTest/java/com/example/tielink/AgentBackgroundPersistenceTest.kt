package com.example.tielink

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.tielink.data.local.db.AppDatabase
import com.example.tielink.data.local.db.entity.AgentBackgroundTaskEntity
import com.example.tielink.data.local.db.entity.HistoryEntity
import com.example.tielink.data.repository.AgentBackgroundTaskRepository
import com.example.tielink.data.repository.AgentTeamRepository
import com.example.tielink.domain.model.AgentBackgroundStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AgentBackgroundPersistenceTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }
    @After fun cleanup() { database.close() }

    @Test fun repeatedRequestCreatesOneTask() = runBlocking {
        val repository = AgentBackgroundTaskRepository(database)
        val first = repository.enqueue("整理方案", null, "stable-request")
        val second = repository.enqueue("整理方案", null, "stable-request")
        assertEquals(first.id, second.id)
        try { repository.enqueue("另一项要求", null, "stable-request"); fail("Conflicting input must be rejected") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun concurrentClaimsStillHaveOneExecutionOwner() = runBlocking {
        val dao = database.agentBackgroundTaskDao()
        dao.insert(row("a", 1)); dao.insert(row("b", 2))
        val claimed = coroutineScope { listOf(async { dao.claimNext(3) }, async { dao.claimNext(3) }).awaitAll() }
        assertEquals(1, claimed.filterNotNull().size)
        assertEquals("a", claimed.filterNotNull().single().id)
    }

    @Test fun pauseAndResumeFenceLateOutputAndRetainCompletedDraft() = runBlocking {
        val dao = database.agentBackgroundTaskDao()
        dao.insert(row("a", 1))
        val first = requireNotNull(dao.claimNext(2))
        assertEquals(1, dao.checkpoint("a", first.epoch, "render", "", "草稿", "", 3))
        dao.interrupt("a", "PAUSED", 4)
        assertEquals(0, dao.checkpoint("a", first.epoch, "saving", "", "迟到内容", "旧 HTML", 5))
        dao.resume("a", 6)
        val resumed = requireNotNull(dao.claimNext(7))
        assertEquals("草稿", resumed.document)
        assertTrue(resumed.epoch > first.epoch)
        assertEquals(0, dao.finish("a", first.epoch, "COMPLETED", "", 8))
    }

    @Test fun recoveryIsOncePerRepositoryInstance() = runBlocking {
        val dao = database.agentBackgroundTaskDao()
        dao.insert(row("a", 1)); dao.claimNext(2)
        val repository = AgentBackgroundTaskRepository(database)
        repository.initialize()
        assertEquals("PAUSED", dao.get("a")?.status)
        dao.resume("a", 3); dao.claimNext(4)
        repository.initialize()
        assertEquals("RUNNING", dao.get("a")?.status)
    }

    @Test fun supplementalRequirementsFencePreviousExecutionAtomically() = runBlocking {
        val repository = AgentBackgroundTaskRepository(database)
        val old = repository.enqueue("生成方案", null)
        val execution = requireNotNull(repository.claimNext())
        val updated = repository.enqueue("生成方案\n补充：保持一页", null, supersedesTaskId = old.id)
        val dao = database.agentBackgroundTaskDao()
        assertEquals("SUPERSEDED", dao.get(old.id)?.status)
        assertEquals(0, dao.checkpoint(old.id, execution.epoch, "render", "", "旧结果", "", 10))
        assertEquals(updated.id, repository.claimNext()?.id)
    }

    @Test fun completionRequiresStableDocumentAndHtml() = runBlocking {
        val repository = AgentBackgroundTaskRepository(database)
        repository.enqueue("整理方案", null)
        val task = requireNotNull(repository.claimNext())
        try { repository.finish(task, com.example.tielink.domain.model.AgentBackgroundStatus.COMPLETED); fail("Missing outputs") }
        catch (_: IllegalArgumentException) { }
        assertEquals("RUNNING", database.agentBackgroundTaskDao().get(task.id)?.status)
    }

    @Test fun migrationFrom15PreservesExistingHistory() = runBlocking {
        database.close()
        val name = "background-migration-${UUID.randomUUID()}.db"
        try {
            database = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            val id = database.historyDao().insert(HistoryEntity(createdAt = 1, jdRawText = "", jdTitle = "保留的会话",
                originalResume = "", polishedResume = "原始内容", jdSkills = "[]", matchNote = "保留"))
            database.openHelper.writableDatabase.apply {
                execSQL("DROP TABLE agent_teams")
                execSQL("DROP TABLE agent_background_tasks")
                version = 15
            }
            database.close()
            database = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(AppDatabase.MIGRATION_15_16, AppDatabase.MIGRATION_16_17).build()
            assertEquals("原始内容", database.historyDao().getById(id)?.polishedResume)
            database.agentBackgroundTaskDao().insert(row("new", 2))
            assertNotNull(database.agentBackgroundTaskDao().get("new"))
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun tasksRetainAcceptedTeamAfterProfileEditAndDeletion() = runBlocking {
        val teams = AgentTeamRepository(database)
        val profile = teams.save(null, "灵感团队", "整理方案", "只用已知事实", false)
        val repository = AgentBackgroundTaskRepository(database)
        val task = repository.enqueue("整理方案", null, team = profile.snapshot())
        teams.save(profile.id, "改名后", "其他用途", "不同要求", true)
        assertEquals(profile.snapshot(), repository.get(task.id)?.team)
        teams.delete(profile.id)
        assertNull(teams.get(profile.id))
        assertEquals(profile.snapshot(), repository.get(task.id)?.team)
    }

    @Test fun documentOnlyTaskCanCompleteWithoutHtml() = runBlocking {
        val profile = AgentTeamRepository(database).save(null, "文档团队", "只生成文档", "", false)
        val repository = AgentBackgroundTaskRepository(database)
        repository.enqueue("整理方案", null, team = profile.snapshot())
        val task = requireNotNull(repository.claimNext())
        assertTrue(repository.checkpoint(task, "saving", document = "完整草稿"))
        assertTrue(repository.finish(task, AgentBackgroundStatus.COMPLETED))
        val completed = requireNotNull(repository.get(task.id))
        assertEquals(AgentBackgroundStatus.COMPLETED, completed.status)
        assertFalse(completed.hasHtml)
        assertFalse(completed.includeHtml)
    }

    @Test fun repeatedTaskIdCannotChangeItsAcceptedTeam() = runBlocking {
        val profile = AgentTeamRepository(database).save(null, "团队", "用途", "", true)
        val repository = AgentBackgroundTaskRepository(database)
        repository.enqueue("整理方案", null, "team-request", team = profile.snapshot())
        try {
            repository.enqueue("整理方案", null, "team-request", team = profile.snapshot().copy(includeHtml = false))
            fail("Task identity cannot be reused with a different tool selection")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun explicitOutputPreferencePersistsIndependentlyOfTeamDefault() = runBlocking {
        val profile = AgentTeamRepository(database).save(null, "轻量团队", "文档草稿", "", false)
        val repository = AgentBackgroundTaskRepository(database)
        val htmlTask = repository.enqueue("本次也导出 HTML", null, "html-override", team = profile.snapshot(), includeHtml = true)
        assertTrue(requireNotNull(repository.get(htmlTask.id)).includeHtml)
        assertEquals(true, repository.get(htmlTask.id)?.team?.includeHtml)
        assertEquals(false, AgentTeamRepository(database).get(profile.id)?.includeHtml)
        val defaultTask = repository.enqueue("不要 HTML", null, "default-no-html", includeHtml = false)
        assertFalse(requireNotNull(repository.get(defaultTask.id)).includeHtml)
        assertNull(repository.get(defaultTask.id)?.team)
    }

    @Test fun teamLimitRejectsNewProfilesButAllowsEdits() = runBlocking {
        val teams = AgentTeamRepository(database)
        val saved = (1..20).map { teams.save(null, "团队$it", "用途", "", true) }
        try { teams.save(null, "超额团队", "用途", "", true); fail("Limit must hold") }
        catch (_: IllegalArgumentException) { }
        assertEquals("更新要求", teams.save(saved.first().id, "修改", "用途", "更新要求", false).instructions)
    }

    @Test fun migrationFrom16PreservesTasksAndDefaultsToOriginalTools() = runBlocking {
        database.close()
        val name = "team-migration-${UUID.randomUUID()}.db"
        try {
            database = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            database.openHelper.writableDatabase.apply {
                execSQL("DROP TABLE agent_teams")
                execSQL("DROP TABLE agent_background_tasks")
                AppDatabase.MIGRATION_15_16.migrate(this)
                execSQL("""INSERT INTO agent_background_tasks
                    (id, requestId, originHistoryId, title, prompt, status, stage, partialText, document, html, error, epoch, createdAt, updatedAt, readAt)
                    VALUES ('old', 'old-request', NULL, '原任务', '原要求', 'PAUSED', 'render', '', '已保存草稿', '', '', 2, 1, 2, 0)""".trimIndent())
                version = 16
            }
            database.close()
            database = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(AppDatabase.MIGRATION_16_17).build()
            val preserved = requireNotNull(database.agentBackgroundTaskDao().get("old")).toModel()
            assertEquals("已保存草稿", preserved.document)
            assertTrue(preserved.includeHtml)
            assertNull(preserved.team)
            assertNotNull(AgentTeamRepository(database).save(null, "新团队", "用途", "", false))
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    private fun row(id: String, time: Long) = AgentBackgroundTaskEntity(id, "request-$id", null,
        "草稿", "生成一页方案", createdAt = time, updatedAt = time)
}
