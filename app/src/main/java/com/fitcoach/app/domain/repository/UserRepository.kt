package com.fitcoach.app.domain.repository

import com.fitcoach.app.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun observeProfile(): Flow<UserProfile?>
    suspend fun getProfile(): UserProfile?
    suspend fun saveProfile(profile: UserProfile)

    /** Ключ разработчика для прямого режима AI (хранится в EncryptedSharedPreferences). */
    suspend fun getApiKey(): String
    suspend fun saveApiKey(key: String)

    /** Стабильный анонимный идентификатор устройства (UUID), для лимитов AI-прокси. */
    suspend fun getDeviceId(): String

    /** Произвольные флаги/настройки (демо-режим AI, показанные подсказки и т.п.). */
    suspend fun getFlag(key: String, default: Boolean = false): Boolean
    suspend fun setFlag(key: String, value: Boolean)
}
