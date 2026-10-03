package com.fitcoach.app.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fitcoach.app.R
import com.fitcoach.app.domain.repository.MealPlanRepository
import com.fitcoach.app.domain.repository.UserRepository
import com.fitcoach.app.l10n.withLanguage
import com.fitcoach.app.presentation.navigation.Screen
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Напоминание о покупках. Срабатывает в выбранный момент (`main`) и за 24 часа до него (`pre`).
 * Не уведомляет, если напоминание выключено, список пуст или всё куплено, а также если WorkManager
 * запустил задание сильно раньше срока (до выбранного момента ещё > 24 ч — например, после смены настроек).
 * Тап по уведомлению открывает список покупок (extra `nav_route`).
 */
@HiltWorker
class ShoppingReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val mealPlanRepo: MealPlanRepository,
    private val userRepo: UserRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val reminder = mealPlanRepo.getReminder()
        if (!reminder.enabled) return Result.success()
        val kind = inputData.getString(ShoppingReminderScheduler.KEY_KIND) ?: ShoppingReminderScheduler.KIND_MAIN
        val now = System.currentTimeMillis()

        // Для main после срабатывания планируем следующую неделю; pre перепланирует schedule() вместе с main.
        if (kind == ShoppingReminderScheduler.KIND_MAIN) {
            ShoppingReminderScheduler.schedule(context, reminder, now, minDelayMs = 60 * 60 * 1000L)
        }

        val remaining = mealPlanRepo.getShoppingItems().count { !it.checked }
        if (remaining == 0) return Result.success()

        // «Если до выбранного момента > 24 ч — ничего»: pre допускает окно до 24 ч, main — только около срока.
        val untilNext = ShoppingReminderScheduler.nextTrigger(reminder.dayOfWeek, reminder.hour, reminder.minute, now) - now
        val tolerance = 6 * 60 * 60 * 1000L
        val onTime = if (kind == ShoppingReminderScheduler.KIND_PRE) untilNext <= ShoppingReminderScheduler.DAY_MS + tolerance
        else untilNext >= 7 * ShoppingReminderScheduler.DAY_MS - tolerance // только что наступил момент → до следующего ~неделя
        if (!onTime) return Result.success()

        val res = context.withLanguage(runCatching { userRepo.getProfile()?.language }.getOrNull() ?: "")
        val text = if (kind == ShoppingReminderScheduler.KIND_PRE) res.resources.getQuantityString(R.plurals.plural_notif_shopping_pre, remaining, remaining)
        else res.resources.getQuantityString(R.plurals.plural_notif_shopping, remaining, remaining)
        Notifications.show(
            context, Notifications.CHANNEL_SHOPPING, ID,
            res.getString(R.string.notif_shopping_title), text,
            route = Screen.ShoppingList.route
        )
        return Result.success()
    }

    companion object {
        private const val ID = 1004
    }
}
