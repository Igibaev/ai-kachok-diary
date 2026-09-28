package com.fitcoach.app.ai

import com.fitcoach.app.ai.dto.ProxyChatRequest
import com.fitcoach.app.ai.dto.ProxyChatResponse
import com.fitcoach.app.ai.dto.ProxyMessage
import com.fitcoach.app.brand.BrandConfig
import com.fitcoach.app.domain.model.ChatMessage
import com.fitcoach.app.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Клиент AI-прокси клуба (Cloudflare Worker из папки proxy/).
 * Ключ Anthropic хранится у клуба; приложение шлёт только токен приложения.
 *
 * Контракт: POST {aiProxyUrl}/v1/chat, заголовки X-App-Token, X-Device-Id,
 * тело {system, messages[{role, content}], locale, deviceId}; ответ {text, model, usage?}.
 * 429 → в `text` готовая фраза для пользователя (возвращаем как успешный ответ).
 */
@Singleton
class ProxyAiClient @Inject constructor(
    private val okHttp: OkHttpClient,
    private val json: Json,
    private val userRepository: UserRepository
) : AiClient {


    override suspend fun chat(
        system: String,
        history: List<ChatMessage>,
        userMessage: String,
        locale: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val deviceId = userRepository.getDeviceId()
        val turns = HistorySanitizer.build(history, userMessage)
        val body = ProxyChatRequest(
            system = system,
            messages = turns.map { ProxyMessage(it.role, it.content) },
            locale = locale,
            deviceId = deviceId
        )
        val request = Request.Builder()
            .url("${BrandConfig.aiProxyUrl}/v1/chat")
            .header("X-App-Token", BrandConfig.aiProxyToken)
            .header("X-Device-Id", deviceId)
            .header("Accept", "application/json")
            .post(json.encodeToString(ProxyChatRequest.serializer(), body).toRequestBody(JSON_TYPE))
            .build()

        try {
            okHttp.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val parsed = runCatching { json.decodeFromString(ProxyChatResponse.serializer(), raw) }.getOrNull()
                when {
                    response.isSuccessful -> {
                        val text = parsed?.text?.trim().orEmpty()
                        if (text.isNotEmpty()) Result.success(text)
                        else Result.failure(AiException(AiErrors.emptyAnswer(locale)))
                    }
                    response.code == 429 -> {
                        val text = parsed?.text?.trim().orEmpty()
                        Result.success(text.ifEmpty { AiErrors.rateLimited(locale) })
                    }
                    response.code == 401 || response.code == 403 ->
                        Result.failure(AiException(AiErrors.unauthorized(locale)))
                    response.code in 400..499 ->
                        Result.failure(AiException(AiErrors.badRequest(locale)))
                    else -> Result.failure(AiException(AiErrors.serverUnavailable(locale)))
                }
            }
        } catch (e: IOException) {
            Result.failure(AiException(AiErrors.network(locale), e))
        } catch (e: IllegalStateException) {
            Result.failure(AiException(AiErrors.serverUnavailable(locale), e))
        }
    }

    private companion object {
        val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

/** Ошибка AI-слоя с готовым текстом для пользователя. */
class AiException(message: String, cause: Throwable? = null) : Exception(message, cause)
