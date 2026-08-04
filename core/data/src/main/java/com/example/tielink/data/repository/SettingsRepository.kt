package com.example.tielink.data.repository

import com.example.tielink.data.local.AppPreferences
import com.example.tielink.data.remote.DeepSeekApiServiceFactory
import com.example.tielink.data.remote.dto.DeepSeekRequest
import com.example.tielink.data.remote.dto.Message
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val appPreferences: AppPreferences,
    private val apiServiceFactory: DeepSeekApiServiceFactory
) {
    suspend fun getApiKey(): String = appPreferences.getApiKey()

    suspend fun setApiKey(key: String) = appPreferences.setApiKey(key)

    suspend fun getModel(): String = appPreferences.getModel()

    suspend fun setModel(model: String) = appPreferences.setModel(model)

    suspend fun getBaseUrl(): String = appPreferences.getBaseUrl()

    suspend fun setBaseUrl(url: String) = appPreferences.setBaseUrl(url)

    suspend fun hasSeenOnboarding(): Boolean = appPreferences.hasSeenOnboarding()

    suspend fun setOnboardingSeen() = appPreferences.setOnboardingSeen()

    suspend fun testConnection(): Result<Unit> = runCatching {
        val response = apiServiceFactory.create().chatCompletion(
            DeepSeekRequest(messages = listOf(Message(role = "user", content = "Hello")), maxTokens = 10)
        )
        check(response.choices.isNotEmpty()) { "Provider returned no choices" }
    }
}
