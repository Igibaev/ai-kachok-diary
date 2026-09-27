package com.fitcoach.app.brand

import android.net.Uri
import androidx.compose.ui.graphics.Color
import com.fitcoach.app.BuildConfig

/**
 * Единая точка доступа к настройкам бренда (white-label).
 * Значения приходят из brands/<flavor>.properties через BuildConfig (см. app/build.gradle.kts).
 */
object BrandConfig {
    val brandId: String = BuildConfig.BRAND_ID
    val appName: String = BuildConfig.APP_NAME.ifBlank { "FitCoach AI" }
    val brandName: String = BuildConfig.BRAND_NAME.ifBlank { appName }
    val aiCoachName: String = BuildConfig.AI_COACH_NAME.ifBlank { "Коуч" }
    val defaultLanguage: String = BuildConfig.DEFAULT_LANGUAGE.ifBlank { "ru" }

    val clubName: String = BuildConfig.CLUB_NAME.ifBlank { brandName }
    val clubCity: String = BuildConfig.CLUB_CITY
    val clubAddress: String = BuildConfig.CLUB_ADDRESS
    val clubHours: String = BuildConfig.CLUB_HOURS
    val clubPhone: String = BuildConfig.CLUB_PHONE
    val clubWhatsapp: String = BuildConfig.CLUB_WHATSAPP.filter { it.isDigit() }
    val clubInstagram: String = BuildConfig.CLUB_INSTAGRAM.trimStart('@')
    val clubMapUrl: String = BuildConfig.CLUB_MAP_URL
    val clubWebsite: String = BuildConfig.CLUB_WEBSITE
    val clubDataUrl: String = BuildConfig.CLUB_DATA_URL
    val newsUrl: String = BuildConfig.NEWS_URL

    val aiProxyUrl: String = BuildConfig.AI_PROXY_URL.trimEnd('/')
    val aiProxyToken: String = BuildConfig.AI_PROXY_TOKEN
    val aiModel: String = BuildConfig.AI_MODEL.ifBlank { "claude-opus-5" }
    val hasAiProxy: Boolean get() = aiProxyUrl.isNotBlank()

    val accent: Color = parseColor(BuildConfig.ACCENT_COLOR, 0xFFC8FF00)
    val accentOn: Color = parseColor(BuildConfig.ACCENT_ON_COLOR, 0xFF0D0D0D)
    val background: Color = parseColor(BuildConfig.BACKGROUND_COLOR, 0xFF0D0D0D)
    val surface: Color = parseColor(BuildConfig.SURFACE_COLOR, 0xFF141414)
    val card: Color = parseColor(BuildConfig.CARD_COLOR, 0xFF1C1C1C)

    // --- Готовые ссылки для вкладки «Клуб» ---
    val hasPhone: Boolean get() = clubPhone.isNotBlank()
    val hasWhatsapp: Boolean get() = clubWhatsapp.isNotBlank()
    val hasInstagram: Boolean get() = clubInstagram.isNotBlank()
    val hasMap: Boolean get() = clubMapUrl.isNotBlank()

    fun whatsappUrl(text: String = ""): String =
        "https://wa.me/$clubWhatsapp" + if (text.isNotBlank()) "?text=${Uri.encode(text)}" else ""

    val instagramUrl: String get() = "https://instagram.com/$clubInstagram"
    val phoneUri: String get() = "tel:${clubPhone.filter { it.isDigit() || it == '+' }}"

    /** Разбор "#RRGGBB" или "#AARRGGBB"; при ошибке — значение по умолчанию. */
    fun parseColor(hex: String, default: Long): Color {
        val clean = hex.trim().removePrefix("#")
        val argb = when (clean.length) {
            6 -> clean.toLongOrNull(16)?.let { 0xFF000000L or it }
            8 -> clean.toLongOrNull(16)
            else -> null
        } ?: default
        return Color(argb.toULong().toInt())
    }
}
