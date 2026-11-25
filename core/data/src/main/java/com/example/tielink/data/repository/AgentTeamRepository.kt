package com.example.tielink.data.repository

import com.example.tielink.data.local.db.AppDatabase
import com.example.tielink.data.local.db.entity.AgentTeamEntity
import com.example.tielink.domain.agent.AgentTeamPolicy
import com.example.tielink.domain.model.AgentTeamProfile
import com.example.tielink.domain.model.AgentTeamSnapshot
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentTeamRepository @Inject constructor(database: AppDatabase) {
    private val dao = database.agentTeamDao()
    val teams = dao.observeAll().map { rows -> rows.map { it.toModel() } }

    suspend fun get(id: String): AgentTeamProfile? = dao.get(id)?.toModel()

    suspend fun save(id: String?, name: String, brief: String, instructions: String, includeHtml: Boolean): AgentTeamProfile {
        val previous = id?.let { requireNotNull(dao.get(it)) { "团队已删除，请返回列表重新创建。" } }
        val key = id ?: UUID.randomUUID().toString()
        val snapshot = AgentTeamSnapshot(key, name.trim(), brief.trim(), instructions.trim(), includeHtml)
        AgentTeamPolicy.validate(snapshot)
        val now = System.currentTimeMillis()
        val team = AgentTeamEntity(key, snapshot.name, snapshot.brief, snapshot.instructions, includeHtml,
            previous?.createdAt ?: now, now)
        dao.save(team, AgentTeamPolicy.MAX_TEAMS)
        return team.toModel()
    }

    suspend fun delete(id: String) = dao.delete(id)
}
