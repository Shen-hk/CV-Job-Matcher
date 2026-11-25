package com.example.tielink.domain.model

/** A reusable work profile. Selecting or saving one never starts a task. */
data class AgentTeamProfile(
    val id: String,
    val name: String,
    val brief: String,
    val instructions: String,
    val includeHtml: Boolean,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun snapshot() = AgentTeamSnapshot(id, name, brief, instructions, includeHtml)
}

/** Immutable requirements captured at acceptance, independent of later profile edits/deletion. */
data class AgentTeamSnapshot(
    val id: String,
    val name: String,
    val brief: String,
    val instructions: String,
    val includeHtml: Boolean
)
