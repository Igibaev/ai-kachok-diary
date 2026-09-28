package com.fitcoach.app.ai

import com.fitcoach.app.domain.model.ChatMessage

/**
 * Единый интерфейс AI-тренера. Реализации: [ProxyAiClient] (прокси клуба),
 * [DirectAnthropicClient] (ключ разработчика, только DEBUG), [DemoAiClient] (офлайн).
 */
interface AiClient {
    /**
     * @param system   системный промпт (см. BuildSystemPromptUseCase)
     * @param history  история диалога из БД БЕЗ текущего сообщения пользователя;
     *                 сообщения с isError = true игнорируются реализациями
     * @param userMessage текст нового сообщения пользователя
     * @param locale   "ru" | "kk" — язык интерфейса пользователя
     * @return текст ответа или ошибка с коротким сообщением на языке пользователя
     */
    suspend fun chat(
        system: String,
        history: List<ChatMessage>,
        userMessage: String,
        locale: String
    ): Result<String>

}

/** Режим работы AI; подписи — только в ресурсах (R.string.ai_mode_*), чтобы не было второго источника правды. */
enum class AiMode { DEMO, CLUB, DEVELOPER }

/**
 * Короткие тексты ошибок на языке пользователя. Никаких технических деталей —
 * они пользователю не нужны, а в чат попадают как системный пузырь.
 */
object AiErrors {
    fun network(locale: String) = pick(
        locale,
        "Нет соединения с интернетом. Проверь сеть и попробуй ещё раз.",
        "Интернет байланысы жоқ. Желіні тексеріп, қайта көр."
    )

    fun unauthorized(locale: String) = pick(
        locale,
        "AI-сервис клуба не принял запрос приложения. Сообщи администратору клуба.",
        "Клубтың AI-қызметі қолданба сұрауын қабылдамады. Клуб әкімшісіне хабарлас."
    )

    fun rateLimited(locale: String) = pick(
        locale,
        "Лимит вопросов на сегодня исчерпан. Возвращайся завтра или напиши тренеру клуба.",
        "Бүгінгі сұрақ шегі бітті. Ертең қайта кел немесе клуб жаттықтырушысына жаз."
    )

    fun serverUnavailable(locale: String) = pick(
        locale,
        "AI-сервис временно недоступен. Попробуй через минуту.",
        "AI-қызмет уақытша қолжетімсіз. Бір минуттан кейін қайта көр."
    )

    fun emptyAnswer(locale: String) = pick(
        locale,
        "Не удалось получить ответ. Попробуй переформулировать вопрос.",
        "Жауап алу мүмкін болмады. Сұрақты басқаша қойып көр."
    )

    fun noApiKey(locale: String) = pick(
        locale,
        "Ключ разработчика не задан. Открой Настройки → AI-тренер.",
        "Әзірлеуші кілті енгізілмеген. Баптаулар → AI-жаттықтырушы бөлімін аш."
    )

    fun badRequest(locale: String) = pick(
        locale,
        "Запрос отклонён AI-сервисом. Попробуй задать вопрос короче.",
        "Сұрауды AI-қызмет қабылдамады. Сұрақты қысқарақ қойып көр."
    )

    private fun pick(locale: String, ru: String, kk: String): String =
        if (locale.startsWith("kk", ignoreCase = true)) kk else ru
}
