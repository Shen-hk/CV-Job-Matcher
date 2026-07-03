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

    fun decode(json: String): PersistedAgentChatDraft? {
        if (json.isBlank()) return null
        return runCatching { adapter.fromJson(json) }.getOrNull()
    }

    fun encode(draft: PersistedAgentChatDraft): String = adapter.toJson(draft)

    suspend fun load(): PersistedAgentChatDraft {
        val json = appPreferences.getAgentChatDraftJson()
        if (json.isBlank()) return PersistedAgentChatDraft()
        return decode(json) ?: PersistedAgentChatDraft()
    }

    suspend fun save(draft: PersistedAgentChatDraft) {
        appPreferences.setAgentChatDraftJson(encode(draft))
    }

    suspend fun clear() {
        appPreferences.setAgentChatDraftJson("")
    }
}
