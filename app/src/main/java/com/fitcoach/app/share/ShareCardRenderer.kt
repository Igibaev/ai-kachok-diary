package com.fitcoach.app.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import com.fitcoach.app.R
import com.fitcoach.app.brand.BrandConfig
import com.fitcoach.app.domain.service.ProgressShareData
import com.fitcoach.app.domain.service.WorkoutShareData
import com.fitcoach.app.presentation.theme.FitCoachColors
import kotlin.math.roundToInt

/** Рисует карточку 1080×1920 для Instagram Stories средствами android.graphics.Canvas. */
class ShareCardRenderer(private val context: Context) {

    data class Branding(val hashtags: List<String>, val referralCode: String)

    private val w = 1080
    private val h = 1920
    private val bg = FitCoachColors.Background.toArgb()
    private val card = FitCoachColors.Card.toArgb()
    private val accent = FitCoachColors.Accent.toArgb()
    private val accentOn = FitCoachColors.AccentOn.toArgb()
    private val textPrimary = FitCoachColors.TextPrimary.toArgb()
    private val textSecondary = FitCoachColors.TextSecondary.toArgb()
    private val textMuted = FitCoachColors.TextMuted.toArgb()

    private fun paint(color: Int, size: Float, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        textAlign = align
        typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
    }

    fun renderWorkout(data: WorkoutShareData, branding: Branding): Bitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        drawBackground(c)
        drawHeader(c, data.userName.ifBlank { str(R.string.share_workout_done) }, str(R.string.share_workout_label))

        // Заголовок тренировки
        val title = data.workoutTitle.ifBlank { str(R.string.share_workout_default_title) }
        drawWrapped(c, title, 80f, 640f, 1000f, paint(textPrimary, 84f, bold = true), 96f, maxLines = 2)

        // Сетка цифр 2×2
        val stats = listOf(
            "${data.doneSets}" to str(R.string.share_sets),
            "${data.durationMinutes}" to str(R.string.share_minutes),
            formatVolume(data.volumeKg) to str(R.string.share_volume),
            "${data.streakWorkouts}" to str(R.string.share_streak)
        )
        drawStatsGrid(c, stats, top = 880f)

