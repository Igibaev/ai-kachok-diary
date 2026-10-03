package com.fitcoach.app.ai.chef

import com.fitcoach.app.ai.AiClientSelector
import com.fitcoach.app.ai.AiMode
import com.fitcoach.app.brand.BrandConfig
import com.fitcoach.app.domain.repository.UserRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Выбор реализации [AiChefClient]: флаг force-demo → [DemoAiChefClient]; задан прокси → [ProxyAiChefClient];
 * иначе демо. Режима «ключ разработчика» у повара нет: vision и structured output идут только через прокси клуба.
 */
@Singleton
class AiChefSelector @Inject constructor(
    private val userRepository: UserRepository,
    private val demo: DemoAiChefClient,
    private val proxy: ProxyAiChefClient
) {
    suspend fun current(): AiChefClient = when (currentMode()) {
        AiMode.CLUB -> proxy
        else -> demo
    }

    suspend fun currentMode(): AiMode = when {
        userRepository.getFlag(AiClientSelector.FLAG_FORCE_DEMO) -> AiMode.DEMO
        BrandConfig.hasAiProxy -> AiMode.CLUB
        else -> AiMode.DEMO
    }
}
