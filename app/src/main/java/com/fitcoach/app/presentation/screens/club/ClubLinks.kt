package com.fitcoach.app.presentation.screens.club

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.fitcoach.app.R
import com.fitcoach.app.brand.BrandConfig

/** Открытие внешних ссылок клуба (WhatsApp, Instagram, карта, звонок) без падений, если приложения нет. */
object ClubLinks {
    /** Разрешённые схемы: ctaUrl приходит из удалённого club.json, intent:/file:/content: туда не пускаем. */
    private val ALLOWED_SCHEMES = setOf("http", "https", "tel", "whatsapp", "mailto")

    fun open(context: Context, url: String) {
        if (url.isBlank()) return
        val scheme = runCatching { Uri.parse(url).scheme?.lowercase() }.getOrNull() ?: return
        if (scheme !in ALLOWED_SCHEMES) return
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun dial(context: Context) {
        if (!BrandConfig.hasPhone) return
        runCatching {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse(BrandConfig.phoneUri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun whatsapp(context: Context, text: String, digits: String = BrandConfig.clubWhatsapp) {
        val number = digits.filter { it.isDigit() }.ifBlank { BrandConfig.clubWhatsapp }
        if (number.isBlank()) return
        open(context, "https://wa.me/$number?text=${Uri.encode(text)}")
    }

    fun instagram(context: Context, handle: String = BrandConfig.clubInstagram) {
        val h = handle.trimStart('@')
        if (h.isBlank()) return
        open(context, "https://instagram.com/$h")
    }

    fun map(context: Context) = open(context, BrandConfig.clubMapUrl)

    /** Лид с меткой «из приложения»: тренер сразу видит источник заявки. Текст — на языке приложения. */
    fun bookTrainerText(context: Context, trainerName: String?): String =
        if (trainerName.isNullOrBlank()) context.getString(R.string.club_book_trainer_text, BrandConfig.appName)
        else context.getString(R.string.club_book_trainer_named_text, BrandConfig.appName, trainerName)

    fun bookServiceText(context: Context, service: String): String =
        context.getString(R.string.club_book_service_text, BrandConfig.appName, service)

    fun promoText(context: Context, promoTitle: String): String =
        context.getString(R.string.club_promo_text, BrandConfig.appName, promoTitle)

    fun greetingText(context: Context): String = context.getString(R.string.club_whatsapp_greeting, BrandConfig.appName)
}
