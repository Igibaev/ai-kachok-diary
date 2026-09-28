package com.fitcoach.app.ai

import com.fitcoach.app.data.remote.api.AnthropicApi
import com.fitcoach.app.data.remote.dto.ApiMessage
import com.fitcoach.app.data.remote.dto.MessageRequest
import com.fitcoach.app.domain.model.ChatMessage
import com.fitcoach.app.domain.repository.UserRepository
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Прямой вызов Anthropic Messages API ключом разработчика.
 * Доступен только в DEBUG-сборках (см. [AiClientSelector]) — в релизе ключ в приложении не хранится.
 */
@Singleton
class DirectAnthropicClient @Inject constructor(
    private val api: AnthropicApi,
    private val userRepository: UserRepository,
    private val brand: AiBrand
) : AiClient {

    override val label: String = "Anthropic API (${brand.model})"

    override suspend fun chat(
        system: String,
        history: List<ChatMessage>,
        userMessage: String,
        locale: String
    ): Result<String> {
        val apiKey = userRepository.getApiKey()
        if (apiKey.isBlank()) return Result.failure(AiException(AiErrors.noApiKey(locale)))

        val messages = HistorySanitizer.build(history, userMessage).map { ApiMessage(it.role, it.content) }
        val request = MessageRequest(
            model = brand.model,
            maxTokens = MAX_TOKENS,
            system = system,
            messages = messages
        )
        return try {
            val response = api.createMessage(apiKey = apiKey, request = request)
            if (response.stopReason == "refusal") {
                return Result.failure(AiException(AiErrors.badRequest(locale)))
            }
            val text = response.content
                .filter { it.type == "text" }
                .joinToString("\n") { it.text }
                .trim()
            if (text.isEmpty()) Result.failure(AiException(AiErrors.emptyAnswer(locale)))
            else Result.success(text)
        } catch (e: HttpException) {
            val msg = when (e.code()) {
                401, 403 -> AiErrors.unauthorized(locale)
                429 -> AiErrors.rateLimited(locale)
                in 400..499 -> AiErrors.badRequest(locale)
                else -> AiErrors.serverUnavailable(locale)
            }
            Result.failure(AiException(msg, e))
        } catch (e: IOException) {
            Result.failure(AiException(AiErrors.network(locale), e))
        } catch (e: IllegalStateException) {
            Result.failure(AiException(AiErrors.serverUnavailable(locale), e))
        }
    }

    private companion object {
        const val MAX_TOKENS = 2048
    }
}
