package com.fitcoach.app.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.fitcoach.app.domain.model.WorkoutPlan
import com.fitcoach.app.domain.repository.UserRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@HiltWorker
class WorkoutReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val userRepo: UserRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val profile = userRepo.getProfile() ?: return Result.success()
        val startDate = LocalDate.ofEpochDay(profile.programStartDate / 86400000L)
        val workoutKey = WorkoutPlan.getWorkoutForDate(LocalDate.now(), startDate)

        if (workoutKey != null) {
            showNotification(workoutKey)
        }

        return Result.success()
    }

    private fun showNotification(planKey: String) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        val channelId = "workout_reminders"

        val channel = NotificationChannel(channelId, "Напоминания о тренировках", NotificationManager.IMPORTANCE_DEFAULT)
        manager.createNotificationChannel(channel)

        val template = WorkoutPlan.getTemplate(planKey)
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Время тренировки! 💪")
            .setContentText("Сегодня: $planKey — ${template?.phaseName ?: ""}. Не пропусти!")
            .setAutoCancel(true)
            .build()

        manager.notify(1002, notification)
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WorkoutReminderWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "workout_reminder",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
