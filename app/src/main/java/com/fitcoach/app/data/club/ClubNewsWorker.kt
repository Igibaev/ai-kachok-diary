package com.fitcoach.app.data.club

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fitcoach.app.R
import com.fitcoach.app.workers.Notifications
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Раз в 6 часов подтягивает club.json с сервера и показывает локальный push о новой акции. */
@HiltWorker
class ClubNewsWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val clubRepository: ClubRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val fresh = runCatching { clubRepository.refresh() }.getOrElse { return Result.retry() }
        fresh.take(MAX_NOTIFICATIONS).forEachIndexed { index, promo ->
            Notifications.show(
                context,
                Notifications.CHANNEL_CLUB,
                ID_BASE + index,
                context.getString(R.string.notif_promo_title, promo.title),
                promo.text.ifBlank { context.getString(R.string.notif_promo_text) }
            )
        }
        return Result.success()
    }

    companion object {
        private const val ID_BASE = 3001
        private const val MAX_NOTIFICATIONS = 2
        const val WORK_NAME = "club_news"

        fun schedule(context: Context, intervalHours: Long = 6) {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val request = PeriodicWorkRequestBuilder<ClubNewsWorker>(intervalHours, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setInitialDelay(30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
