package com.fitcoach.app.ai.chef

import java.util.Base64
import com.fitcoach.app.ai.AiErrors
import com.fitcoach.app.ai.AiException
import com.fitcoach.app.brand.BrandConfig
import com.fitcoach.app.domain.model.FoodAnalysis
import com.fitcoach.app.domain.model.MealPlan
import com.fitcoach.app.domain.model.MealPlanRequest
import com.fitcoach.app.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI-повар через прокси клуба: POST {aiProxyUrl}/v1/meal-plan и /v1/food-photo.
 * Заголовки и формат ошибок — как у [com.fitcoach.app.ai.ProxyAiClient] (`{error, text}`).
 * План генерируется долго — отдельный таймаут чтения 200 с; фото — 90 с.
 */
@Singleton
class ProxyAiChefClient @Inject constructor(
    okHttp: OkHttpClient,
    private val json: Json,
    private val userRepository: UserRepository
) : AiChefClient {

    private val planHttp = okHttp.newBuilder().readTimeout(200, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).build()
    private val photoHttp = okHttp.newBuilder().readTimeout(90, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).build()

    override suspend fun generateMealPlan(req: MealPlanRequest, locale: String): Result<MealPlan> = withContext(Dispatchers.IO) {
        val deviceId = userRepository.getDeviceId()
        val body = json.encodeToString(MealPlanRequestDto.serializer(), req.toDto(locale, deviceId))
        call(planHttp, "/v1/meal-plan", body, deviceId, locale, limitText = ChefErrors.planLimit(locale)) { raw ->
            val parsed = json.decodeFromString(MealPlanResponseDto.serializer(), raw)
            val plan = parsed.plan?.toDomain()
            if (plan == null || plan.days.isEmpty()) Result.failure(AiException(ChefErrors.emptyPlan(locale)))
            else Result.success(plan)
        }
    }

    override suspend fun analyzeFoodPhoto(jpeg: ByteArray, mediaType: String, hint: String?, locale: String): Result<FoodAnalysis> =
        withContext(Dispatchers.IO) {
            if (jpeg.size > MAX_IMAGE_BYTES) return@withContext Result.failure(AiException(ChefErrors.imageTooLarge(locale)))
            val deviceId = userRepository.getDeviceId()
            val dto = FoodPhotoRequestDto(
                locale = locale,
                imageBase64 = Base64.getEncoder().encodeToString(jpeg),
                mediaType = mediaType,
                hint = hint?.trim()?.takeIf { it.isNotEmpty() },
                deviceId = deviceId
            )
            val body = json.encodeToString(FoodPhotoRequestDto.serializer(), dto)
            call(photoHttp, "/v1/food-photo", body, deviceId, locale, limitText = ChefErrors.photoLimit(locale)) { raw ->
                val parsed = json.decodeFromString(FoodPhotoResponseDto.serializer(), raw)
                val analysis = parsed.analysis?.toDomain()
                if (analysis == null) Result.failure(AiException(AiErrors.emptyAnswer(locale))) else Result.success(analysis)
            }
        }

    private inline fun <T> call(
        http: OkHttpClient,
        path: String,
        body: String,
        deviceId: String,
        locale: String,
        limitText: String,
        parse: (String) -> Result<T>
    ): Result<T> {
        val request = Request.Builder()
            .url("${BrandConfig.aiProxyUrl}$path")
            .header("X-App-Token", BrandConfig.aiProxyToken)
            .header("X-Device-Id", deviceId)
            .header("Accept", "application/json")
            .post(body.toRequestBody(JSON_TYPE))
            .build()
        return try {
            http.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                when {
                    response.isSuccessful -> runCatching { parse(raw) }
                        .getOrElse { Result.failure(AiException(AiErrors.emptyAnswer(locale), it)) }
                    response.code == 429 -> Result.failure(AiException(serverText(raw) ?: limitText))
                    response.code == 422 -> Result.failure(AiException(serverText(raw) ?: ChefErrors.refused(locale)))
                    response.code == 413 -> Result.failure(AiException(ChefErrors.imageTooLarge(locale)))
                    response.code == 401 || response.code == 403 -> Result.failure(AiException(AiErrors.unauthorized(locale)))
                    response.code in 400..499 -> Result.failure(AiException(serverText(raw) ?: AiErrors.badRequest(locale)))
                    else -> Result.failure(AiException(AiErrors.serverUnavailable(locale)))
                }
            }
        } catch (e: IOException) {
            Result.failure(AiException(AiErrors.network(locale), e))
        } catch (e: IllegalStateException) {
            Result.failure(AiException(AiErrors.serverUnavailable(locale), e))
        }
    }

    /** Текст для пользователя из `{error, text}` прокси (если есть). */
    private fun serverText(raw: String): String? =
        runCatching { json.decodeFromString(MealPlanResponseDto.serializer(), raw).text?.trim() }.getOrNull()?.takeIf { it.isNotEmpty() }

    private companion object {
        val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
        const val MAX_IMAGE_BYTES = 1_500_000
    }
}
