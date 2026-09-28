package com.fitcoach.app.demo

import com.fitcoach.app.data.local.db.AppDatabase
import com.fitcoach.app.data.local.db.dao.BodyMeasurementDao
import com.fitcoach.app.data.local.db.dao.NutritionDao
import com.fitcoach.app.data.local.db.dao.WaterDao
import com.fitcoach.app.data.local.db.entity.BodyMeasurementEntity
import com.fitcoach.app.data.local.db.entity.NutritionEntryEntity
import com.fitcoach.app.data.local.db.entity.WaterEntryEntity
import com.fitcoach.app.domain.model.ExerciseSet
import com.fitcoach.app.domain.model.MealType
import com.fitcoach.app.domain.model.Restriction
import com.fitcoach.app.domain.model.Sex
import com.fitcoach.app.domain.program.ExerciseTemplate
import com.fitcoach.app.domain.program.ProgramCatalog
import com.fitcoach.app.domain.program.WorkoutTemplate
import com.fitcoach.app.domain.model.UserProfile
import com.fitcoach.app.domain.model.Workout
import com.fitcoach.app.domain.repository.ChatRepository
import com.fitcoach.app.domain.repository.UserRepository
import com.fitcoach.app.domain.repository.WorkoutRepository
import com.fitcoach.app.domain.service.DemoDataSeeder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 3 недели «живой» истории для демонстрации владельцу клуба: тренировки с подходами и прогрессией веса,
 * групповые занятия, вода, питание за 3 дня, 6 замеров веса с трендом −1,5 кг. Чат не трогаем.
 */
