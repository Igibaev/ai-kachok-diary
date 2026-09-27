package com.fitcoach.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.fitcoach.app.data.local.db.dao.UserProfileDao
import com.fitcoach.app.data.local.db.entity.UserProfileEntity
import com.fitcoach.app.domain.model.*
import com.fitcoach.app.domain.repository.UserRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    private val dao: UserProfileDao,
    @ApplicationContext private val context: Context
) : UserRepository {

    private val securePrefs: SharedPreferences by lazy {
        runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context, "secure_prefs", masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrElse {
            // Крайне редкий случай повреждённого keystore — не падаем, работаем без шифрования.
            context.getSharedPreferences("secure_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    private val plainPrefs: SharedPreferences by lazy {
        context.getSharedPreferences("fitcoach_prefs", Context.MODE_PRIVATE)
    }

    override fun observeProfile(): Flow<UserProfile?> =
        dao.observeProfile().map { it?.toDomain() }

    override suspend fun getProfile(): UserProfile? = dao.getProfile()?.toDomain()

    override suspend fun saveProfile(profile: UserProfile) {
        val withMember = if (profile.memberId.isBlank()) profile.copy(memberId = generateMemberId()) else profile
        dao.insertProfile(withMember.toEntity())
    }

    override suspend fun getApiKey(): String =
        securePrefs.getString(KEY_API, "") ?: ""

    override suspend fun saveApiKey(key: String) =
        securePrefs.edit().putString(KEY_API, key.trim()).apply()

    override suspend fun getDeviceId(): String {
        plainPrefs.getString(KEY_DEVICE_ID, null)?.let { return it }
        val id = UUID.randomUUID().toString()
        plainPrefs.edit().putString(KEY_DEVICE_ID, id).apply()
        return id
    }

    override suspend fun getFlag(key: String, default: Boolean): Boolean =
        plainPrefs.getBoolean("flag_$key", default)

    override suspend fun setFlag(key: String, value: Boolean) =
        plainPrefs.edit().putBoolean("flag_$key", value).apply()

    private fun generateMemberId(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..8).map { alphabet.random() }.joinToString("")
    }

    private companion object {
        const val KEY_API = "anthropic_api_key"
        const val KEY_DEVICE_ID = "device_id"
    }
}

private inline fun <reified T : Enum<T>> String.toEnumOr(default: T): T =
    enumValues<T>().firstOrNull { it.name == this } ?: default

fun UserProfileEntity.toDomain() = UserProfile(
    id = id,
    name = name,
    sex = sex.toEnumOr(Sex.MALE),
    age = age,
    heightCm = heightCm,
    weightKg = weightKg,
    targetWeightKg = targetWeightKg,
    goal = goal.toEnumOr(Goal.GENERAL_FITNESS),
    level = level.toEnumOr(Level.BEGINNER),
    daysPerWeek = daysPerWeek,
    restrictions = restrictions.split(',').filter { it.isNotBlank() }
        .mapNotNull { r -> Restriction.entries.firstOrNull { it.name == r.trim() } }.toSet(),
    programKey = programKey,
    programStartDate = programStartDate,
    waterGoalMl = waterGoalMl,
    calorieGoal = calorieGoal,
    proteinGoal = proteinGoal,
    carbsGoal = carbsGoal,
    fatGoal = fatGoal,
    language = language,
    onboardingCompleted = onboardingCompleted,
    memberId = memberId
)

fun UserProfile.toEntity() = UserProfileEntity(
    id = id,
    name = name,
    sex = sex.name,
    age = age,
    heightCm = heightCm,
    weightKg = weightKg,
    targetWeightKg = targetWeightKg,
    goal = goal.name,
    level = level.name,
    daysPerWeek = daysPerWeek,
    restrictions = restrictions.joinToString(",") { it.name },
    programKey = programKey,
    programStartDate = programStartDate,
    waterGoalMl = waterGoalMl,
    calorieGoal = calorieGoal,
    proteinGoal = proteinGoal,
    carbsGoal = carbsGoal,
    fatGoal = fatGoal,
    language = language,
    onboardingCompleted = onboardingCompleted,
    memberId = memberId
)
