package com.fitcoach.app.domain.service

import android.content.Context

/** Данные для карточки «Поделиться в Stories» после тренировки. */
data class WorkoutShareData(
    val userName: String,
    val workoutTitle: String,
    val doneSets: Int,
    val totalSets: Int,
    val durationMinutes: Int,
    val volumeKg: Int,
    val streakWorkouts: Int,
    val personalRecords: Int,
    val referralCode: String
)

/** Данные для карточки прогресса (вес, тренировки за период). */
data class ProgressShareData(
    val userName: String,
    val startWeightKg: Float,
    val currentWeightKg: Float,
    val workoutsCompleted: Int,
    val weeksInProgram: Int,
    val referralCode: String
)

interface ShareService {
    /** Рисует карточку 1080×1920 с брендом клуба и открывает системный share (Instagram Stories в приоритете). */
    suspend fun shareWorkoutCard(context: Context, data: WorkoutShareData): Result<Unit>
    suspend fun shareProgressCard(context: Context, data: ProgressShareData): Result<Unit>
}

/** Заглушка до реализации агентом «Клуб». */
class NoopShareService : ShareService {
    override suspend fun shareWorkoutCard(context: Context, data: WorkoutShareData): Result<Unit> =
        Result.failure(UnsupportedOperationException("Share not implemented"))

    override suspend fun shareProgressCard(context: Context, data: ProgressShareData): Result<Unit> =
        Result.failure(UnsupportedOperationException("Share not implemented"))
}
