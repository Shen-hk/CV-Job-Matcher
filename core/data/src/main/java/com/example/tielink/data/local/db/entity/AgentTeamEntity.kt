package com.example.tielink.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.tielink.domain.model.AgentTeamProfile

@Entity(tableName = "agent_teams")
data class AgentTeamEntity(
    @PrimaryKey val id: String,
    val name: String,
    val brief: String,
    val instructions: String,
    val includeHtml: Boolean,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toModel() = AgentTeamProfile(id, name, brief, instructions, includeHtml, createdAt, updatedAt)
}
