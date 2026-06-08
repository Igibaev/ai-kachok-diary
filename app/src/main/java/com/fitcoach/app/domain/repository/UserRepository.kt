package com.fitcoach.app.domain.repository

import com.fitcoach.app.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun observeProfile(): Flow<UserProfile?>
    suspend fun getProfile(): UserProfile?
    suspend fun saveProfile(profile: UserProfile)
    suspend fun getApiKey(): String
    suspend fun saveApiKey(key: String)
}