        var y = 1420f
        if (data.personalRecords > 0) {
            drawPill(c, str(R.string.share_records, data.personalRecords), y, accent, accentOn)
            y += 130f
        }
        drawFooter(c, branding, y)
        return bitmap
    }

    fun renderProgress(data: ProgressShareData, branding: Branding): Bitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        drawBackground(c)
        drawHeader(c, data.userName.ifBlank { str(R.string.share_progress_title) }, str(R.string.share_progress_label))

        val diff = data.currentWeightKg - data.startWeightKg
        val diffText = (if (diff <= 0) "−" else "+") + str(R.string.format_kg, "%.1f".format(kotlin.math.abs(diff)))
        drawWrapped(c, str(R.string.share_week_in_program, data.weeksInProgram.coerceAtLeast(1)), 80f, 640f, 1000f, paint(textPrimary, 76f, bold = true), 90f, maxLines = 2)

        // Крупная разница веса
        c.drawText(diffText, 80f, 900f, paint(accent, 190f, bold = true))
        c.drawText(str(R.string.share_weight_change), 80f, 970f, paint(textSecondary, 40f))

        val stats = listOf(
            "%.1f".format(data.startWeightKg) to str(R.string.share_kg_start),
            "%.1f".format(data.currentWeightKg) to str(R.string.share_kg_now),
            "${data.workoutsCompleted}" to str(R.string.share_workouts),
            "${data.weeksInProgram}" to str(R.string.share_weeks)
        )
        drawStatsGrid(c, stats, top = 1040f)
        drawFooter(c, branding, 1560f)
        return bitmap
    }

    // ---------- части карточки ----------

    private fun drawBackground(c: Canvas) {
        c.drawColor(bg)
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), 700f, intArrayOf(withAlpha(accent, 0x55), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w.toFloat(), 900f, glow)
        // Диагональная акцентная полоса
        val stripe = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(accent, 0x22) }
        c.save(); c.rotate(-12f, w / 2f, h / 2f)
        c.drawRect(-200f, 1250f, w + 200f, 1330f, stripe)
        c.restore()
    }

    private fun drawHeader(c: Canvas, name: String, kicker: String) {
        // Логотип в круге
        val logoSize = 140
        val circle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(accent, 0x33) }
        c.drawCircle(80f + logoSize / 2f, 140f + logoSize / 2f, logoSize / 2f + 16f, circle)
        ContextCompat.getDrawable(context, R.drawable.brand_logo)?.let { d ->
            d.setBounds(80, 140, 80 + logoSize, 140 + logoSize)
            d.draw(c)
        }
        c.drawText(BrandConfig.clubName, 260f, 200f, paint(textPrimary, 52f, bold = true))
        val sub = listOf(BrandConfig.clubCity, "@${BrandConfig.clubInstagram}".takeIf { BrandConfig.hasInstagram }.orEmpty())
            .filter { it.isNotBlank() }.joinToString(" · ")
        c.drawText(sub, 260f, 258f, paint(textSecondary, 36f))

        c.drawText(kicker, 80f, 470f, paint(accent, 38f, bold = true).apply { letterSpacing = 0.18f })
        c.drawText(name, 80f, 550f, paint(textSecondary, 44f))
    }

    private fun drawStatsGrid(c: Canvas, stats: List<Pair<String, String>>, top: Float) {
        val gap = 24f
        val cellW = (w - 160f - gap) / 2f
        val cellH = 230f
        stats.forEachIndexed { i, (value, label) ->
            val col = i % 2
            val row = i / 2
            val left = 80f + col * (cellW + gap)
            val t = top + row * (cellH + gap)
            val rect = RectF(left, t, left + cellW, t + cellH)
            c.drawRoundRect(rect, 36f, 36f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = card })
            c.drawRoundRect(rect, 36f, 36f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(accent, 0x40); style = Paint.Style.STROKE; strokeWidth = 3f })
            c.drawText(value, left + 40f, t + 130f, paint(accent, 96f, bold = true))
            c.drawText(label, left + 40f, t + 190f, paint(textSecondary, 34f))
        }
    }

    private fun drawPill(c: Canvas, text: String, top: Float, fill: Int, textColor: Int) {
        val p = paint(textColor, 44f, bold = true)
        val tw = p.measureText(text)
        val rect = RectF(80f, top, 80f + tw + 80f, top + 96f)
        c.drawRoundRect(rect, 48f, 48f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill })
        c.drawText(text, 120f, top + 62f, p)
    }

    private fun drawFooter(c: Canvas, branding: Branding, top: Float) {
        var y = top
        if (branding.referralCode.isNotBlank()) {
            val rect = RectF(80f, y, w - 80f, y + 150f)
            c.drawRoundRect(rect, 32f, 32f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(accent, 0x2A) })
            c.drawRoundRect(rect, 32f, 32f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent; style = Paint.Style.STROKE; strokeWidth = 3f })
            c.drawText(str(R.string.share_referral), 120f, y + 60f, paint(textSecondary, 34f))
            c.drawText(branding.referralCode, 120f, y + 122f, paint(accent, 60f, bold = true).apply { letterSpacing = 0.12f })
            y += 190f
        }
        val tags = branding.hashtags.joinToString("  ").ifBlank { "#${BrandConfig.brandId}" }
        drawWrapped(c, tags, 80f, y + 20f, w - 160f, paint(textMuted, 34f), 46f, maxLines = 2)

        val bottom = h - 90f
        c.drawText(BrandConfig.appName, 80f, bottom, paint(textSecondary, 34f, bold = true))
        if (BrandConfig.hasInstagram) {
            c.drawText("@${BrandConfig.clubInstagram}", w - 80f, bottom, paint(accent, 34f, bold = true, align = Paint.Align.RIGHT))
        }
    }

    /** Простой перенос по словам. */
    private fun drawWrapped(c: Canvas, text: String, x: Float, y: Float, maxWidth: Float, p: Paint, lineHeight: Float, maxLines: Int) {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (p.measureText(candidate) <= maxWidth) current = StringBuilder(candidate)
            else {
                if (current.isNotEmpty()) lines += current.toString()
                current = StringBuilder(word)
            }
            if (lines.size == maxLines) break
        }
        if (lines.size < maxLines && current.isNotEmpty()) lines += current.toString()
        lines.take(maxLines).forEachIndexed { i, line ->
            var l = line
            if (i == maxLines - 1 && (lines.size > maxLines || words.joinToString(" ") != lines.joinToString(" "))) {
                while (l.isNotEmpty() && p.measureText("$l…") > maxWidth) l = l.dropLast(1)
                l = "$l…"
            }
            c.drawText(l, x, y + i * lineHeight, p)
        }
    }

    private fun formatVolume(kg: Int): String =
        if (kg >= 10_000) str(R.string.share_tons, ((kg / 1000f * 10).roundToInt() / 10f).toString().removeSuffix(".0")) else kg.toString()

    /** Строки карточки — на языке приложения (контекст с локалью задаёт ShareServiceImpl). */
    private fun str(resId: Int, vararg args: Any): String = context.getString(resId, *args)

    private fun withAlpha(color: Int, alpha: Int) = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
}
