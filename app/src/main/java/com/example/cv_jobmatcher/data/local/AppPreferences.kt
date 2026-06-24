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
    
    // Ollama配置
    val OLLAMA_BASE_URL = stringPreferencesKey("ollama_base_url")
    val OLLAMA_MODEL = stringPreferencesKey("ollama_model")
    val OLLAMA_EMBED_MODEL = stringPreferencesKey("ollama_embed_model")
    
    // AI Provider选择
    val AI_PROVIDER = stringPreferencesKey("ai_provider") // "deepseek" | "ollama" | "local"
    
    // PDF模板偏好
    val PDF_TEMPLATE = stringPreferencesKey("pdf_template")
    
    // 语言设置
    val APP_LANGUAGE = stringPreferencesKey("app_language") // "zh" | "en"

    // JD缓存
    val CACHED_JD_RAW = stringPreferencesKey("cached_jd_raw")
    val CACHED_JD_JSON = stringPreferencesKey("cached_jd_json")
    val CACHED_JD_COMPANY = stringPreferencesKey("cached_jd_company")

    // 面试偏好
    val LAST_INTERVIEW_PERSONA = stringPreferencesKey("last_interview_persona")

    // Agent Context
    val AGENT_CONTEXT_JSON = stringPreferencesKey("agent_context_json")
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

    // ── Ollama配置 ────────────────────────────────────────────

    suspend fun getOllamaBaseUrl(): String {
        return dataStore.data.first()[PrefKeys.OLLAMA_BASE_URL] ?: "http://10.0.2.2:11434"
    }

    suspend fun setOllamaBaseUrl(url: String) {
        dataStore.edit { prefs ->
            prefs[PrefKeys.OLLAMA_BASE_URL] = url
        }
    }

    suspend fun getOllamaModel(): String {
        return dataStore.data.first()[PrefKeys.OLLAMA_MODEL] ?: "qwen2.5:7b"
    }

    suspend fun setOllamaModel(model: String) {
        dataStore.edit { prefs ->
            prefs[PrefKeys.OLLAMA_MODEL] = model
        }
    }

    suspend fun getOllamaEmbedModel(): String {
        return dataStore.data.first()[PrefKeys.OLLAMA_EMBED_MODEL] ?: "nomic-embed-text"
    }

    suspend fun setOllamaEmbedModel(model: String) {
        dataStore.edit { prefs ->
            prefs[PrefKeys.OLLAMA_EMBED_MODEL] = model
        }
    }

    // ── AI Provider ───────────────────────────────────────────

    suspend fun getAiProvider(): String {
        return dataStore.data.first()[PrefKeys.AI_PROVIDER] ?: "deepseek"
    }

    suspend fun setAiProvider(provider: String) {
        dataStore.edit { prefs ->
            prefs[PrefKeys.AI_PROVIDER] = provider
        }
    }

    // ── PDF模板 ───────────────────────────────────────────────

    suspend fun getPdfTemplate(): String {
        return dataStore.data.first()[PrefKeys.PDF_TEMPLATE] ?: "CLASSIC_SINGLE"
    }

    suspend fun setPdfTemplate(template: String) {
        dataStore.edit { prefs ->
            prefs[PrefKeys.PDF_TEMPLATE] = template
        }
    }

    // ── 语言设置 ─────────────────────────────────────────────

    suspend fun getAppLanguage(): String {
        return dataStore.data.first()[PrefKeys.APP_LANGUAGE] ?: "zh"
    }

    suspend fun setAppLanguage(language: String) {
        dataStore.edit { prefs ->
            prefs[PrefKeys.APP_LANGUAGE] = language
        }
    }

    // ── JD 缓存 ──────────────────────────────────────────────

    suspend fun getCachedJdRawText(): String {
        return dataStore.data.first()[PrefKeys.CACHED_JD_RAW] ?: ""
    }

    suspend fun setCachedJdRawText(text: String) {
        dataStore.edit { prefs ->
            prefs[PrefKeys.CACHED_JD_RAW] = text
        }
    }

    suspend fun getCachedJdStructuredJson(): String {
        return dataStore.data.first()[PrefKeys.CACHED_JD_JSON] ?: ""
    }

    suspend fun setCachedJdStructuredJson(json: String) {
        dataStore.edit { prefs ->
            prefs[PrefKeys.CACHED_JD_JSON] = json
        }
    }

    suspend fun getCachedJdCompanyName(): String {
        return dataStore.data.first()[PrefKeys.CACHED_JD_COMPANY] ?: ""
    }

    suspend fun setCachedJdCompanyName(name: String) {
        dataStore.edit { prefs ->
            prefs[PrefKeys.CACHED_JD_COMPANY] = name
        }
    }

    // ── 面试偏好 ──────────────────────────────────────────────

    suspend fun getLastInterviewPersona(): String {
        return dataStore.data.first()[PrefKeys.LAST_INTERVIEW_PERSONA] ?: "MILD_TECH"
    }

    suspend fun setLastInterviewPersona(persona: String) {
        dataStore.edit { prefs ->
            prefs[PrefKeys.LAST_INTERVIEW_PERSONA] = persona
        }
    }

    // ── Agent Context ───────────────────────────────────────────

    suspend fun getAgentContextJson(): String {
        return dataStore.data.first()[PrefKeys.AGENT_CONTEXT_JSON] ?: ""
    }

    suspend fun setAgentContextJson(json: String) {
        dataStore.edit { prefs ->
            prefs[PrefKeys.AGENT_CONTEXT_JSON] = json
        }
    }
}
