package com.fitcoach.app.workers

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.fitcoach.app.domain.model.ShoppingReminder
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Планирование напоминания «пора за покупками»: два OneTime-задания — за 24 часа до выбранного момента
 * (`pre`) и в сам момент (`main`). Worker после срабатывания перепланирует следующую неделю.
 * [nextTrigger] — чистая функция (тестируется без Android).
 */
object ShoppingReminderScheduler {
    const val WORK_MAIN = "shopping_reminder_main"
    const val WORK_PRE = "shopping_reminder_pre"
    const val KEY_KIND = "kind"
    const val KIND_MAIN = "main"
    const val KIND_PRE = "pre"
    const val DAY_MS = 24 * 60 * 60 * 1000L

    /**
     * Ближайший момент (epoch millis) строго после [now], когда наступит [dayOfWeek] (1 = Пн … 7 = Вс, ISO)
     * в [hour]:[minute] локального времени [zone]. Если выбранное время сегодня уже прошло (или равно now) — через неделю.
     */
    fun nextTrigger(dayOfWeek: Int, hour: Int, minute: Int, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val day = DayOfWeek.of(dayOfWeek.coerceIn(1, 7))
        val time = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        val nowZdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone)
        var candidate = nowZdt.toLocalDate().atTime(time).atZone(zone)
        // Сдвигаемся вперёд до нужного дня недели (0..6 дней), затем ещё на неделю, если момент не в будущем.
        val shift = (day.value - candidate.dayOfWeek.value + 7) % 7
        candidate = candidate.plusDays(shift.toLong())
        if (candidate.toInstant().toEpochMilli() <= now) candidate = candidate.plusWeeks(1)
        return candidate.toInstant().toEpochMilli()
    }

    /**
     * @param minDelayMs если ближайший момент наступает раньше, чем через [minDelayMs] (например, он только что
     *                   сработал и WorkManager запустил задание с опозданием), берём следующую неделю.
     */
    fun schedule(context: Context, reminder: ShoppingReminder, now: Long = System.currentTimeMillis(), minDelayMs: Long = 0L) {
        val wm = WorkManager.getInstance(context)
        if (!reminder.enabled) {
            cancel(context)
            return
        }
        var trigger = nextTrigger(reminder.dayOfWeek, reminder.hour, reminder.minute, now)
        if (trigger - now < minDelayMs) trigger += 7 * DAY_MS
        enqueue(wm, WORK_MAIN, KIND_MAIN, trigger - now)
        val preDelay = trigger - DAY_MS - now
        if (preDelay > 0) enqueue(wm, WORK_PRE, KIND_PRE, preDelay) else wm.cancelUniqueWork(WORK_PRE)
    }

    fun cancel(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(WORK_MAIN)
        wm.cancelUniqueWork(WORK_PRE)
    }

    private fun enqueue(wm: WorkManager, name: String, kind: String, delayMs: Long) {
        val request = OneTimeWorkRequestBuilder<ShoppingReminderWorker>()
            .setInitialDelay(delayMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putString(KEY_KIND, kind).build())
            .build()
        wm.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
    }
}
