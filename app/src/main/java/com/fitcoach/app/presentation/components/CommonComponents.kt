package com.fitcoach.app.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitcoach.app.presentation.theme.FitCoachColors
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

@Composable
fun FitCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(FitCoachColors.Card)
            .border(1.dp, FitCoachColors.Border, RoundedCornerShape(16.dp))
            .padding(16.dp),
        content = content
    )
}

@Composable
fun LabeledProgressBar(
    value: Float,
    max: Float,
    color: Color,
    label: String,
    sub: String,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp
) {
    val progress = (value / max.coerceAtLeast(1f)).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(600),
        label = "progress"
    )

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            Text(text = sub, style = MaterialTheme.typography.labelMedium, color = FitCoachColors.TextMuted)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(50))
                .background(FitCoachColors.Border)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .clip(RoundedCornerShape(50))
                    .background(color)
            )
        }
    }
}

@Composable
fun CircularProgress(
    value: Float,
    max: Float,
    color: Color,
    label: String,
    size: Dp = 120.dp,
    strokeWidth: Dp = 10.dp
) {
    val progress = (value / max.coerceAtLeast(1f)).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(800),
        label = "circular_progress"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        Canvas(modifier = Modifier.size(size)) {
            val sw = strokeWidth.toPx()
            val topLeft = Offset(sw / 2, sw / 2)
            val arcSize = Size(this.size.width - sw, this.size.height - sw)

            drawArc(
                color = FitCoachColors.Border,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = sw, cap = StrokeCap.Round)
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = sw, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${value.toInt()}",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = FitCoachColors.TextPrimary
            )
            Text(
                text = label,
                fontSize = 10.sp,
                color = FitCoachColors.TextMuted
            )
        }
    }
}

@Composable
fun PhaseChip(phase: Int, week: Int) {
    val (color, name) = when (phase) {
        1 -> FitCoachColors.PhaseBlue to "Фаза I"
        2 -> FitCoachColors.PhaseOrange to "Фаза II"
        else -> FitCoachColors.PhaseAccent to "Фаза III"
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = "$name · Нед. $week",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

@Composable
fun SetButton(
    setNumber: Int,
    targetWeight: String,
    targetReps: Int,
    isDone: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isDone) FitCoachColors.Success.copy(alpha = 0.15f) else FitCoachColors.Card
    val borderColor = if (isDone) FitCoachColors.Success else FitCoachColors.Border
    val textColor = if (isDone) FitCoachColors.Success else FitCoachColors.TextPrimary

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(enabled = !isDone, onClick = onClick)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (isDone) "✓" else "Сет $setNumber",
                fontWeight = FontWeight.Bold,
                color = textColor,
                fontSize = 14.sp
            )
            Text(
                text = "$targetReps × $targetWeight",
                fontSize = 12.sp,
                color = if (isDone) FitCoachColors.Success.copy(alpha = 0.7f) else FitCoachColors.TextSecondary
            )
        }
    }
}
