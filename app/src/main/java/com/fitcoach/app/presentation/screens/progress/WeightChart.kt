package com.fitcoach.app.presentation.screens.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitcoach.app.presentation.theme.FitCoachColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Точка графика: дата (мс) и вес. */
data class WeightPoint(val date: Long, val weightKg: Float)

/**
 * График веса на Canvas: линия с заливкой, точки, подписи min/max и крайних дат, пунктир целевого веса.
 * Ожидает точки по возрастанию даты.
 */
@Composable
fun WeightChart(points: List<WeightPoint>, targetKg: Float?, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = FitCoachColors.TextMuted)
    val valueStyle = TextStyle(fontSize = 11.sp, color = FitCoachColors.TextPrimary, fontWeight = FontWeight.Bold)
    val dateFmt = DateTimeFormatter.ofPattern("dd.MM")
    val zone = ZoneId.systemDefault()

    Canvas(modifier = modifier.fillMaxWidth().height(180.dp)) {
        if (points.isEmpty()) return@Canvas
        val padL = 8.dp.toPx(); val padR = 8.dp.toPx(); val padT = 22.dp.toPx(); val padB = 22.dp.toPx()
        val w = size.width - padL - padR
        val h = size.height - padT - padB

        val minW = points.minOf { it.weightKg }
        val maxW = points.maxOf { it.weightKg }
        val lo = minOf(minW, targetKg ?: minW) - 0.5f
        val hi = maxOf(maxW, targetKg ?: maxW) + 0.5f
        val range = (hi - lo).coerceAtLeast(1f)
        val minT = points.first().date
        val maxT = points.last().date
        val span = (maxT - minT).coerceAtLeast(1L).toFloat()

        fun x(t: Long) = if (points.size == 1) padL + w / 2 else padL + (t - minT) / span * w
        fun y(v: Float) = padT + (1f - (v - lo) / range) * h

        // Сетка
        val grid = FitCoachColors.Border
        for (i in 0..3) {
            val gy = padT + h * i / 3f
            drawLine(grid, Offset(padL, gy), Offset(padL + w, gy), strokeWidth = 1f)
        }

        // Целевой вес — пунктир
        targetKg?.let { t ->
            val ty = y(t)
            drawLine(
                FitCoachColors.Success.copy(alpha = 0.7f), Offset(padL, ty), Offset(padL + w, ty), strokeWidth = 2f,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
            )
            drawText(textMeasurer, "цель $t", Offset(padL + w - 60.dp.toPx(), ty - 14.dp.toPx()), TextStyle(fontSize = 10.sp, color = FitCoachColors.Success))
        }

        // Линия и заливка
        val line = Path()
        val fill = Path()
        points.forEachIndexed { i, p ->
            val px = x(p.date); val py = y(p.weightKg)
            if (i == 0) { line.moveTo(px, py); fill.moveTo(px, padT + h); fill.lineTo(px, py) } else { line.lineTo(px, py); fill.lineTo(px, py) }
        }
        fill.lineTo(x(points.last().date), padT + h); fill.close()
        drawPath(fill, FitCoachColors.Accent.copy(alpha = 0.12f))
        drawPath(line, FitCoachColors.Accent, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))

        // Точки
        points.forEach { p ->
            val c = Offset(x(p.date), y(p.weightKg))
            drawCircle(FitCoachColors.Background, radius = 5.dp.toPx(), center = c)
            drawCircle(FitCoachColors.Accent, radius = 3.5.dp.toPx(), center = c)
        }

        // Подписи min / max
        val labelMaxX = maxOf(padL, padL + w - 30.dp.toPx()) // защита от узкого Canvas (coerceIn требует min <= max)
        val maxP = points.maxBy { it.weightKg }
        val minP = points.minBy { it.weightKg }
        drawText(textMeasurer, "%.1f".format(maxP.weightKg), Offset((x(maxP.date) - 12.dp.toPx()).coerceIn(padL, labelMaxX), y(maxP.weightKg) - 20.dp.toPx()), valueStyle)
        if (minP !== maxP) {
            drawText(textMeasurer, "%.1f".format(minP.weightKg), Offset((x(minP.date) - 12.dp.toPx()).coerceIn(padL, labelMaxX), y(minP.weightKg) + 6.dp.toPx()), valueStyle)
        }

        // Даты по краям
        fun fmt(t: Long) = Instant.ofEpochMilli(t).atZone(zone).toLocalDate().format(dateFmt)
        drawText(textMeasurer, fmt(minT), Offset(padL, size.height - 14.dp.toPx()), labelStyle)
        if (points.size > 1) drawText(textMeasurer, fmt(maxT), Offset(padL + w - 30.dp.toPx(), size.height - 14.dp.toPx()), labelStyle)
    }
}

