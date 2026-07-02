package com.example.tielink.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

object PrefKeys {
    val API_KEY = stringPreferencesKey("deepseek_api_key")
    val LLM_MODEL = stringPreferencesKey("llm_model")
    val LLM_BASE_URL = stringPreferencesKey("llm_base_url")
    val LAST_RESUME_TEXT = stringPreferencesKey("last_resume")
    val HAS_SEEN_ONBOARDING = booleanPreferencesKey("has_seen_onboarding")

    // Ollama config
    val OLLAMA_BASE_URL = stringPreferencesKey("ollama_base_url")
    val OLLAMA_MODEL = stringPreferencesKey("ollama_model")
    val OLLAMA_EMBED_MODEL = stringPreferencesKey("ollama_embed_model")

    // AI provider selection
    val AI_PROVIDER = stringPreferencesKey("ai_provider")

    // PDF template preference
    val PDF_TEMPLATE = stringPreferencesKey("pdf_template")

    // App language
    val APP_LANGUAGE = stringPreferencesKey("app_language")

    // JD cache
    val CACHED_JD_RAW = stringPreferencesKey("cached_jd_raw")
    val CACHED_JD_JSON = stringPreferencesKey("cached_jd_json")
    val CACHED_JD_COMPANY = stringPreferencesKey("cached_jd_company")

    // Interview preference
    val LAST_INTERVIEW_PERSONA = stringPreferencesKey("last_interview_persona")

    // Agent context
    val AGENT_CONTEXT_JSON = stringPreferencesKey("agent_context_json")
    val AGENT_CHAT_DRAFT_JSON = stringPreferencesKey("agent_chat_draft_json")
    val RESUME_OPTIMIZE_CONTINUE = stringPreferencesKey("resume_optimize_continue")

    // Active provider / model selection
    val ACTIVE_PROVIDER_ID = stringPreferencesKey("active_provider_id")
    val ACTIVE_MODEL_NAME = stringPreferencesKey("active_model_name")
}

data class AppSettingsSnapshot(
    val apiKey: String = "",
    val model: String = AppPreferences.DEFAULT_MODEL,
    val baseUrl: String = AppPreferences.DEFAULT_BASE_URL,
    val lastResume: String = "",
    val hasSeenOnboarding: Boolean = false,
    val ollamaBaseUrl: String = AppPreferences.DEFAULT_OLLAMA_BASE_URL,
    val ollamaModel: String = AppPreferences.DEFAULT_OLLAMA_MODEL,
    val ollamaEmbedModel: String = AppPreferences.DEFAULT_OLLAMA_EMBED_MODEL,
    val aiProvider: String = AppPreferences.DEFAULT_AI_PROVIDER,
    val pdfTemplate: String = AppPreferences.DEFAULT_PDF_TEMPLATE,
    val appLanguage: String = AppPreferences.DEFAULT_APP_LANGUAGE,
    val cachedJdRawText: String = "",
    val cachedJdStructuredJson: String = "",
    val cachedJdCompanyName: String = "",
    val lastInterviewPersona: String = AppPreferences.DEFAULT_INTERVIEW_PERSONA,
    val agentContextJson: String = "",
    val agentChatDraftJson: String = "",
    val resumeOptimizeContinue: Boolean = false,
    val activeProviderId: Long? = null,
    val activeModelName: String? = null
)

