package com.example.cv_jobmatcher.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

object PrefKeys {
    val API_KEY = stringPreferencesKey("deepseek_api_key")
    val LLM_MODEL = stringPreferencesKey("llm_model")
    val LLM_BASE_URL = stringPreferencesKey("llm_base_url")
    val LAST_RESUME_TEXT = stringPreferencesKey("last_resume")
    val HAS_SEEN_ONBOARDING = stringPreferencesKey("has_seen_onboarding")
}

@Singleton
class AppPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"
        const val DEFAULT_MODEL = "deepseek-chat"
    }

    // ── API Key ────────────────────────────────────────────────

    @Volatile
    private var cachedApiKey: String? = null

    init {
        // Eagerly load API key into volatile cache for the OkHttp interceptor
        runBlocking { cachedApiKey = getApiKey() }
    }

    fun getApiKeyFlow(): Flow<String> {
        return dataStore.data.map { prefs ->
            prefs[PrefKeys.API_KEY] ?: ""
        }
    }

    suspend fun getApiKey(): String {
        val key = dataStore.data.first()[PrefKeys.API_KEY] ?: ""
        cachedApiKey = key
        return key
    }

    suspend fun setApiKey(key: String) {
        cachedApiKey = key
        dataStore.edit { prefs ->
            prefs[PrefKeys.API_KEY] = key
        }
    }

    // ── Model ──────────────────────────────────────────────────

    suspend fun getModel(): String {
        return dataStore.data.first()[PrefKeys.LLM_MODEL] ?: DEFAULT_MODEL
    }

    suspend fun setModel(model: String) {
        dataStore.edit { prefs ->
            prefs[PrefKeys.LLM_MODEL] = model
        }
    }

    // ── Base URL ───────────────────────────────────────────────

    fun getBaseUrlSync(): String = DEFAULT_BASE_URL

    suspend fun getBaseUrl(): String {
        return dataStore.data.first()[PrefKeys.LLM_BASE_URL] ?: DEFAULT_BASE_URL
    }

    suspend fun setBaseUrl(url: String) {
        dataStore.edit { prefs ->
            prefs[PrefKeys.LLM_BASE_URL] = url
        }
    }

    // ── Last Resume (cache) ────────────────────────────────────

    suspend fun getLastResume(): String {
        return dataStore.data.first()[PrefKeys.LAST_RESUME_TEXT] ?: ""
    }

    suspend fun setLastResume(text: String) {
        dataStore.edit { prefs ->
            prefs[PrefKeys.LAST_RESUME_TEXT] = text
        }
    }

    // ── Onboarding ─────────────────────────────────────────────

    suspend fun hasSeenOnboarding(): Boolean {
        return dataStore.data.first()[PrefKeys.HAS_SEEN_ONBOARDING] == "true"
    }

    suspend fun setOnboardingSeen() {
        dataStore.edit { prefs ->
            prefs[PrefKeys.HAS_SEEN_ONBOARDING] = "true"
        }
    }
}
