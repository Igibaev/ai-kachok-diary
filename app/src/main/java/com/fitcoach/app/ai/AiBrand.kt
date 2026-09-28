package com.fitcoach.app.ai

import com.fitcoach.app.brand.BrandConfig

/**
 * Брендовые данные, нужные AI-слою (имя коуча, клуб). Вынесены в отдельный класс,
 * чтобы промпт и демо-клиент можно было тестировать без BuildConfig.
 */
data class AiBrand(
    val coachName: String,
    val clubName: String,
    val clubCity: String,
    val appName: String,
    val hasWhatsapp: Boolean,
    val model: String
) {
    companion object {
        fun fromConfig(): AiBrand = AiBrand(
            coachName = BrandConfig.aiCoachName,
            clubName = BrandConfig.clubName,
            clubCity = BrandConfig.clubCity,
            appName = BrandConfig.appName,
            hasWhatsapp = BrandConfig.hasWhatsapp,
            model = BrandConfig.aiModel
        )
    }
}
