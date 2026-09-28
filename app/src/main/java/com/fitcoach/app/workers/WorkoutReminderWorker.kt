package com.fitcoach.app.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fitcoach.app.R
import com.fitcoach.app.brand.BrandConfig
import com.fitcoach.app.domain.program.ProgramCatalog
import com.fitcoach.app.domain.repository.UserRepository
import com.fitcoach.app.domain.repository.WorkoutRepository
import com.fitcoach.app.domain.util.DayBounds
import com.fitcoach.app.l10n.tr
import com.fitcoach.app.l10n.withLanguage
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

        // «2+ дня назад» — по календарным дням (как стрик и проверка «сегодня» выше), а не скользящие 48 ч:
        // иначе тренировка позавчера вечером при запуске в 18:00 ещё не считалась бы пропуском.
        val last = completed.maxOfOrNull { it.date }
        if (last != null && DayBounds.startOfDay(last) > todayStart - 2 * 24 * 60 * 60 * 1000L) return Result.success()

        val program = ProgramCatalog.getOrDefault(profile.programKey)
        val next = ProgramCatalog.nextWorkout(program, completed, profile.daysPerWeek)
        val res = context.withLanguage(profile.language)
        val name = profile.name.ifBlank { res.getString(R.string.dashboard_default_name) }
        val exercises = next.template.exercises.size
        Notifications.show(
            context, Notifications.CHANNEL_WORKOUT, ID,
            res.getString(R.string.notif_workout_title, name),
            res.getString(
                R.string.notif_workout_text,
                next.template.title.tr(profile.language),
                res.resources.getQuantityString(R.plurals.plural_exercises, exercises, exercises),
                next.template.estimatedMinutes,
                BrandConfig.clubName
            )
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
