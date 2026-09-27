package com.fitcoach.app.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.fitcoach.app.domain.repository.UserRepository
import com.fitcoach.app.domain.repository.WaterRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar
import java.util.concurrent.TimeUnit

@HiltWorker
class WaterReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val waterRepo: WaterRepository,
    private val userRepo: UserRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour < 9 || hour > 21) return Result.success()

        val profile = userRepo.getProfile() ?: return Result.success()
        if (!profile.onboardingCompleted) return Result.success()
        val totalToday = waterRepo.getTotalForDateSync(System.currentTimeMillis())

        if (totalToday < profile.waterGoalMl) {
            val left = profile.waterGoalMl - totalToday
            Notifications.show(
                context, Notifications.CHANNEL_WATER, ID,
                "Пора выпить воды 💧",
                "Сегодня $totalToday из ${profile.waterGoalMl} мл. Осталось $left мл — стакан воды сейчас."
            )
        }
        return Result.success()
    }

    companion object {
        private const val ID = 1001

        fun schedule(context: Context, intervalHours: Long = 3) {
            val request = PeriodicWorkRequestBuilder<WaterReminderWorker>(intervalHours, TimeUnit.HOURS)
                .setInitialDelay(intervalHours, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "water_reminder", ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
