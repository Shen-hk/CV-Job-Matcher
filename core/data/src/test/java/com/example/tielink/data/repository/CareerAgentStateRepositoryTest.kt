package com.example.tielink.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import com.example.tielink.data.local.AppPreferences
import com.example.tielink.data.local.PrefKeys
import com.example.tielink.data.local.SecretCipher
import com.example.tielink.domain.model.CareerObservation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.*
import org.junit.Test

class CareerAgentStateRepositoryTest {
    private class MemoryDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        private val mutex = Mutex()
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
            transform(state.value).also { state.value = it }
        }
    }

    @Test fun concurrentTransitionsDoNotOverwriteEachOther() = runBlocking {
        val preferences = AppPreferences(MemoryDataStore(), SecretCipher())
        val repository = CareerAgentStateRepository(preferences)
        (1..30).map { id -> async(Dispatchers.Default) {
            repository.updateState { it.copy(observations = it.observations + CareerObservation("$id", "test", "result-$id", createdAt = id.toLong())) }
        } }.awaitAll()
        val state = repository.getState()
        assertEquals(30L, state.revision)
        assertEquals(30, state.observations.size)
        assertEquals(30, state.observations.map { it.id }.toSet().size)
    }

    @Test fun staleSnapshotCannotOverwriteNewerState() = runBlocking {
        val repository = CareerAgentStateRepository(AppPreferences(MemoryDataStore(), SecretCipher()))
        val old = repository.getState()
        repository.updateState { it.copy(updatedAt = 100) }
        try {
            repository.saveState(old.copy(updatedAt = 200))
            fail("A stale write must be rejected")
        } catch (_: IllegalArgumentException) { }
        assertEquals(100L, repository.getState().updatedAt)
        assertEquals(1L, repository.getState().revision)
    }

    @Test fun contextIsAvailableBeforeTheAsyncSettingsSnapshotInitializes() = runBlocking {
        val dataStore = MemoryDataStore()
        dataStore.edit { it[PrefKeys.AGENT_CONTEXT_JSON] = "{\"currentJdId\":42,\"currentJdText\":\"Android JD\"}" }
        val repository = AgentContextRepository(AppPreferences(dataStore, SecretCipher()))
        assertEquals(42L, repository.getAgentContext().currentJdId)
        assertEquals("Android JD", repository.getAgentContext().currentJdText)
    }

    @Test fun simultaneousContextUpdatesKeepBothJdAndConversationSummary() = runBlocking {
        val repository = AgentContextRepository(AppPreferences(MemoryDataStore(), SecretCipher()))
        listOf(
            async(Dispatchers.Default) { repository.updateAgentContext(currentJdId = 42, currentJdText = "Android JD") },
            async(Dispatchers.Default) { repository.updateAgentContext(conversationSummary = "已建立匹配基线") }
        ).awaitAll()
        val context = repository.getAgentContext()
        assertEquals(42L, context.currentJdId)
        assertEquals("Android JD", context.currentJdText)
        assertEquals("已建立匹配基线", context.conversationSummary)
    }
}
