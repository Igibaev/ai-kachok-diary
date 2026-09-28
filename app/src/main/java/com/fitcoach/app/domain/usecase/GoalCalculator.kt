package com.fitcoach.app.domain.usecase

import com.fitcoach.app.domain.model.Goal
import com.fitcoach.app.domain.model.Sex
import com.fitcoach.app.domain.model.UserProfile
import kotlin.math.roundToInt

data class Goals(
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val waterMl: Int
)

/**
 * Расчёт КБЖУ и воды. Миффлин — Сан Жеор; активность 1.375 (≤3 дня) / 1.55 (4+);
 * похудение −15 %, масса +10 %; белок 1.6–2.0 г/кг; жиры 25–30 % ккал; углеводы — остаток;
 * вода 33 мл/кг (1500–4000). Минимум ккал: 1200 (Ж) / 1500 (М).
 */
object GoalCalculator {

    fun bmr(profile: UserProfile): Double {
        val base = 10.0 * profile.weightKg + 6.25 * profile.heightCm - 5.0 * profile.age
        return if (profile.sex == Sex.MALE) base + 5 else base - 161
    }

    fun activityFactor(daysPerWeek: Int): Double = if (daysPerWeek <= 3) 1.375 else 1.55

    fun goalAdjustment(goal: Goal): Double = when (goal) {
        Goal.FAT_LOSS -> 0.85
        Goal.MUSCLE_GAIN -> 1.10
        else -> 1.0
    }

    fun proteinPerKg(goal: Goal): Double = when (goal) {
        Goal.FAT_LOSS, Goal.MUSCLE_GAIN -> 2.0
        Goal.TONE -> 1.8
        else -> 1.6
    }

    fun fatShare(goal: Goal): Double = when (goal) {
        Goal.MUSCLE_GAIN -> 0.25
        else -> 0.30
    }

    fun calculate(profile: UserProfile): Goals {
        val minKcal = if (profile.sex == Sex.FEMALE) 1200 else 1500
        val tdee = bmr(profile) * activityFactor(profile.daysPerWeek) * goalAdjustment(profile.goal)
        val calories = tdee.roundToInt().coerceAtLeast(minKcal)

        // Белок — от целевого веса, если он ниже фактического: при 120 кг 2 г/кг дали бы 240 г (почти половина рациона).
        val proteinBaseKg = profile.targetWeightKg?.takeIf { it > 0f && it < profile.weightKg } ?: profile.weightKg
        val protein = (proteinPerKg(profile.goal) * proteinBaseKg).roundToInt()
            .coerceIn(50, (calories * 0.35 / 4.0).roundToInt().coerceAtLeast(50))
        val fat = (calories * fatShare(profile.goal) / 9.0).roundToInt().coerceAtLeast(30)
        val carbs = ((calories - protein * 4 - fat * 9) / 4.0).roundToInt().coerceAtLeast(50)

        val water = (33.0 * profile.weightKg).roundToInt()
            .coerceIn(1500, 4000)
            .let { (it / 50) * 50 }

        return Goals(calories, protein, carbs, fat, water)
    }

    fun applyTo(profile: UserProfile, goals: Goals = calculate(profile)): UserProfile = profile.copy(
        calorieGoal = goals.calories,
        proteinGoal = goals.proteinG,
        carbsGoal = goals.carbsG,
        fatGoal = goals.fatG,
        waterGoalMl = goals.waterMl
    )
}
