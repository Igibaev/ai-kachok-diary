package com.fitcoach.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MessageRequest(
    val model: String = "claude-opus-4-5",
    @SerialName("max_tokens") val maxTokens: Int = 1024,
    val system: String,
    val messages: List<ApiMessage>
)

@Serializable
data class ApiMessage(
    val role: String,
    val content: String
)
