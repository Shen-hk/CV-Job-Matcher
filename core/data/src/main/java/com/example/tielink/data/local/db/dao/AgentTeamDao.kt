package com.example.tielink.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.tielink.data.local.db.entity.AgentTeamEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AgentTeamDao {
    @Query("SELECT * FROM agent_teams ORDER BY updatedAt DESC, id ASC")
    abstract fun observeAll(): Flow<List<AgentTeamEntity>>

    @Query("SELECT * FROM agent_teams WHERE id = :id")
    abstract suspend fun get(id: String): AgentTeamEntity?

    @Query("SELECT COUNT(*) FROM agent_teams")
    protected abstract suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsert(team: AgentTeamEntity)

    @Transaction
    open suspend fun save(team: AgentTeamEntity, limit: Int) {
        require(get(team.id) != null || count() < limit) { "最多保存 $limit 个团队，请先整理已有团队。" }
        upsert(team)
    }

    @Query("DELETE FROM agent_teams WHERE id = :id")
    abstract suspend fun delete(id: String)
}
