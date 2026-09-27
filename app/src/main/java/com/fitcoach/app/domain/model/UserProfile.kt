package com.fitcoach.app.domain.model

enum class Sex { MALE, FEMALE }

enum class Goal(val title: String, val emoji: String) {
    FAT_LOSS("Похудение", "🔥"),
    MUSCLE_GAIN("Набор массы", "💪"),
    TONE("Тонус и рельеф", "✨"),
    BACK_HEALTH("Здоровая спина", "🧘"),
    GENERAL_FITNESS("Общая форма", "⚡")
}

enum class Level(val title: String) {
    BEGINNER("Новичок"),
    INTERMEDIATE("Средний"),
    ADVANCED("Опытный")
}

enum class Restriction(val title: String) {
    BACK("Спина / поясница"),
    KNEES("Колени"),
    SHOULDERS("Плечи"),
    HYPERTENSION("Давление / сердце"),
    PREGNANCY_POSTPARTUM("Беременность / послеродовой период")
}

data class UserProfile(
    val id: Int = 1,
    val name: String = "",
    val sex: Sex = Sex.MALE,
    val age: Int = 30,
    val heightCm: Int = 175,
    val weightKg: Float = 75f,
    val targetWeightKg: Float? = null,
    val goal: Goal = Goal.GENERAL_FITNESS,
    val level: Level = Level.BEGINNER,
    val daysPerWeek: Int = 3,
    val restrictions: Set<Restriction> = emptySet(),
    val programKey: String = DEFAULT_PROGRAM_KEY,
    val programStartDate: Long = System.currentTimeMillis(),
    val waterGoalMl: Int = 2500,
    val calorieGoal: Int = 2000,
    val proteinGoal: Int = 130,
    val carbsGoal: Int = 220,
    val fatGoal: Int = 65,
    val language: String = "ru",
    val onboardingCompleted: Boolean = false,
    /** Короткий код участника для QR-пропуска; генерируется один раз. */
    val memberId: String = ""
) {
    val bmi: Float get() = if (heightCm > 0) weightKg / ((heightCm / 100f) * (heightCm / 100f)) else 0f

    companion object {
        const val DEFAULT_PROGRAM_KEY = "BEGINNER_3"
    }
}
