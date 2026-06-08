package com.fitcoach.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String = "",
    val age: Int = 29,
    val heightCm: Int = 185,
    val weightKg: Float = 103f,
    val targetWeightKg: Float? = null,
    val activityLevel: String = "MODERATE",
    val backCondition: String = "L5_S1_HERNIA",
    val programStartDate: Long = System.currentTimeMillis(),
    val anthropicApiKey: String = "",
    val waterGoalMl: Int = 2500,
    val calorieGoal: Int = 2100,
    val proteinGoal: Int = 190,
    val carbsGoal: Int = 195,
    val fatGoal: Int = 60
)
