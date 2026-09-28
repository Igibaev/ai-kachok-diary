package com.fitcoach.app.di

import android.content.Context
import androidx.room.Room
import com.fitcoach.app.BuildConfig
import com.fitcoach.app.data.local.db.AppDatabase
import com.fitcoach.app.data.local.db.PrepopulateCallback
import com.fitcoach.app.data.remote.api.AnthropicApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase {
        var db: AppDatabase? = null
        db = Room.databaseBuilder(ctx, AppDatabase::class.java, "fitcoach.db")
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onCreate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                    super.onCreate(database)
                    db?.let { PrepopulateCallback(it).onCreate(database) }
                }

                override fun onDestructiveMigration(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                    super.onDestructiveMigration(database)
                    db?.let { PrepopulateCallback(it).onDestructiveMigration(database) }
                }
            })
            // TODO(release): до публикации в Play — exportSchema=true + явные Migration, fallback оставить только в debug:
            // иначе первое же изменение Entity сотрёт дневник здоровья пользователей.
            .fallbackToDestructiveMigration()
            .build()
        return db
    }

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .apply {
            if (BuildConfig.DEBUG) {
                // Заголовки (в т.ч. ключи) в лог не пишем даже в debug.
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            }
        }
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl("https://api.anthropic.com/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideAnthropicApi(retrofit: Retrofit): AnthropicApi =
        retrofit.create(AnthropicApi::class.java)

    // DAOs
    @Provides fun provideWorkoutDao(db: AppDatabase) = db.workoutDao()
    @Provides fun provideExerciseSetDao(db: AppDatabase) = db.exerciseSetDao()
    @Provides fun provideNutritionDao(db: AppDatabase) = db.nutritionDao()
    @Provides fun provideWaterDao(db: AppDatabase) = db.waterDao()
    @Provides fun provideChatMessageDao(db: AppDatabase) = db.chatMessageDao()
    @Provides fun provideUserProfileDao(db: AppDatabase) = db.userProfileDao()
    @Provides fun provideBodyMeasurementDao(db: AppDatabase) = db.bodyMeasurementDao()
}
