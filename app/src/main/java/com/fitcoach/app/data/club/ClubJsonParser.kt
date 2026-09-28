package com.fitcoach.app.data.club

import kotlinx.serialization.json.Json

/** Разбор club.json: неизвестные поля игнорируются, чтобы владелец клуба мог расширять таблицу. */
object ClubJsonParser {
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun parse(text: String): Result<ClubContent> = runCatching {
        json.decodeFromString(ClubContent.serializer(), text)
    }

    fun encode(content: ClubContent): String = json.encodeToString(ClubContent.serializer(), content)
}

/** Поиск новых акций: id, которых не было в уже показанном наборе. */
object PromoDiff {
    /**
     * @param seenIds ранее известные id акций; null = первый запуск (ничего не считаем новым,
     * чтобы не спамить уведомлениями сразу после установки).
     */
    fun newPromos(content: ClubContent, seenIds: Set<String>?): List<Promo> {
        if (seenIds == null) return emptyList()
        return content.activePromos().filter { it.id !in seenIds }
    }
}

/** Содержимое QR «Карты участника»: `<brandId>:<memberId>` — читается сканером на ресепшене. */
object QrPayload {
    fun build(brandId: String, memberId: String): String = "${brandId.trim()}:${memberId.trim()}"

    /** Обратный разбор (для сканера/тестов). */
    fun parse(payload: String): Pair<String, String>? {
        val idx = payload.indexOf(':')
        if (idx <= 0 || idx == payload.lastIndex) return null
        return payload.substring(0, idx) to payload.substring(idx + 1)
    }
}
