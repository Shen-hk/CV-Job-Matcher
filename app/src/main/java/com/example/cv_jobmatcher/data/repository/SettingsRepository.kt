package com.example.cv_jobmatcher.data.repository

import com.example.cv_jobmatcher.data.local.AppPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val appPreferences: AppPreferences
) {
    suspend fun getApiKey(): String = appPreferences.getApiKey()

    suspend fun setApiKey(key: String) = appPreferences.setApiKey(key)

    suspend fun getModel(): String = appPreferences.getModel()

    suspend fun setModel(model: String) = appPreferences.setModel(model)

    suspend fun getBaseUrl(): String = appPreferences.getBaseUrl()

    suspend fun setBaseUrl(url: String) = appPreferences.setBaseUrl(url)

    suspend fun hasSeenOnboarding(): Boolean = appPreferences.hasSeenOnboarding()

    suspend fun setOnboardingSeen() = appPreferences.setOnboardingSeen()

    suspend fun getLastResume(): String = appPreferences.getLastResume()

    suspend fun setLastResume(text: String) = appPreferences.setLastResume(text)
}
