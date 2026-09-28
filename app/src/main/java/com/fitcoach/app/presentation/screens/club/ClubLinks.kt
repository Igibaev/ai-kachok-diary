package com.fitcoach.app.presentation.screens.club

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.fitcoach.app.brand.BrandConfig

/** Открытие внешних ссылок клуба (WhatsApp, Instagram, карта, звонок) без падений, если приложения нет. */
object ClubLinks {
    fun open(context: Context, url: String) {
        if (url.isBlank()) return
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

    /** Лид с меткой «из приложения»: тренер сразу видит источник заявки. */
    fun bookTrainerText(trainerName: String?): String {
        val to = if (trainerName.isNullOrBlank()) "тренеру" else "тренеру $trainerName"
        return "Здравствуйте! Пишу из приложения ${BrandConfig.appName}. Хочу записаться на тренировку к $to. Когда есть свободное время?"
    }

    fun bookServiceText(service: String): String =
        "Здравствуйте! Пишу из приложения ${BrandConfig.appName}. Интересует «$service». Расскажите, пожалуйста, подробнее."

    fun promoText(promoTitle: String): String =
        "Здравствуйте! Пишу из приложения ${BrandConfig.appName}. Хочу воспользоваться акцией «$promoTitle»."
}
