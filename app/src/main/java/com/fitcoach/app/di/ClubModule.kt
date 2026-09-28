package com.fitcoach.app.di

import com.fitcoach.app.data.club.ClubRepository
import com.fitcoach.app.data.club.ClubRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ClubModule {
    @Binds @Singleton
    abstract fun bindClubRepository(impl: ClubRepositoryImpl): ClubRepository
}