@Singleton
class AppPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"
        const val DEFAULT_MODEL = "deepseek-chat"
        const val DEFAULT_OLLAMA_BASE_URL = "http://10.0.2.2:11434"
        const val DEFAULT_OLLAMA_MODEL = "qwen2.5:7b"
        const val DEFAULT_OLLAMA_EMBED_MODEL = "nomic-embed-text"
        const val DEFAULT_AI_PROVIDER = "deepseek"
        const val DEFAULT_PDF_TEMPLATE = "CLASSIC_SINGLE"
        const val DEFAULT_APP_LANGUAGE = "zh"
        const val DEFAULT_INTERVIEW_PERSONA = "MILD_TECH"
    }

    @Volatile
    private var snapshot = AppSettingsSnapshot()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            dataStore.data.collect { prefs ->
                snapshot = AppSettingsSnapshot(
                    apiKey = prefs[PrefKeys.API_KEY] ?: "",
                    model = prefs[PrefKeys.LLM_MODEL] ?: DEFAULT_MODEL,
                    baseUrl = prefs[PrefKeys.LLM_BASE_URL] ?: DEFAULT_BASE_URL,
                    lastResume = prefs[PrefKeys.LAST_RESUME_TEXT] ?: "",
                    hasSeenOnboarding = prefs[PrefKeys.HAS_SEEN_ONBOARDING] ?: false,
                    ollamaBaseUrl = prefs[PrefKeys.OLLAMA_BASE_URL] ?: DEFAULT_OLLAMA_BASE_URL,
                    ollamaModel = prefs[PrefKeys.OLLAMA_MODEL] ?: DEFAULT_OLLAMA_MODEL,
                    ollamaEmbedModel = prefs[PrefKeys.OLLAMA_EMBED_MODEL] ?: DEFAULT_OLLAMA_EMBED_MODEL,
                    aiProvider = prefs[PrefKeys.AI_PROVIDER] ?: DEFAULT_AI_PROVIDER,
                    pdfTemplate = prefs[PrefKeys.PDF_TEMPLATE] ?: DEFAULT_PDF_TEMPLATE,
                    appLanguage = prefs[PrefKeys.APP_LANGUAGE] ?: DEFAULT_APP_LANGUAGE,
                    cachedJdRawText = prefs[PrefKeys.CACHED_JD_RAW] ?: "",
                    cachedJdStructuredJson = prefs[PrefKeys.CACHED_JD_JSON] ?: "",
                    cachedJdCompanyName = prefs[PrefKeys.CACHED_JD_COMPANY] ?: "",
                    lastInterviewPersona = prefs[PrefKeys.LAST_INTERVIEW_PERSONA] ?: DEFAULT_INTERVIEW_PERSONA,
                    agentContextJson = prefs[PrefKeys.AGENT_CONTEXT_JSON] ?: "",
                    agentChatDraftJson = prefs[PrefKeys.AGENT_CHAT_DRAFT_JSON] ?: "",
                    resumeOptimizeContinue = prefs[PrefKeys.RESUME_OPTIMIZE_CONTINUE] == "true",
                    activeProviderId = (prefs[PrefKeys.ACTIVE_PROVIDER_ID] ?: "").toLongOrNull()?.takeIf { it > 0 },
                    activeModelName = prefs[PrefKeys.ACTIVE_MODEL_NAME]?.ifBlank { null }
                )
            }
        }
    }

    fun getApiKeyFlow(): Flow<String> = dataStore.data.map { it[PrefKeys.API_KEY] ?: "" }

    fun getModelFlow(): Flow<String> = dataStore.data.map { it[PrefKeys.LLM_MODEL] ?: DEFAULT_MODEL }

    fun getBaseUrlFlow(): Flow<String> = dataStore.data.map { it[PrefKeys.LLM_BASE_URL] ?: DEFAULT_BASE_URL }

    fun getOllamaBaseUrlFlow(): Flow<String> = dataStore.data.map { it[PrefKeys.OLLAMA_BASE_URL] ?: DEFAULT_OLLAMA_BASE_URL }

    fun getOllamaModelFlow(): Flow<String> = dataStore.data.map { it[PrefKeys.OLLAMA_MODEL] ?: DEFAULT_OLLAMA_MODEL }

    fun getOllamaEmbedModelFlow(): Flow<String> = dataStore.data.map { it[PrefKeys.OLLAMA_EMBED_MODEL] ?: DEFAULT_OLLAMA_EMBED_MODEL }

    fun getAiProviderFlow(): Flow<String> = dataStore.data.map { it[PrefKeys.AI_PROVIDER] ?: DEFAULT_AI_PROVIDER }

    fun getActiveProviderIdFlow(): Flow<Long?> = dataStore.data.map { prefs ->
        val id = prefs[PrefKeys.ACTIVE_PROVIDER_ID] ?: ""
        id.toLongOrNull()?.takeIf { it > 0 }
    }

    fun getActiveModelNameFlow(): Flow<String?> = dataStore.data.map { prefs ->
        prefs[PrefKeys.ACTIVE_MODEL_NAME]?.ifBlank { null }
    }

    fun snapshot(): AppSettingsSnapshot = snapshot

    suspend fun getApiKey(): String = snapshot.apiKey

    suspend fun setApiKey(key: String) {
        snapshot = snapshot.copy(apiKey = key)
        dataStore.edit { prefs ->
            prefs[PrefKeys.API_KEY] = key
        }
    }

    suspend fun getModel(): String = snapshot.model

    suspend fun setModel(model: String) {
        snapshot = snapshot.copy(model = model)
        dataStore.edit { prefs ->
            prefs[PrefKeys.LLM_MODEL] = model
        }
    }

    fun getBaseUrlSync(): String = snapshot.baseUrl

    suspend fun getBaseUrl(): String = snapshot.baseUrl

    suspend fun setBaseUrl(url: String) {
        snapshot = snapshot.copy(baseUrl = url)
        dataStore.edit { prefs ->
            prefs[PrefKeys.LLM_BASE_URL] = url
        }
    }

    suspend fun getLastResume(): String = snapshot.lastResume

    suspend fun setLastResume(text: String) {
        snapshot = snapshot.copy(lastResume = text)
        dataStore.edit { prefs ->
            prefs[PrefKeys.LAST_RESUME_TEXT] = text
        }
    }

    suspend fun hasSeenOnboarding(): Boolean = snapshot.hasSeenOnboarding

    suspend fun setOnboardingSeen() {
        snapshot = snapshot.copy(hasSeenOnboarding = true)
        dataStore.edit { prefs ->
            prefs[PrefKeys.HAS_SEEN_ONBOARDING] = true
        }
    }

    suspend fun getOllamaBaseUrl(): String = snapshot.ollamaBaseUrl

    suspend fun setOllamaBaseUrl(url: String) {
        snapshot = snapshot.copy(ollamaBaseUrl = url)
        dataStore.edit { prefs ->
            prefs[PrefKeys.OLLAMA_BASE_URL] = url
        }
    }

    suspend fun getOllamaModel(): String = snapshot.ollamaModel

    suspend fun setOllamaModel(model: String) {
        snapshot = snapshot.copy(ollamaModel = model)
        dataStore.edit { prefs ->
            prefs[PrefKeys.OLLAMA_MODEL] = model
        }
    }

    suspend fun getOllamaEmbedModel(): String = snapshot.ollamaEmbedModel

    suspend fun setOllamaEmbedModel(model: String) {
        snapshot = snapshot.copy(ollamaEmbedModel = model)
        dataStore.edit { prefs ->
            prefs[PrefKeys.OLLAMA_EMBED_MODEL] = model
        }
    }

    suspend fun getAiProvider(): String = snapshot.aiProvider

    suspend fun setAiProvider(provider: String) {
        snapshot = snapshot.copy(aiProvider = provider)
        dataStore.edit { prefs ->
            prefs[PrefKeys.AI_PROVIDER] = provider
        }
    }

    suspend fun getPdfTemplate(): String = snapshot.pdfTemplate

    suspend fun setPdfTemplate(template: String) {
        snapshot = snapshot.copy(pdfTemplate = template)
        dataStore.edit { prefs ->
            prefs[PrefKeys.PDF_TEMPLATE] = template
        }
    }

    suspend fun getAppLanguage(): String = snapshot.appLanguage

    suspend fun setAppLanguage(language: String) {
        snapshot = snapshot.copy(appLanguage = language)
        dataStore.edit { prefs ->
            prefs[PrefKeys.APP_LANGUAGE] = language
        }
    }

    suspend fun getCachedJdRawText(): String = snapshot.cachedJdRawText

    suspend fun setCachedJdRawText(text: String) {
        snapshot = snapshot.copy(cachedJdRawText = text)
        dataStore.edit { prefs ->
            prefs[PrefKeys.CACHED_JD_RAW] = text
        }
    }

    suspend fun getCachedJdStructuredJson(): String = snapshot.cachedJdStructuredJson

    suspend fun setCachedJdStructuredJson(json: String) {
        snapshot = snapshot.copy(cachedJdStructuredJson = json)
        dataStore.edit { prefs ->
            prefs[PrefKeys.CACHED_JD_JSON] = json
        }
    }

    suspend fun getCachedJdCompanyName(): String = snapshot.cachedJdCompanyName

    suspend fun setCachedJdCompanyName(name: String) {
        snapshot = snapshot.copy(cachedJdCompanyName = name)
        dataStore.edit { prefs ->
            prefs[PrefKeys.CACHED_JD_COMPANY] = name
        }
    }

    suspend fun getLastInterviewPersona(): String = snapshot.lastInterviewPersona

    suspend fun setLastInterviewPersona(persona: String) {
        snapshot = snapshot.copy(lastInterviewPersona = persona)
        dataStore.edit { prefs ->
            prefs[PrefKeys.LAST_INTERVIEW_PERSONA] = persona
        }
    }

    suspend fun getAgentContextJson(): String = snapshot.agentContextJson

    suspend fun setAgentContextJson(json: String) {
        snapshot = snapshot.copy(agentContextJson = json)
        dataStore.edit { prefs ->
            prefs[PrefKeys.AGENT_CONTEXT_JSON] = json
        }
    }

    suspend fun getAgentChatDraftJson(): String = snapshot.agentChatDraftJson

    suspend fun setAgentChatDraftJson(json: String) {
        snapshot = snapshot.copy(agentChatDraftJson = json)
        dataStore.edit { prefs ->
            prefs[PrefKeys.AGENT_CHAT_DRAFT_JSON] = json
        }
    }

    fun getResumeOptimizeContinueFlow(): Flow<Boolean> {
        return dataStore.data.map { prefs -> prefs[PrefKeys.RESUME_OPTIMIZE_CONTINUE] == "true" }
    }

    suspend fun shouldContinueResumeOptimize(): Boolean = snapshot.resumeOptimizeContinue

    suspend fun setResumeOptimizeContinue(continueFlow: Boolean) {
        snapshot = snapshot.copy(resumeOptimizeContinue = continueFlow)
        dataStore.edit { prefs ->
            prefs[PrefKeys.RESUME_OPTIMIZE_CONTINUE] = if (continueFlow) "true" else ""
        }
    }

    suspend fun clearResumeOptimizeContinue() {
        setResumeOptimizeContinue(false)
    }

    suspend fun getActiveProviderId(): Long? = snapshot.activeProviderId

    suspend fun setActiveProviderId(id: Long?) {
        snapshot = snapshot.copy(activeProviderId = id?.takeIf { it > 0 })
        dataStore.edit { prefs ->
            prefs[PrefKeys.ACTIVE_PROVIDER_ID] = id?.toString() ?: ""
        }
    }

    suspend fun getActiveModelName(): String? = snapshot.activeModelName

    suspend fun setActiveModelName(name: String?) {
        snapshot = snapshot.copy(activeModelName = name?.ifBlank { null })
        dataStore.edit { prefs ->
            prefs[PrefKeys.ACTIVE_MODEL_NAME] = name ?: ""
        }
    }
}
