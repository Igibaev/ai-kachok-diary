package com.fitcoach.app.presentation.screens.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fitcoach.app.R
import com.fitcoach.app.l10n.tr
import com.fitcoach.app.presentation.components.FitCard
import com.fitcoach.app.presentation.components.LabeledProgressBar
import com.fitcoach.app.presentation.theme.FitCoachColors
import kotlinx.coroutines.launch

@Composable
fun ProgressScreen(viewModel: ProgressViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Box(Modifier.fillMaxSize().background(FitCoachColors.Background)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.progress_title), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = FitCoachColors.TextPrimary)
                FloatingActionButton(onClick = { showAdd = true }, containerColor = FitCoachColors.Accent, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.progress_add_measurement), tint = FitCoachColors.AccentOn)
                }
            }

            // Вес
            FitCard {
                Text(stringResource(R.string.progress_weight_90), style = MaterialTheme.typography.labelSmall, color = FitCoachColors.TextMuted)
                Spacer(Modifier.height(8.dp))
                if (state.chartPoints.size >= 2) {
                    WeightChart(
                        points = state.chartPoints.map { WeightPoint(it.date, it.weightKg) },
                        targetKg = state.profile.targetWeightKg
                    )
                } else {
                    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(if (state.chartPoints.isEmpty()) R.string.progress_chart_empty else R.string.progress_chart_need_two),
                            fontSize = 12.sp, color = FitCoachColors.TextMuted
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatItem(stringResource(R.string.progress_start), state.startWeight?.let { "%.1f".format(it) } ?: "—", FitCoachColors.TextSecondary)
                    StatItem(stringResource(R.string.progress_now), state.currentWeight?.let { "%.1f".format(it) } ?: "—", FitCoachColors.Accent)
                    StatItem(stringResource(R.string.progress_target), state.profile.targetWeightKg?.let { "%.1f".format(it) } ?: "—", FitCoachColors.Success)
                    val diff = if (state.startWeight != null && state.currentWeight != null) state.currentWeight!! - state.startWeight!! else null
                    StatItem(
                        stringResource(R.string.progress_change),
                        diff?.let { (if (it > 0) "+" else "") + "%.1f".format(it) } ?: "—",
                        when { diff == null -> FitCoachColors.TextMuted; diff <= 0f -> FitCoachColors.Success; else -> FitCoachColors.Warning }
                    )
                }
                Spacer(Modifier.height(12.dp))
                val shareFailed = stringResource(R.string.progress_share_failed)
                Button(
                    onClick = {
                        viewModel.share(context) { result ->
                            if (result.isFailure) scope.launch { snackbar.showSnackbar(shareFailed) }
                        }
                    },
                    enabled = !state.isSharing,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FitCoachColors.Accent, contentColor = FitCoachColors.AccentOn)
                ) {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (state.isSharing) R.string.progress_share_preparing else R.string.progress_share), fontWeight = FontWeight.Bold)
                }
            }

            // Программа
            FitCard {
                Text(stringResource(R.string.progress_program, state.programTitle.tr().uppercase()), style = MaterialTheme.typography.labelSmall, color = FitCoachColors.TextMuted)
                Spacer(Modifier.height(10.dp))
                LabeledProgressBar(
                    value = state.programCompleted.toFloat(), max = state.programTotal.toFloat(), color = FitCoachColors.Accent,
                    label = stringResource(R.string.progress_workouts_done), sub = stringResource(R.string.progress_ratio, state.programCompleted, state.programTotal)
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatItem(stringResource(R.string.progress_week), stringResource(R.string.progress_ratio, state.weekInProgram, ProgressViewModel.PROGRAM_WEEKS), FitCoachColors.TextPrimary)
                    StatItem(stringResource(R.string.progress_streak), "${state.streakWeeks}", if (state.streakWeeks > 0) FitCoachColors.Accent else FitCoachColors.TextSecondary)
                    StatItem(stringResource(R.string.progress_lifted), "%,d".format(state.totalVolumeKg).replace(',', ' '), FitCoachColors.TextPrimary)
                }
            }

            // Бейджи
            FitCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.progress_badges), style = MaterialTheme.typography.labelSmall, color = FitCoachColors.TextMuted)
                    Text("${state.earnedBadges.size}/${state.allBadges.size}", style = MaterialTheme.typography.labelSmall, color = FitCoachColors.Accent)
                }
                Spacer(Modifier.height(6.dp))
                BadgesGrid(state.allBadges, state.earnedBadges)
            }

            // История замеров
            FitCard {
                Text(stringResource(R.string.progress_measurements), style = MaterialTheme.typography.labelSmall, color = FitCoachColors.TextMuted)
                Spacer(Modifier.height(6.dp))
                if (state.measurements.isEmpty()) {
                    Text(stringResource(R.string.progress_measurements_empty), fontSize = 12.sp, color = FitCoachColors.TextMuted)
                } else {
                    state.measurements.forEachIndexed { index, m ->
                        if (index > 0) HorizontalDivider(color = FitCoachColors.Border)
                        MeasurementRow(m) { viewModel.deleteMeasurement(m) }
                    }
                }
            }

            Spacer(Modifier.height(80.dp))
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp))
    }

    if (showAdd) {
        AddMeasurementDialog(
            defaultWeight = state.currentWeight,
            onDismiss = { showAdd = false },
            onAdd = { w, waist, notes -> viewModel.addMeasurement(w, waist, notes); showAdd = false }
        )
    }
}
