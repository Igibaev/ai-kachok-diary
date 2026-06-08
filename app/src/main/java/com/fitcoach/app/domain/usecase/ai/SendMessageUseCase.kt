package com.fitcoach.app.domain.usecase.ai

import com.fitcoach.app.data.remote.api.AnthropicApi
import com.fitcoach.app.data.remote.dto.ApiMessage
import com.fitcoach.app.data.remote.dto.MessageRequest
import com.fitcoach.app.domain.model.ChatMessage
import javax.inject.Inject

class SendMessageUseCase @Inject constructor(
    private val api: AnthropicApi
) {
    suspend operator fun invoke(
        apiKey: String,
        systemPrompt: String,
        history: List<ChatMessage>,
        userMessage: String
    ): Result<String> = runCatching {
        val messages = history.takeLast(20).map {
            ApiMessage(role = it.role, content = it.content)
        } + ApiMessage(role = "user", content = userMessage)

        val response = api.createMessage(
            apiKey = apiKey,
            request = MessageRequest(
                system = systemPrompt,
                messages = messages
            )
        )
        response.content.firstOrNull()?.text ?: "Нет ответа"
    }
}
