package com.fitcoach.app.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
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
        if (hour < 8 || hour > 20) return Result.success()

        val profile = userRepo.getProfile() ?: return Result.success()
        val totalToday = waterRepo.getTotalForDateSync(System.currentTimeMillis())

        if (totalToday < profile.waterGoalMl) {
            showNotification(totalToday, profile.waterGoalMl)
        }

        return Result.success()
    }

    private fun showNotification(current: Int, goal: Int) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        val channelId = "water_reminders"

        val channel = NotificationChannel(channelId, "Напоминания о воде", NotificationManager.IMPORTANCE_DEFAULT)
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Выпей воды! 💧")
            .setContentText("Выпито ${current}мл из ${goal}мл. Выпей стакан воды!")
            .setAutoCancel(true)
            .build()

        manager.notify(1001, notification)
    }

    companion object {
        fun schedule(context: Context, intervalHours: Int = 2) {
            val request = PeriodicWorkRequestBuilder<WaterReminderWorker>(
                intervalHours.toLong(), TimeUnit.HOURS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "water_reminder",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
