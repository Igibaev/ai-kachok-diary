package com.fitcoach.app.data.remote.api

import com.fitcoach.app.data.remote.dto.MessageRequest
import com.fitcoach.app.data.remote.dto.MessageResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

interface AnthropicApi {
    @POST("v1/messages")
    @Headers(
        "anthropic-version: 2023-06-01",
        "content-type: application/json"
    )
    suspend fun createMessage(
        @Header("x-api-key") apiKey: String,
        @Body request: MessageRequest
    ): MessageResponse
}
