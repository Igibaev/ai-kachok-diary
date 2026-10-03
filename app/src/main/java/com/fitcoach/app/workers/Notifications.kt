package com.fitcoach.app.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fitcoach.app.MainActivity
import com.fitcoach.app.R

/** Общие помощники для уведомлений: каналы, иконка бренда, переход в приложение по тапу. */
object Notifications {
    const val CHANNEL_WATER = "water_reminders"
    const val CHANNEL_WORKOUT = "workout_reminders"
    const val CHANNEL_CLUB = "club_news"
    const val CHANNEL_SHOPPING = "shopping_reminders"

    /** Extra Intent с маршрутом навигации, который MainActivity откроет после старта (см. Screen.route). */
    const val EXTRA_ROUTE = "nav_route"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_WATER, context.getString(R.string.notification_channel_water), NotificationManager.IMPORTANCE_DEFAULT)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_WORKOUT, context.getString(R.string.notification_channel_workout), NotificationManager.IMPORTANCE_DEFAULT)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_CLUB, context.getString(R.string.notification_channel_club), NotificationManager.IMPORTANCE_DEFAULT)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SHOPPING, context.getString(R.string.notification_channel_shopping), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(context: Context, channel: String, id: Int, title: String, text: String) =
        show(context, channel, id, title, text, route = null)

    /** @param route маршрут экрана (Screen.route), который откроется по тапу; null — просто запуск приложения. */
    fun show(context: Context, channel: String, id: Int, title: String, text: String, route: String?) {
        if (!canPost(context)) return
        ensureChannels(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (route != null) putExtra(EXTRA_ROUTE, route)
        }
        val pending = PendingIntent.getActivity(
            context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }
}
