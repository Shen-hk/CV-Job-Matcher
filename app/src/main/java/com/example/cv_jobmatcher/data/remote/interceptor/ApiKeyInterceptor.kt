package com.example.cv_jobmatcher.data.remote.interceptor

import com.example.cv_jobmatcher.data.local.AppPreferences
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

class ApiKeyInterceptor(
    private val appPreferences: AppPreferences
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        // Read cached API key synchronously.
        // AppPreferences maintains a @Volatile cache updated on app start.
        val apiKey = runBlocking { appPreferences.getApiKey() }

        val originalRequest = chain.request()
        val authenticatedRequest = if (apiKey.isNotBlank()) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $apiKey")
                .build()
        } else {
            originalRequest
        }

        return chain.proceed(authenticatedRequest)
    }
}
