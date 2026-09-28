package com.fitcoach.app.ai

import com.fitcoach.app.domain.model.Goal
import com.fitcoach.app.domain.model.Level
import com.fitcoach.app.domain.model.NutritionSummary
import com.fitcoach.app.domain.model.Restriction
import com.fitcoach.app.domain.model.Sex
import com.fitcoach.app.domain.model.UserProfile
import com.fitcoach.app.domain.model.Workout
import com.fitcoach.app.domain.usecase.ai.BuildSystemPromptUseCase
import com.fitcoach.app.domain.usecase.ai.PromptContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildSystemPromptUseCaseTest {

    private val brand = AiBrand(
        coachName = "Арман",
        clubName = "Fit Almaty",
        clubCity = "Алматы",
        appName = "Fit Almaty App",
        hasWhatsapp = true,
        model = "claude-opus-5"
    )
    private val useCase = BuildSystemPromptUseCase(brand)

    private val profile = UserProfile(
        name = "Айгерим Секретная",
        sex = Sex.FEMALE,
        age = 29,
        heightCm = 165,
        weightKg = 68f,
        targetWeightKg = 62f,
        goal = Goal.FAT_LOSS,
        level = Level.BEGINNER,
        daysPerWeek = 3,
        restrictions = setOf(Restriction.BACK, Restriction.KNEES),
        language = "ru"
    )

    private fun ctx(locale: String = "ru", restrictions: Set<Restriction> = profile.restrictions) = PromptContext(
        profile = profile.copy(restrictions = restrictions),
        locale = locale,
        programTitle = "Стройность и тонус",
        currentWeek = 3,
        phaseName = "Фаза 1: Адаптация",
        todayWorkout = null,
        nextWorkoutTitle = "Всё тело A",
        recentWorkouts = listOf(
            Workout(
                id = "w1", date = 1_700_000_000_000L, programKey = "SLIM_3", planKey = "A1",
                phaseName = "Всё тело A", weekNumber = 2, isCompleted = true,
                durationMinutes = 48, painLevel = 2, rpe = 7, notes = ""
            )
        ),
        todayNutrition = NutritionSummary(calories = 1200, proteinG = 80f, carbsG = 120f, fatG = 40f),
        waterToday = 1500
    )

    @Test
    fun `prompt contains persona with club and city`() {
        val prompt = useCase(ctx())
        assertTrue(prompt.contains("Арман"))
        assertTrue(prompt.contains("Fit Almaty"))
        assertTrue(prompt.contains("Алматы"))
    }

    @Test
    fun `prompt never contains user name`() {
        val prompt = useCase(ctx())
        assertFalse(prompt.contains("Айгерим"))
        assertFalse(prompt.contains("Секретная"))
    }

    @Test
    fun `prompt contains restrictions with concrete bans`() {
        val prompt = useCase(ctx())
        assertTrue(prompt.contains("Спина/поясница"))
        assertTrue(prompt.contains("Колени"))
        assertTrue(prompt.contains("ЗАПРЕЩЕНО"))
        assertTrue(prompt.contains("становая тяга"))
        assertFalse(prompt.contains("Плечи"))
    }

    @Test
    fun `prompt without restrictions still reminds about technique`() {
        val prompt = useCase(ctx(restrictions = emptySet()))
        assertTrue(prompt.contains("Ограничений не указано"))
    }

    @Test
    fun `language line for ru and kk`() {
        val ru = useCase(ctx(locale = "ru"))
        assertTrue(ru.contains("Язык интерфейса пользователя: русский (ru)"))
        val kk = useCase(ctx(locale = "kk"))
        assertTrue(kk.contains("Язык интерфейса пользователя: казахский (kk)"))
        assertTrue(kk.contains("отвечай на казахском"))
    }

    @Test
    fun `prompt contains program progress and today context`() {
        val prompt = useCase(ctx())
        assertTrue(prompt.contains("Стройность и тонус"))
        assertTrue(prompt.contains("неделя 3 из 12"))
        assertTrue(prompt.contains("Всё тело A"))
        assertTrue(prompt.contains("1500 мл"))
        assertTrue(prompt.contains("1200 ккал"))
        assertTrue(prompt.contains("тяжесть 7/10"))
    }

    @Test
    fun `prompt contains safety and club rules`() {
        val prompt = useCase(ctx())
        assertTrue(prompt.contains("не ставь диагнозов"))
        assertTrue(prompt.contains("Красные флаги"))
        assertTrue(prompt.contains("Спросить тренера"))
        assertTrue(prompt.contains("не выдумывай цены"))
    }
}
