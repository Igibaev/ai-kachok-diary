package com.fitcoach.app.di

import com.fitcoach.app.data.repository.*
import com.fitcoach.app.domain.repository.*
import com.fitcoach.app.domain.service.DemoDataSeeder
import com.fitcoach.app.domain.service.NoopDemoDataSeeder
import com.fitcoach.app.domain.service.NoopShareService
import com.fitcoach.app.domain.service.ShareService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindWorkoutRepository(impl: WorkoutRepositoryImpl): WorkoutRepository

    @Binds @Singleton
    abstract fun bindNutritionRepository(impl: NutritionRepositoryImpl): NutritionRepository

    @Binds @Singleton
    abstract fun bindWaterRepository(impl: WaterRepositoryImpl): WaterRepository

    @Binds @Singleton
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository

    @Binds @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository
}

/** Сервисы с заглушками; реальные реализации подменяются агентами (см. CONTRACTS). */
@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {
    @Provides @Singleton
    fun provideShareService(): ShareService = NoopShareService()

    @Provides @Singleton
    fun provideDemoDataSeeder(): DemoDataSeeder = NoopDemoDataSeeder()
}
