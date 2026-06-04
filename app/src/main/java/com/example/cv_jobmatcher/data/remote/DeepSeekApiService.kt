package com.example.cv_jobmatcher.data.remote

import com.example.cv_jobmatcher.data.remote.dto.DeepSeekRequest
import com.example.cv_jobmatcher.data.remote.dto.DeepSeekResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface DeepSeekApiService {

    @POST("v1/chat/completions")
    suspend fun chatCompletion(
        @Body request: DeepSeekRequest
    ): DeepSeekResponse
}
