package com.example.tielink.data.remote

import com.example.tielink.data.local.AppPreferences
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeepSeekApiServiceFactory @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi,
    private val appPreferences: AppPreferences
) {
    fun create(): DeepSeekApiService {
        val baseUrl = normalizeBaseUrl(appPreferences.getBaseUrlSync())
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(DeepSeekApiService::class.java)
    }

    private fun normalizeBaseUrl(baseUrl: String): String {
        val fallback = AppPreferences.DEFAULT_BASE_URL
        val url = if (baseUrl.isBlank()) fallback else baseUrl.trim()
        return if (url.endsWith("/")) url else "$url/"
    }
}
