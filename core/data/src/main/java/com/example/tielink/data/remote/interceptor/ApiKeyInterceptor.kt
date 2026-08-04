package com.example.tielink.data.remote.interceptor

import com.example.tielink.data.local.AppPreferences
import okhttp3.Interceptor
import okhttp3.Response

class ApiKeyInterceptor(
    private val appPreferences: AppPreferences
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val apiKey = appPreferences.snapshot().apiKey

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