@Singleton
class DemoDataSeederImpl @Inject constructor(
    private val db: AppDatabase,
    private val workoutRepo: WorkoutRepository,
    private val userRepo: UserRepository,
    private val chatRepo: ChatRepository,
    private val waterDao: WaterDao,
    private val nutritionDao: NutritionDao,
    private val measurementDao: BodyMeasurementDao
) : DemoDataSeeder {

    private val zone: ZoneId get() = ZoneId.systemDefault()
    private fun LocalDate.at(time: LocalTime): Long = atTime(time).atZone(zone).toInstant().toEpochMilli()
    private fun LocalDate.start(): Long = atStartOfDay(zone).toInstant().toEpochMilli()

    override suspend fun seedThreeWeeks() = withContext(Dispatchers.IO) {
        clearHistory()
        val profile = userRepo.getProfile() ?: UserProfile()
        val today = LocalDate.now()
        val rnd = Random(42)

        seedWorkouts(profile, today, rnd)
        seedWater(today, rnd)
        seedNutrition(today)
        seedMeasurements(profile, today)
    }

    override suspend fun clearAllUserData() = withContext(Dispatchers.IO) {
        clearHistory()
        chatRepo.clearAll()
    }

    /** Чистит всё, кроме профиля, шаблонов продуктов и чата. */
    private fun clearHistory() {
        db.runInTransaction {
            val sql = db.openHelper.writableDatabase
            sql.delete("exercise_sets", null, null)
            sql.delete("workouts", null, null)
            sql.delete("water_entries", null, null)
            sql.delete("nutrition_entries", "mealType != ?", arrayOf(MealType.TEMPLATE.name))
            sql.delete("body_measurements", null, null)
        }
    }

    // ---------------- тренировки ----------------

    /** Веса для демо-истории по группе мышц (кг на 1-й неделе, шаг в неделю). */
    private fun baseWeightFor(ex: ExerciseTemplate, profile: UserProfile): Pair<Float, Float> {
        val female = profile.sex == Sex.FEMALE
        val hint = ex.sets.firstOrNull()?.weight.orEmpty()
        if (hint.contains("б/в") || hint.contains("свой вес") || hint.contains("собств")) return 0f to 0f
        val group = (ex.muscleGroup + " " + ex.name).lowercase()
        val base = when {
            "жим ногами" in group || "присед" in group -> if (female) 40f else 70f
            "ног" in group || "ягод" in group || "бедр" in group || "румынск" in group || "выпад" in group -> if (female) 20f else 32f
            "тяга верхнего" in group || "тяга горизонт" in group || "спин" in group || "тяга" in group -> if (female) 25f else 40f
            "груд" in group || "жим" in group -> if (female) 10f else 18f
            "плеч" in group || "разведен" in group -> if (female) 5f else 10f
            "пресс" in group || "кор" in group || "скруч" in group -> if (female) 10f else 15f
            "рук" in group || "бицепс" in group || "трицепс" in group || "сгибан" in group || "разгибан" in group -> if (female) 6f else 10f
            else -> if (female) 10f else 16f
        }
        val step = if (base >= 40f) 5f else if (base >= 15f) 2.5f else 1f
        return base to step
    }

    private suspend fun seedWorkouts(profile: UserProfile, today: LocalDate, rnd: Random) {
        val program = ProgramCatalog.getOrDefault(profile.programKey)
        // Как и экраны (Dashboard/Progress/Chat): недели считаются по profile.daysPerWeek, а не программы.
        val daysPerWeek = profile.daysPerWeek.coerceAtLeast(1)
        val fourDays = daysPerWeek >= 4
        // Смещения назад в днях; последняя тренировка — позавчера, чтобы главная показывала «следующую».
        val offsets = if (fourDays) listOf(20, 19, 17, 15, 13, 12, 10, 8, 6, 2)
        else listOf(20, 18, 16, 13, 11, 9, 6, 4, 2)

        offsets.forEachIndexed { index, daysAgo ->
            val next = ProgramCatalog.nextWorkout(program, index, daysPerWeek)
            val template = ProgramCatalog.applyRestrictions(next.template, profile.restrictions)
            val week = next.weekNumber
            val date = today.minusDays(daysAgo.toLong())
            val workoutId = UUID.randomUUID().toString()
            val rpe = listOf(6, 7, 7, 8, 7, 8, 8, 9, 7, 8)[index % 10]
            val workout = Workout(
                id = workoutId,
                date = date.at(LocalTime.of(19, 0)),
                programKey = program.key,
                planKey = template.key,
                phaseName = template.phaseName,
                weekNumber = week,
                isCompleted = true,
                durationMinutes = 44 + rnd.nextInt(0, 14),
                painLevel = if (index == 1 && Restriction.BACK in profile.restrictions) 2 else 0,
                rpe = rpe,
                notes = if (index == offsets.lastIndex) "Отлично зашла тренировка, +2 кг в первом упражнении" else ""
            )
            workoutRepo.saveWorkout(workout)
            workoutRepo.insertSets(buildSets(workoutId, template, week, profile, rnd))
        }

        // Групповые занятия клуба
        listOf(14 to "Zumba", 7 to "Stretching").forEach { (daysAgo, title) ->
            workoutRepo.saveWorkout(
                Workout(
                    id = UUID.randomUUID().toString(),
                    date = today.minusDays(daysAgo.toLong()).at(LocalTime.of(18, 30)),
                    programKey = program.key,
                    planKey = "ACTIVITY_GROUP",
                    phaseName = "Групповое занятие · $title",
                    weekNumber = (21 - daysAgo) / 7 + 1,
                    isCompleted = true,
                    durationMinutes = 55,
                    painLevel = 0,
                    rpe = 6,
                    notes = title
                )
            )
        }
    }

    private fun buildSets(workoutId: String, template: WorkoutTemplate, week: Int, profile: UserProfile, rnd: Random): List<ExerciseSet> =
        template.exercises.flatMap { ex ->
            val (baseKg, stepKg) = baseWeightFor(ex, profile)
            val kg = baseKg + stepKg * (week - 1)
            ex.sets.mapIndexed { i, st ->
                val n = i + 1
                val timed = st.reps <= 1
                val actualReps = if (timed) 1 else (st.reps - (n - 1) + rnd.nextInt(-1, 2)).coerceAtLeast(6)
                ExerciseSet(
                    id = UUID.randomUUID().toString(),
                    workoutId = workoutId,
                    exerciseId = ex.id,
                    exerciseName = ex.name,
                    setNumber = n,
                    targetReps = st.reps,
                    actualReps = actualReps,
                    targetWeight = st.weight,
                    actualWeight = if (kg <= 0f) null else kg,
                    isDone = true,
                    restSeconds = ex.restSeconds
                )
            }
        }

    // ---------------- вода ----------------

    private suspend fun seedWater(today: LocalDate, rnd: Random) {
        for (daysAgo in 20 downTo 0) {
            val date = today.minusDays(daysAgo.toLong())
            val target = if (daysAgo == 0) 900 else rnd.nextInt(1800, 2650)
            val portions = mutableListOf<Int>()
            var sum = 0
            while (sum < target) {
                val p = listOf(250, 300, 350, 500).random(rnd)
                portions += p; sum += p
            }
            portions.forEachIndexed { i, ml ->
                val hour = 8 + (i * (12f / portions.size)).roundToInt().coerceAtMost(13)
                waterDao.insertEntry(
                    WaterEntryEntity(
                        id = UUID.randomUUID().toString(),
                        date = date.start(),
                        amountMl = ml,
                        createdAt = date.at(LocalTime.of(hour, (i * 17) % 60))
                    )
                )
            }
        }
    }

    // ---------------- питание ----------------

    private data class Meal(val type: MealType, val hour: Int, val name: String, val grams: Float, val kcal: Int, val p: Float, val c: Float, val f: Float)

    private val menus: List<List<Meal>> = listOf(
        listOf(
            Meal(MealType.BREAKFAST, 8, "Овсянка на молоке с бананом", 300f, 320, 11f, 55f, 6f),
            Meal(MealType.LUNCH, 13, "Сорпа с говядиной", 350f, 280, 22f, 12f, 14f),
            Meal(MealType.LUNCH, 13, "Нан (лепёшка тандырная)", 60f, 165, 5f, 33f, 1f),
            Meal(MealType.SNACK, 16, "Айран", 250f, 100, 7f, 10f, 3f),
            Meal(MealType.DINNER, 19, "Куриная грудка с гречкой", 350f, 430, 45f, 40f, 6f)
        ),
        listOf(
            Meal(MealType.BREAKFAST, 8, "Творог 5% с ягодами", 200f, 260, 34f, 12f, 10f),
            Meal(MealType.LUNCH, 13, "Бешбармак (ет)", 300f, 540, 34f, 45f, 24f),
            Meal(MealType.SNACK, 16, "Курт", 40f, 105, 10f, 3f, 6f),
            Meal(MealType.DINNER, 19, "Хек запечённый с овощами", 350f, 300, 36f, 12f, 8f)
        ),
        listOf(
            Meal(MealType.BREAKFAST, 8, "Яичница из 2 яиц + хлеб", 160f, 300, 16f, 20f, 16f),
            Meal(MealType.LUNCH, 13, "Лагман", 400f, 520, 24f, 60f, 18f),
            Meal(MealType.SNACK, 16, "Греческий йогурт 2%", 170f, 100, 17f, 6f, 1f),
            Meal(MealType.DINNER, 19, "Куырдак с салатом", 300f, 480, 30f, 20f, 30f)
        )
    )

    private suspend fun seedNutrition(today: LocalDate) {
        for (daysAgo in 2 downTo 0) {
            val date = today.minusDays(daysAgo.toLong())
            val menu = menus[daysAgo % menus.size]
            val nowHour = LocalTime.now().hour
            menu.filter { daysAgo > 0 || it.hour <= nowHour }.forEach { m ->
                val ts = date.at(LocalTime.of(m.hour, 15))
                nutritionDao.insertEntry(
                    NutritionEntryEntity(
                        id = UUID.randomUUID().toString(),
                        date = ts, mealType = m.type.name, name = m.name,
                        calories = m.kcal, proteinG = m.p, carbsG = m.c, fatG = m.f, grams = m.grams, createdAt = ts
                    )
                )
            }
        }
    }

    // ---------------- замеры ----------------

    private suspend fun seedMeasurements(profile: UserProfile, today: LocalDate) {
        val current = profile.weightKg.takeIf { it >= 30f } ?: 70f // профиль без веса → правдоподобный демо-вес
        val start = current + 1.5f
        val notes = listOf("Стартовый замер", "", "Меньше сладкого", "", "Стало легче подниматься по лестнице", "Отлично!")
        val waistStart = if (profile.sex.name == "FEMALE") 78f else 92f
        for (i in 0 until 6) {
            val daysAgo = 20 - i * 4
            val t = i / 5f
            val weight = ((start + (current - start) * t) * 10).roundToInt() / 10f
            measurementDao.insertMeasurement(
                BodyMeasurementEntity(
                    id = UUID.randomUUID().toString(),
                    date = today.minusDays(daysAgo.toLong()).at(LocalTime.of(7, 30)),
                    weightKg = weight,
                    waistCm = ((waistStart - 2.5f * t) * 10).roundToInt() / 10f,
                    notes = notes[i]
                )
            )
        }
    }
}
