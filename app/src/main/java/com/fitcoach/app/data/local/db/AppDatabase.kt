package com.fitcoach.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fitcoach.app.data.local.db.dao.*
import com.fitcoach.app.data.local.db.entity.*
import com.fitcoach.app.data.local.db.seed.FoodSeed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
    version = 2,
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

/** Первичное наполнение: профиль по умолчанию (onboarding не пройден) и база продуктов. */
class PrepopulateCallback(private val db: AppDatabase) : RoomDatabase.Callback() {
    override fun onCreate(sqLiteDatabase: SupportSQLiteDatabase) {
        super.onCreate(sqLiteDatabase)
        CoroutineScope(Dispatchers.IO).launch {
            db.userProfileDao().insertProfile(UserProfileEntity(id = 1))
            FoodSeed.templates().forEach { db.nutritionDao().insertEntry(it) }
        }
    }
}
