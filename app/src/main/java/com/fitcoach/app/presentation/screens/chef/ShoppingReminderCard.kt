package com.fitcoach.app.presentation.screens.chef

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitcoach.app.R
import com.fitcoach.app.domain.model.ShoppingReminder
import com.fitcoach.app.l10n.DomainTranslations
import com.fitcoach.app.presentation.components.FitCard
import com.fitcoach.app.presentation.components.SelectableChip
import com.fitcoach.app.presentation.theme.FitCoachColors
import com.fitcoach.app.workers.ShoppingReminderScheduler
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Карточка «Напоминание»: день недели (чипы Пн…Вс), время (TimePicker M3), переключатель вкл/выкл. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingReminderCard(reminder: ShoppingReminder, onChange: (ShoppingReminder) -> Unit) {
    var showTimePicker by remember { mutableStateOf(false) }
    val dayLabels = listOf(
        R.string.day_short_mon, R.string.day_short_tue, R.string.day_short_wed, R.string.day_short_thu,
        R.string.day_short_fri, R.string.day_short_sat, R.string.day_short_sun
    )

    FitCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("🔔 " + stringResource(R.string.shopping_reminder_title), fontWeight = FontWeight.SemiBold, color = FitCoachColors.TextPrimary)
                Text(stringResource(R.string.shopping_reminder_text), fontSize = 12.sp, color = FitCoachColors.TextMuted, lineHeight = 16.sp)
            }
            Switch(
                checked = reminder.enabled,
                onCheckedChange = { onChange(reminder.copy(enabled = it)) },
                colors = SwitchDefaults.colors(checkedThumbColor = FitCoachColors.AccentOn, checkedTrackColor = FitCoachColors.Accent)
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            dayLabels.forEachIndexed { index, res ->
                val day = index + 1
                SelectableChip(
                    text = stringResource(res),
                    selected = reminder.dayOfWeek == day,
                    onClick = { onChange(reminder.copy(dayOfWeek = day)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.shopping_reminder_time), color = FitCoachColors.TextSecondary)
            TextButton(onClick = { showTimePicker = true }) {
                Text(String.format(Locale.ROOT, "%02d:%02d", reminder.hour, reminder.minute), color = FitCoachColors.Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (reminder.enabled) {
            val next = ShoppingReminderScheduler.nextTrigger(reminder.dayOfWeek, reminder.hour, reminder.minute, System.currentTimeMillis())
            val formatter = DateTimeFormatter.ofPattern("EEE, d MMM HH:mm", DomainTranslations.currentLocale())
            Text(
                stringResource(R.string.shopping_reminder_next, formatter.format(Instant.ofEpochMilli(next).atZone(ZoneId.systemDefault()))),
                fontSize = 12.sp, color = FitCoachColors.TextMuted
            )
        }
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(initialHour = reminder.hour, initialMinute = reminder.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor = FitCoachColors.Card,
            title = { Text(stringResource(R.string.shopping_reminder_pick_time), color = FitCoachColors.TextPrimary) },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    showTimePicker = false
                    onChange(reminder.copy(hour = timeState.hour, minute = timeState.minute))
                }) { Text(stringResource(R.string.common_done), color = FitCoachColors.Accent) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.common_cancel), color = FitCoachColors.TextMuted) }
            }
        )
    }
}
