package com.fitcoach.app.ai.dto

import kotlinx.serialization.Serializable

/** Тело запроса к прокси клуба: POST {aiProxyUrl}/v1/chat (см. proxy/README.md). */
@Serializable
data class ProxyChatRequest(
    val system: String,
    val messages: List<ProxyMessage>,
    val locale: String,
    val deviceId: String
)

@Serializable
data class ProxyMessage(
    val role: String,
    val content: String
)

/** Ответ прокси. При 429 в `text` лежит готовая фраза для пользователя. */
@Serializable
data class ProxyChatResponse(
    val text: String = "",
    val model: String = "",
    val usage: ProxyUsage? = null,
    val error: String? = null
)

@Serializable
data class ProxyUsage(
    val input_tokens: Int = 0,
    val output_tokens: Int = 0
)
