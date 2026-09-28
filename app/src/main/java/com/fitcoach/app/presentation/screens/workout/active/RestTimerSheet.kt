package com.fitcoach.app.presentation.screens.workout.active

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitcoach.app.R
import com.fitcoach.app.domain.program.WorkoutTitles
import com.fitcoach.app.l10n.tr
import com.fitcoach.app.presentation.components.RingProgress
import com.fitcoach.app.presentation.theme.FitCoachColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestTimerSheet(rest: RestState, onAdjust: (Int) -> Unit, onSkip: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(rest.finished) {
        if (rest.finished) scope.launch(Dispatchers.Default) { signalRestOver(context) }
    }

    ModalBottomSheet(
        onDismissRequest = onSkip,
        sheetState = sheetState,
        containerColor = FitCoachColors.Surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = FitCoachColors.Border) }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(if (rest.finished) R.string.workout_rest_go else R.string.workout_rest_label), style = MaterialTheme.typography.labelSmall, color = FitCoachColors.TextMuted)

            Box(contentAlignment = Alignment.Center) {
                RingProgress(
                    value = rest.remainingSeconds.toFloat(), max = rest.totalSeconds.toFloat(),
                    color = if (rest.finished) FitCoachColors.Success else FitCoachColors.Accent,
                    size = 180.dp, strokeWidth = 14.dp
                )
                Text(
                    WorkoutTitles.formatDuration(rest.remainingSeconds.toLong()),
                    fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, color = FitCoachColors.TextPrimary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { onAdjust(-15) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = FitCoachColors.TextPrimary),
                    border = BorderStroke(1.dp, FitCoachColors.Border), shape = RoundedCornerShape(12.dp)
                ) { Text(stringResource(R.string.workout_rest_minus)) }
                OutlinedButton(
                    onClick = { onAdjust(15) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = FitCoachColors.TextPrimary),
                    border = BorderStroke(1.dp, FitCoachColors.Border), shape = RoundedCornerShape(12.dp)
                ) { Text(stringResource(R.string.workout_rest_plus)) }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.workout_rest_next), style = MaterialTheme.typography.labelSmall, color = FitCoachColors.TextMuted)
                Text(rest.nextExerciseName?.tr() ?: stringResource(R.string.workout_rest_next_exercise), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = FitCoachColors.TextPrimary)
                Text(stringResource(R.string.component_set_number, rest.nextSetNumber), fontSize = 13.sp, color = FitCoachColors.Accent)
            }

            Button(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FitCoachColors.Accent, contentColor = FitCoachColors.AccentOn),
                shape = RoundedCornerShape(14.dp)
            ) { Text(stringResource(if (rest.finished) R.string.common_continue else R.string.common_skip), fontWeight = FontWeight.Bold) }
        }
    }
}

/** Вибрация + короткий сигнал по окончании отдыха. */
private suspend fun signalRestOver(context: Context) {
    runCatching {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 250, 120, 250), -1))
    }
    val tone = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80) }.getOrNull() ?: return
    try {
        tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 350)
        delay(450)
    } finally {
        tone.release()
    }
}
