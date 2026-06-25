package com.example.tielink.data.remote

import com.example.tielink.data.remote.dto.DeepSeekRequest
import com.example.tielink.data.remote.dto.DeepSeekResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface DeepSeekApiService {

    @POST("v1/chat/completions")
    suspend fun chatCompletion(
        @Body request: DeepSeekRequest
    ): DeepSeekResponse
}
