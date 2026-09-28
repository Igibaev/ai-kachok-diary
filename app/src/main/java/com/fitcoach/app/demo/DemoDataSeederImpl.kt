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

    private data class DemoExercise(val id: String, val name: String, val baseKg: Float, val reps: Int, val stepKg: Float)
    private data class DemoTemplate(val planKey: String, val title: String, val exercises: List<DemoExercise>)

    private val upper = DemoTemplate("DEMO_UPPER", "Верх тела", listOf(
        DemoExercise("demo_bench_db", "Жим гантелей лёжа", 16f, 10, 2f),
        DemoExercise("demo_lat_pull", "Тяга верхнего блока", 35f, 12, 2.5f),
        DemoExercise("demo_shoulder_press", "Жим гантелей сидя", 10f, 10, 1f),
        DemoExercise("demo_row_cable", "Тяга горизонтального блока", 30f, 12, 2.5f),
        DemoExercise("demo_curl", "Сгибание рук с гантелями", 8f, 12, 1f)
    ))
    private val lower = DemoTemplate("DEMO_LOWER", "Низ тела", listOf(
        DemoExercise("demo_leg_press", "Жим ногами", 70f, 12, 5f),
        DemoExercise("demo_rdl", "Румынская тяга с гантелями", 20f, 10, 2f),
        DemoExercise("demo_leg_curl", "Сгибание ног лёжа", 25f, 12, 2.5f),
        DemoExercise("demo_glute_bridge", "Ягодичный мостик", 20f, 15, 2.5f),
        DemoExercise("demo_calf", "Подъём на носки", 30f, 15, 2.5f)
    ))
    private val full = DemoTemplate("DEMO_FULL", "Всё тело", listOf(
        DemoExercise("demo_goblet_squat", "Гоблет-присед", 12f, 12, 2f),
        DemoExercise("demo_pushup", "Отжимания", 0f, 12, 0f),
        DemoExercise("demo_row_db", "Тяга гантели в наклоне", 12f, 12, 1f),
        DemoExercise("demo_plank", "Планка", 0f, 1, 0f),
        DemoExercise("demo_cable_crunch", "Скручивания на блоке", 15f, 15, 2.5f)
    ))

    private suspend fun seedWorkouts(profile: UserProfile, today: LocalDate, rnd: Random) {
        val fourDays = profile.daysPerWeek >= 4
        // Смещения назад в днях; последняя тренировка — позавчера, чтобы главная показывала «следующую».
        val offsets = if (fourDays) listOf(20, 19, 17, 15, 13, 12, 10, 8, 6, 2)
        else listOf(20, 18, 16, 13, 11, 9, 6, 4, 2)
        val order = if (fourDays) listOf(upper, lower, upper, lower) else listOf(upper, lower, full)
        val cycle = offsets.size.coerceAtMost(9)
        val chosen = offsets.takeLast(cycle)

        chosen.forEachIndexed { index, daysAgo ->
            val template = order[index % order.size]
            val week = index / profile.daysPerWeek.coerceAtLeast(1) + 1
            val date = today.minusDays(daysAgo.toLong())
            val workoutId = UUID.randomUUID().toString()
            val rpe = listOf(6, 7, 7, 8, 7, 8, 8, 9, 7)[index % 9]
            val workout = Workout(
                id = workoutId,
                date = date.at(LocalTime.of(19, 0)),
                programKey = profile.programKey,
                planKey = template.planKey,
                phaseName = template.title,
                weekNumber = week,
                isCompleted = true,
                durationMinutes = 44 + rnd.nextInt(0, 14),
                painLevel = if (index == 1) 2 else 0,
                rpe = rpe,
                notes = if (index == chosen.lastIndex) "Отлично зашла тренировка, +2 кг в жиме" else ""
            )
            workoutRepo.saveWorkout(workout)
            workoutRepo.insertSets(buildSets(workoutId, template, week, rnd))
        }

        // Групповые занятия клуба
        listOf(14 to "Zumba", 7 to "Stretching").forEach { (daysAgo, title) ->
            workoutRepo.saveWorkout(
                Workout(
                    id = UUID.randomUUID().toString(),
                    date = today.minusDays(daysAgo.toLong()).at(LocalTime.of(18, 30)),
                    programKey = profile.programKey,
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

    private fun buildSets(workoutId: String, template: DemoTemplate, week: Int, rnd: Random): List<ExerciseSet> =
        template.exercises.flatMap { ex ->
            val kg = ex.baseKg + ex.stepKg * (week - 1)
            (1..3).map { n ->
                val actualReps = (ex.reps - (n - 1) + rnd.nextInt(-1, 2)).coerceAtLeast(if (ex.reps == 1) 1 else 6)
                ExerciseSet(
                    id = UUID.randomUUID().toString(),
                    workoutId = workoutId,
                    exerciseId = ex.id,
                    exerciseName = ex.name,
                    setNumber = n,
                    targetReps = ex.reps,
                    actualReps = if (ex.reps == 1) 1 else actualReps,
                    targetWeight = if (kg <= 0f) "б/в" else "средний",
                    actualWeight = if (kg <= 0f) null else kg,
                    isDone = true,
                    restSeconds = 90
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
        val current = profile.weightKg
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
