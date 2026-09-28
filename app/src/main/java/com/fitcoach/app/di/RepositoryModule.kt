package com.fitcoach.app.di

import com.fitcoach.app.data.repository.*
import com.fitcoach.app.demo.DemoDataSeederImpl
import com.fitcoach.app.share.ShareServiceImpl
import com.fitcoach.app.domain.repository.*
import com.fitcoach.app.domain.service.DemoDataSeeder
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

/** Реальные сервисы «Клуб»: карточки для Stories и демо-данные для презентаций. */
@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {
    @Provides @Singleton
    fun provideShareService(impl: ShareServiceImpl): ShareService = impl

    @Provides @Singleton
    fun provideDemoDataSeeder(impl: DemoDataSeederImpl): DemoDataSeeder = impl
}
