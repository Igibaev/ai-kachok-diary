package com.fitcoach.app.di

import com.fitcoach.app.ai.AiBrand
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * DI AI-слоя. Клиенты ([com.fitcoach.app.ai.DemoAiClient], [com.fitcoach.app.ai.ProxyAiClient],
 * [com.fitcoach.app.ai.DirectAnthropicClient]) и [com.fitcoach.app.ai.AiClientSelector]
 * имеют @Inject-конструкторы; здесь — только брендовые данные для промпта и демо-ответов.
 */
@Module
@InstallIn(SingletonComponent::class)
object AiModule {

    @Provides
    @Singleton
    fun provideAiBrand(): AiBrand = AiBrand.fromConfig()
}
