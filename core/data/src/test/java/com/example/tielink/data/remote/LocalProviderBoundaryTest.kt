package com.example.tielink.data.remote

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import com.example.tielink.data.local.AppPreferences
import com.example.tielink.data.local.PrefKeys
import com.example.tielink.data.local.SecretCipher
import com.example.tielink.data.remote.dto.Message
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test

class LocalProviderBoundaryTest {
    private class LocalSettings : DataStore<Preferences> {
        override val data = MutableStateFlow(preferencesOf(
            PrefKeys.AI_PROVIDER to "local",
            // Even with network services configured, local must reject chat before creating a request.
            PrefKeys.OLLAMA_BASE_URL to "http://127.0.0.1:1"
        ))
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }

    private fun manager(): AiProviderManager {
        val preferences = AppPreferences(LocalSettings(), SecretCipher())
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val deepSeek = DeepSeekProvider(DeepSeekApiServiceFactory(OkHttpClient(), moshi, preferences), preferences)
        return AiProviderManager(deepSeek, OllamaProvider(preferences), preferences)
    }

    private val request = LlmRequest(messages = listOf(Message(role = "user", content = "改写简历")))

    @Test fun localRejectsNonStreamingChat() = runBlocking {
        expectLocalRejection { manager().chatWithFallback(request) }
    }

    @Test fun localRejectsStreamingChat() = runBlocking {
        expectLocalRejection { manager().chatStream(request).toList() }
    }

    @Test fun legacyStreamingEntryAlsoHonorsLocalSelection() = runBlocking {
        expectLocalRejection { manager().chatWithFallbackStream(request).toList() }
    }

    private suspend fun expectLocalRejection(block: suspend () -> Unit) {
        try {
            block()
            fail("Local mode must not attempt a network chat")
        } catch (error: UnsupportedOperationException) {
            assertEquals(AiProviderManager.LOCAL_CHAT_UNAVAILABLE, error.message)
        }
    }
}
