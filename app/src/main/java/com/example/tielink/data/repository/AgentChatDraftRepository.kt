package com.example.tielink.data.repository

import com.example.tielink.data.local.AppPreferences
import com.example.tielink.domain.model.PersistedAgentChatDraft
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentChatDraftRepository @Inject constructor(
    private val appPreferences: AppPreferences
) {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val adapter = moshi.adapter(PersistedAgentChatDraft::class.java)

    suspend fun load(): PersistedAgentChatDraft {
        val json = appPreferences.getAgentChatDraftJson()
        if (json.isBlank()) return PersistedAgentChatDraft()
        return try {
            adapter.fromJson(json) ?: PersistedAgentChatDraft()
        } catch (_: Exception) {
            PersistedAgentChatDraft()
        }
    }

    suspend fun save(draft: PersistedAgentChatDraft) {
        appPreferences.setAgentChatDraftJson(adapter.toJson(draft))
    }

    suspend fun clear() {
        appPreferences.setAgentChatDraftJson("")
    }
}
