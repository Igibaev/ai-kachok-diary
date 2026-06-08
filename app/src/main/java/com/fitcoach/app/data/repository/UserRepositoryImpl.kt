package com.fitcoach.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.fitcoach.app.data.local.db.dao.UserProfileDao
import com.fitcoach.app.data.local.db.entity.UserProfileEntity
import com.fitcoach.app.domain.model.UserProfile
import com.fitcoach.app.domain.repository.UserRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    private val dao: UserProfileDao,
    @ApplicationContext private val context: Context
) : UserRepository {

    private val securePrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context, "secure_prefs", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun observeProfile(): Flow<UserProfile?> =
        dao.observeProfile().map { it?.toDomain() }

    override suspend fun getProfile(): UserProfile? = dao.getProfile()?.toDomain()

    override suspend fun saveProfile(profile: UserProfile) =
        dao.insertProfile(profile.toEntity())

    override suspend fun getApiKey(): String =
        securePrefs.getString("anthropic_api_key", "") ?: ""

    override suspend fun saveApiKey(key: String) =
        securePrefs.edit().putString("anthropic_api_key", key).apply()
}

private fun UserProfileEntity.toDomain() = UserProfile(
    id, name, age, heightCm, weightKg, targetWeightKg, activityLevel,
    backCondition, programStartDate, anthropicApiKey, waterGoalMl, calorieGoal, proteinGoal, carbsGoal, fatGoal
)

private fun UserProfile.toEntity() = UserProfileEntity(
    id, name, age, heightCm, weightKg, targetWeightKg, activityLevel,
    backCondition, programStartDate, anthropicApiKey, waterGoalMl, calorieGoal, proteinGoal, carbsGoal, fatGoal
)
