package com.fitcoach.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String = "",
    val sex: String = "MALE",
    val age: Int = 30,
    val heightCm: Int = 175,
    val weightKg: Float = 75f,
    val targetWeightKg: Float? = null,
    val goal: String = "GENERAL_FITNESS",
    val level: String = "BEGINNER",
    val daysPerWeek: Int = 3,
    /** Ограничения через запятую: "BACK,KNEES". */
    val restrictions: String = "",
    val programKey: String = "BEGINNER_3",
    val programStartDate: Long = System.currentTimeMillis(),
    val waterGoalMl: Int = 2500,
    val calorieGoal: Int = 2000,
    val proteinGoal: Int = 130,
    val carbsGoal: Int = 220,
    val fatGoal: Int = 65,
    val language: String = "ru",
    val onboardingCompleted: Boolean = false,
    val memberId: String = ""
)
