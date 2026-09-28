package com.fitcoach.app.data.club

import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * Контент клуба (тренеры, услуги, расписание, акции). Источник — assets/club/club.json,
 * при наличии BrandConfig.clubDataUrl — удалённый JSON той же схемы (см. docs/CLUB_CONTENT.md).
 */
@Serializable
data class ClubContent(
    val version: Int = 1,
    val trainers: List<Trainer> = emptyList(),
    val services: List<ClubService> = emptyList(),
    val schedule: List<ScheduleItem> = emptyList(),
    val promos: List<Promo> = emptyList(),
    val hashtags: List<String> = emptyList(),
    val referralCode: String = "",
    val referralText: String = ""
) {
    /** Акции, срок которых не истёк на дату [today]. */
    fun activePromos(today: LocalDate = LocalDate.now()): List<Promo> =
        promos.distinctBy { it.id }.filter { it.isActive(today) }

    /** Занятия дня недели [day] (1 = понедельник … 7 = воскресенье), отсортированные по времени. */
    fun scheduleFor(day: Int): List<ScheduleItem> =
        schedule.filter { it.day == day }.sortedBy { it.time }

    companion object {
        val EMPTY = ClubContent()
    }
}

@Serializable
data class Trainer(
    val name: String,
    val role: String = "",
    val photoUrl: String? = null,
    val instagram: String? = null,
    /** Только цифры с кодом страны, как BrandConfig.clubWhatsapp. */
    val whatsapp: String? = null,
    val specialties: List<String> = emptyList()
) {
    val whatsappDigits: String get() = whatsapp.orEmpty().filter { it.isDigit() }
    val instagramHandle: String get() = instagram.orEmpty().trimStart('@')
}

@Serializable
data class ClubService(
    val title: String,
    val price: Int = 0,
    val unit: String = "",
    val description: String? = null
) {
    /** «25 000 ₸ / мес» — формат цены для UI; для бесплатной услуги (price <= 0) UI берёт R.string.club_price_free. */
    val priceLabel: String
        get() {
            val digits = "%,d".format(price).replace(',', ' ')
            val base = "$digits ₸"
            return if (unit.isBlank() || price <= 0) base else "$base / $unit"
        }
}

@Serializable
data class ScheduleItem(
    /** 1 = понедельник … 7 = воскресенье. */
    val day: Int,
    val time: String,
    val title: String,
    val trainer: String? = null,
    val durationMin: Int = 60
)

@Serializable
data class Promo(
    val id: String,
    val title: String,
    val text: String = "",
    /** ISO-дата «2026-12-31»; null или пусто = бессрочно. */
    val validUntil: String? = null,
    val ctaText: String? = null,
    val ctaUrl: String? = null
) {
    fun validUntilDate(): LocalDate? = validUntil?.takeIf { it.isNotBlank() }?.let {
        runCatching { LocalDate.parse(it) }.getOrNull()
    }

    fun isActive(today: LocalDate = LocalDate.now()): Boolean {
        val until = validUntilDate() ?: return true
        return !today.isAfter(until)
    }
}
