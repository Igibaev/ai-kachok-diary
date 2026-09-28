package com.fitcoach.app.data.club

import kotlinx.coroutines.flow.Flow

interface ClubRepository {
    /** assets → локальный кэш → (если настроен clubDataUrl) свежая версия с сервера. */
    fun observe(): Flow<ClubContent>

    /** Текущее содержимое (загружает assets/кэш при первом обращении). */
    suspend fun current(): ClubContent

    /**
     * Обновляет контент с сервера (если clubDataUrl задан) и возвращает акции,
     * которых пользователь ещё не видел. Первый вызов помечает все текущие акции как известные.
     */
    suspend fun refresh(): List<Promo>
}
