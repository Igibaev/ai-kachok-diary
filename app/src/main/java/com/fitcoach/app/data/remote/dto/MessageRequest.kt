package com.fitcoach.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Тело запроса Anthropic Messages API (POST /v1/messages). Модель берётся из BrandConfig.aiModel. */
@Serializable
data class MessageRequest(
    val model: String = "claude-opus-5",
    @SerialName("max_tokens") val maxTokens: Int = 2048,
    val system: String,
    val messages: List<ApiMessage>
)

@Serializable
data class ApiMessage(
    val role: String,
    val content: String
)
