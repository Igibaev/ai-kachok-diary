package com.fitcoach.app.presentation.screens.water

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fitcoach.app.R
import com.fitcoach.app.l10n.DomainTranslations
import com.fitcoach.app.presentation.components.CircularProgress
import com.fitcoach.app.presentation.components.FitCard
import com.fitcoach.app.presentation.theme.FitCoachColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaterScreen(onBack: () -> Unit = {}, viewModel: WaterViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var customInput by remember { mutableStateOf("") }
    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val zone = remember { ZoneId.systemDefault() }
    val goal = state.profile.waterGoalMl.coerceAtLeast(1)

    Scaffold(
        containerColor = FitCoachColors.Background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.water_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = FitCoachColors.Background,
                    titleContentColor = FitCoachColors.TextPrimary,
                    navigationIconContentColor = FitCoachColors.TextPrimary
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    CircularProgress(
                        value = state.totalMl.toFloat(), max = goal.toFloat(),
                        color = FitCoachColors.Water, label = stringResource(R.string.unit_ml), size = 170.dp, strokeWidth = 16.dp
                    )
                    Spacer(Modifier.height(8.dp))
                    val left = goal - state.totalMl
                    Text(
                        if (left > 0) stringResource(R.string.water_progress, state.totalMl, goal, left) else stringResource(R.string.water_goal_done, state.totalMl),
                        fontSize = 14.sp, color = FitCoachColors.TextSecondary
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(150 to R.string.water_glass, 250 to R.string.water_mug, 500 to R.string.water_bottle).forEach { (amount, labelRes) ->
                        OutlinedButton(
                            onClick = { viewModel.addWater(amount) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = FitCoachColors.Water),
                            border = BorderStroke(1.dp, FitCoachColors.Water),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("+$amount", fontWeight = FontWeight.Bold)
                                Text(stringResource(labelRes), fontSize = 10.sp, color = FitCoachColors.TextMuted)
                            }
                        }
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = customInput, onValueChange = { customInput = it.filter { ch -> ch.isDigit() }.take(4) },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.water_custom), color = FitCoachColors.TextMuted) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = FitCoachColors.TextPrimary, unfocusedTextColor = FitCoachColors.TextPrimary,
                            focusedBorderColor = FitCoachColors.Water, unfocusedBorderColor = FitCoachColors.Border, cursorColor = FitCoachColors.Water
                        ),
                        shape = RoundedCornerShape(12.dp), singleLine = true
                    )
                    Button(
                        onClick = { customInput.toIntOrNull()?.let { viewModel.addWater(it); customInput = "" } },
                        enabled = (customInput.toIntOrNull() ?: 0) > 0,
                        colors = ButtonDefaults.buttonColors(containerColor = FitCoachColors.Water, contentColor = FitCoachColors.Background),
                        shape = RoundedCornerShape(12.dp), modifier = Modifier.height(56.dp)
                    ) { Text(stringResource(R.string.common_add), fontWeight = FontWeight.Bold) }
                }
            }

            item { WeekBars(state.week, goal) }

            if (state.entries.isNotEmpty()) {
                item { Text(stringResource(R.string.water_today), style = MaterialTheme.typography.labelMedium, color = FitCoachColors.TextMuted) }
                items(state.entries.asReversed(), key = { it.id }) { entry ->
                    FitCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("💧", fontSize = 20.sp)
                                Column {
                                    Text(stringResource(R.string.format_ml, entry.amountMl), fontWeight = FontWeight.Bold, color = FitCoachColors.Water)
                                    Text(Instant.ofEpochMilli(entry.createdAt).atZone(zone).toLocalTime().format(timeFmt), fontSize = 11.sp, color = FitCoachColors.TextMuted)
                                }
                            }
                            IconButton(onClick = { viewModel.deleteEntry(entry) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_delete), tint = FitCoachColors.TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            } else {
                item { Text(stringResource(R.string.water_empty), fontSize = 12.sp, color = FitCoachColors.TextMuted) }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** Мини-бары за 7 дней: высота — доля от цели, сегодня выделено. */
@Composable
private fun WeekBars(week: List<DayTotal>, goalMl: Int) {
    if (week.isEmpty()) return
    val today = LocalDate.now()
    FitCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.water_week), style = MaterialTheme.typography.labelSmall, color = FitCoachColors.TextMuted)
            val avg = week.filter { it.date != today }.map { it.totalMl }.average().takeIf { !it.isNaN() }?.toInt() ?: 0
            Text(stringResource(R.string.water_average, avg), style = MaterialTheme.typography.labelSmall, color = FitCoachColors.TextMuted)
        }
        Spacer(Modifier.height(10.dp))
        val locale = DomainTranslations.currentLocale()
        Row(Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            week.forEach { day ->
                val frac = (day.totalMl / goalMl.toFloat()).coerceIn(0f, 1f)
                val isToday = day.date == today
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(FitCoachColors.Border), contentAlignment = Alignment.BottomCenter) {
                        Box(
                            Modifier.fillMaxWidth().fillMaxHeight(frac.coerceAtLeast(if (day.totalMl > 0) 0.06f else 0f))
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (frac >= 1f) FitCoachColors.Success else if (isToday) FitCoachColors.Water else FitCoachColors.Water.copy(alpha = 0.6f))
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).replaceFirstChar { it.uppercase() }, fontSize = 10.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (isToday) FitCoachColors.TextPrimary else FitCoachColors.TextMuted
                    )
                }
            }
        }
    }
}
