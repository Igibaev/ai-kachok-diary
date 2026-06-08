package com.fitcoach.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fitcoach.app.data.local.db.dao.*
import com.fitcoach.app.data.local.db.entity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

@Database(
    entities = [
        WorkoutEntity::class,
        ExerciseSetEntity::class,
        NutritionEntryEntity::class,
        WaterEntryEntity::class,
        ChatMessageEntity::class,
        UserProfileEntity::class,
        BodyMeasurementEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
    abstract fun exerciseSetDao(): ExerciseSetDao
    abstract fun nutritionDao(): NutritionDao
    abstract fun waterDao(): WaterDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun bodyMeasurementDao(): BodyMeasurementDao
}

class PrepopulateCallback(private val db: AppDatabase) : RoomDatabase.Callback() {
    override fun onCreate(sqLiteDatabase: SupportSQLiteDatabase) {
        super.onCreate(sqLiteDatabase)
        CoroutineScope(Dispatchers.IO).launch {
            prepopulateNutritionTemplates()
            insertDefaultProfile()
        }
    }

    private suspend fun insertDefaultProfile() {
        db.userProfileDao().insertProfile(
            UserProfileEntity(
                id = 1,
                name = "",
                programStartDate = System.currentTimeMillis()
            )
        )
    }

    private suspend fun prepopulateNutritionTemplates() {
        val templates = listOf(
            NutritionEntryEntity(
                id = UUID.randomUUID().toString(),
                date = 0L,
                mealType = "TEMPLATE",
                name = "Куриная грудка варёная",
                calories = 165, proteinG = 31f, carbsG = 0f, fatG = 3.6f, grams = 100f
            ),
            NutritionEntryEntity(
                id = UUID.randomUUID().toString(),
                date = 0L, mealType = "TEMPLATE",
                name = "Гречка варёная",
                calories = 92, proteinG = 3.4f, carbsG = 19.9f, fatG = 0.6f, grams = 100f
            ),
            NutritionEntryEntity(
                id = UUID.randomUUID().toString(),
                date = 0L, mealType = "TEMPLATE",
                name = "Творог 5%",
                calories = 121, proteinG = 17f, carbsG = 3f, fatG = 5f, grams = 100f
            ),
            NutritionEntryEntity(
                id = UUID.randomUUID().toString(),
                date = 0L, mealType = "TEMPLATE",
                name = "Яйцо куриное",
                calories = 78, proteinG = 6f, carbsG = 0.5f, fatG = 5.3f, grams = 55f
            ),
            NutritionEntryEntity(
                id = UUID.randomUUID().toString(),
                date = 0L, mealType = "TEMPLATE",
                name = "Овсянка сухая",
                calories = 367, proteinG = 13f, carbsG = 62f, fatG = 7f, grams = 100f
            ),
            NutritionEntryEntity(
                id = UUID.randomUUID().toString(),
                date = 0L, mealType = "TEMPLATE",
                name = "Молоко 2.5%",
                calories = 52, proteinG = 2.8f, carbsG = 4.7f, fatG = 2.5f, grams = 100f
            ),
            NutritionEntryEntity(
                id = UUID.randomUUID().toString(),
                date = 0L, mealType = "TEMPLATE",
                name = "Рис варёный",
                calories = 130, proteinG = 2.7f, carbsG = 28.2f, fatG = 0.3f, grams = 100f
            ),
            NutritionEntryEntity(
                id = UUID.randomUUID().toString(),
                date = 0L, mealType = "TEMPLATE",
                name = "Хек запечённый",
                calories = 86, proteinG = 18f, carbsG = 0f, fatG = 1.4f, grams = 100f
            ),
            NutritionEntryEntity(
                id = UUID.randomUUID().toString(),
                date = 0L, mealType = "TEMPLATE",
                name = "Говядина тушёная",
                calories = 193, proteinG = 25f, carbsG = 0f, fatG = 10f, grams = 100f
            ),
            NutritionEntryEntity(
                id = UUID.randomUUID().toString(),
                date = 0L, mealType = "TEMPLATE",
                name = "Банан",
                calories = 105, proteinG = 1.3f, carbsG = 27f, fatG = 0.4f, grams = 120f
            ),
            NutritionEntryEntity(
                id = UUID.randomUUID().toString(),
                date = 0L, mealType = "TEMPLATE",
                name = "Греческий йогурт 2%",
                calories = 59, proteinG = 10f, carbsG = 3.6f, fatG = 0.4f, grams = 100f
            ),
            NutritionEntryEntity(
                id = UUID.randomUUID().toString(),
                date = 0L, mealType = "TEMPLATE",
                name = "Кефир 1%",
                calories = 40, proteinG = 3.4f, carbsG = 4.7f, fatG = 1f, grams = 100f
            ),
            NutritionEntryEntity(
                id = UUID.randomUUID().toString(),
                date = 0L, mealType = "TEMPLATE",
                name = "Картофель варёный",
                calories = 83, proteinG = 2f, carbsG = 17f, fatG = 0.4f, grams = 100f
            ),
            NutritionEntryEntity(
                id = UUID.randomUUID().toString(),
                date = 0L, mealType = "TEMPLATE",
                name = "Хлеб цельнозерновой",
                calories = 63, proteinG = 3f, carbsG = 11f, fatG = 0.9f, grams = 30f
            ),
            NutritionEntryEntity(
                id = UUID.randomUUID().toString(),
                date = 0L, mealType = "TEMPLATE",
                name = "Оливковое масло",
                calories = 45, proteinG = 0f, carbsG = 0f, fatG = 5f, grams = 5f
            )
        )
        templates.forEach { db.nutritionDao().insertEntry(it) }
    }
}
