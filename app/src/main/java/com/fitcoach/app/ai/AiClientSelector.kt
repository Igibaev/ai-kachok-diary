package com.fitcoach.app.ai

import com.fitcoach.app.BuildConfig
import com.fitcoach.app.brand.BrandConfig
import com.fitcoach.app.domain.repository.UserRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Выбирает реализацию [AiClient] по приоритету:
 *  1. флаг `ai_force_demo` → [DemoAiClient];
 *  2. задан `BrandConfig.aiProxyUrl` → [ProxyAiClient];
 *  3. DEBUG-сборка и сохранён ключ разработчика → [DirectAnthropicClient];
 *  4. иначе → [DemoAiClient].
 */
@Singleton
class AiClientSelector @Inject constructor(
    private val userRepository: UserRepository,
    private val demo: DemoAiClient,
    private val proxy: ProxyAiClient,
    private val direct: DirectAnthropicClient
) {
    suspend fun current(): AiClient = when (currentMode()) {
        AiMode.DEMO -> demo
        AiMode.CLUB -> proxy
        AiMode.DEVELOPER -> direct
    }

    suspend fun currentMode(): AiMode = when {
        userRepository.getFlag(FLAG_FORCE_DEMO) -> AiMode.DEMO
        BrandConfig.hasAiProxy -> AiMode.CLUB
        BuildConfig.DEBUG && userRepository.getApiKey().isNotBlank() -> AiMode.DEVELOPER
        else -> AiMode.DEMO
    }

    suspend fun isForceDemo(): Boolean = userRepository.getFlag(FLAG_FORCE_DEMO)

    suspend fun setForceDemo(value: Boolean) = userRepository.setFlag(FLAG_FORCE_DEMO, value)

    /** Есть ли вообще «настоящий» AI помимо демо (для подсказок в настройках). */
    fun hasRealBackend(): Boolean = BrandConfig.hasAiProxy || BuildConfig.DEBUG

    companion object {
        const val FLAG_FORCE_DEMO = "ai_force_demo"
    }
}
