package com.fitcoach.app.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fitcoach.app.brand.BrandConfig
import com.fitcoach.app.domain.program.ProgramCatalog
import com.fitcoach.app.domain.repository.UserRepository
import com.fitcoach.app.domain.repository.WorkoutRepository
import com.fitcoach.app.domain.util.DayBounds
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Раз в день (около 18:00) напоминает о тренировке, если сегодня ещё не тренировался
 * и последняя тренировка была 2+ дня назад. Текст — со следующей тренировкой программы.
 */
@HiltWorker
class WorkoutReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val userRepo: UserRepository,
    private val workoutRepo: WorkoutRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val profile = userRepo.getProfile() ?: return Result.success()
        if (!profile.onboardingCompleted) return Result.success()

        val completed = workoutRepo.observeCompletedWorkouts().first()
        val todayStart = DayBounds.startOfDay()
        if (completed.any { it.date >= todayStart }) return Result.success()

        val twoDaysAgo = System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000L
        val last = completed.maxOfOrNull { it.date }
        if (last != null && last > twoDaysAgo) return Result.success()

        val program = ProgramCatalog.getOrDefault(profile.programKey)
        val next = ProgramCatalog.nextWorkout(program, completed, profile.daysPerWeek)
        val name = profile.name.ifBlank { "Атлет" }
        Notifications.show(
            context, Notifications.CHANNEL_WORKOUT, ID,
            "$name, тренировка ждёт 💪",
            "Следующая тренировка: ${next.template.title} · ${next.template.exercises.size} упражнений · ~${next.template.estimatedMinutes} мин. ${BrandConfig.clubName} ждёт тебя!"
        )
        return Result.success()
    }

    companion object {
        private const val ID = 1002

        fun schedule(context: Context) {
            val now = Calendar.getInstance()
            val target = (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 18); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
                if (before(now)) add(Calendar.DAY_OF_MONTH, 1)
            }
            val delay = target.timeInMillis - now.timeInMillis
            val request = PeriodicWorkRequestBuilder<WorkoutReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "workout_reminder", ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
