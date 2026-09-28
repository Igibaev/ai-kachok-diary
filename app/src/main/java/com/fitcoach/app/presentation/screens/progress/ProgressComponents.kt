package com.fitcoach.app.presentation.screens.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitcoach.app.data.local.db.entity.BodyMeasurementEntity
import com.fitcoach.app.domain.program.Achievements
import com.fitcoach.app.presentation.theme.FitCoachColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun StatItem(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = color)
        Text(label, fontSize = 11.sp, color = FitCoachColors.TextMuted)
    }
}

/** Сетка бейджей 3 в ряд; неполученные — приглушены. */
@Composable
fun BadgesGrid(all: List<Achievements.Badge>, earned: List<Achievements.Badge>) {
    val earnedIds = earned.map { it.id }.toSet()
    all.chunked(3).forEach { row ->
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { badge ->
                val got = badge.id in earnedIds
                Column(
                    modifier = Modifier.weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (got) FitCoachColors.AccentSoft else FitCoachColors.Surface)
                        .border(1.dp, if (got) FitCoachColors.Accent.copy(alpha = 0.5f) else FitCoachColors.Border, RoundedCornerShape(12.dp))
                        .padding(vertical = 10.dp, horizontal = 6.dp)
                        .alpha(if (got) 1f else 0.45f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(badge.emoji, fontSize = 24.sp)
                    Text(badge.title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FitCoachColors.TextPrimary, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(badge.description, fontSize = 9.sp, color = FitCoachColors.TextMuted, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
fun MeasurementRow(m: BodyMeasurementEntity, onDelete: () -> Unit) {
    val fmt = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy") }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(Instant.ofEpochMilli(m.date).atZone(ZoneId.systemDefault()).toLocalDate().format(fmt), fontSize = 13.sp, color = FitCoachColors.TextSecondary)
                Text("%.1f кг".format(m.weightKg), fontSize = 14.sp, color = FitCoachColors.TextPrimary, fontWeight = FontWeight.Bold)
                m.waistCm?.let { Text("талия %.0f см".format(it), fontSize = 11.sp, color = FitCoachColors.TextMuted) }
            }
            if (m.notes.isNotBlank()) Text(m.notes, fontSize = 11.sp, color = FitCoachColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Удалить замер", tint = FitCoachColors.TextMuted, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun AddMeasurementDialog(defaultWeight: Float?, onDismiss: () -> Unit, onAdd: (Float, Float?, String) -> Unit) {
    var weight by remember { mutableStateOf(defaultWeight?.let { "%.1f".format(it).replace(',', '.') } ?: "") }
    var waist by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val parsedWeight = weight.replace(',', '.').toFloatOrNull()

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = FitCoachColors.TextPrimary, unfocusedTextColor = FitCoachColors.TextPrimary,
        focusedBorderColor = FitCoachColors.Accent, unfocusedBorderColor = FitCoachColors.Border,
        cursorColor = FitCoachColors.Accent
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FitCoachColors.Card,
        title = { Text("Новый замер", color = FitCoachColors.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = weight, onValueChange = { weight = it }, singleLine = true,
                    label = { Text("Вес, кг", color = FitCoachColors.TextMuted) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(), colors = fieldColors
                )
                OutlinedTextField(
                    value = waist, onValueChange = { waist = it }, singleLine = true,
                    label = { Text("Талия, см (необязательно)", color = FitCoachColors.TextMuted) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(), colors = fieldColors
                )
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text("Заметка", color = FitCoachColors.TextMuted) },
                    modifier = Modifier.fillMaxWidth(), colors = fieldColors
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { parsedWeight?.let { onAdd(it, waist.replace(',', '.').toFloatOrNull(), notes.trim()) } },
                enabled = parsedWeight != null && parsedWeight in 30f..300f,
                colors = ButtonDefaults.buttonColors(containerColor = FitCoachColors.Accent, contentColor = FitCoachColors.AccentOn)
            ) { Text("Сохранить", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = FitCoachColors.TextMuted) } }
    )
}
