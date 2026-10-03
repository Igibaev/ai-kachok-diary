package com.fitcoach.app.ai.chef

import com.fitcoach.app.domain.model.FoodAnalysis
import com.fitcoach.app.domain.model.MealPlan
import com.fitcoach.app.domain.model.MealPlanRequest

/**
 * AI-повар: план питания по целям профиля и разбор фото еды.
 * Реализации: [ProxyAiChefClient] (прокси клуба, ключ Anthropic не в приложении) и [DemoAiChefClient] (офлайн).
 * Прямого режима с ключом разработчика у повара нет — только прокси или демо (см. [AiChefSelector]).
 */
interface AiChefClient {
    /** @param locale "ru" | "kk" — язык интерфейса; контент плана приходит на этом языке. */
    suspend fun generateMealPlan(req: MealPlanRequest, locale: String): Result<MealPlan>

    /**
     * @param jpeg      байты изображения после уменьшения (≤ 1,5 МБ)
     * @param mediaType "image/jpeg" | "image/png" | "image/webp"
     * @param hint      подсказка пользователя («бешбармак, порция средняя»), может быть null
     */
    suspend fun analyzeFoodPhoto(jpeg: ByteArray, mediaType: String, hint: String?, locale: String): Result<FoodAnalysis>
}

/** Ошибки повара с готовым текстом для пользователя (дополняют [com.fitcoach.app.ai.AiErrors]). */
object ChefErrors {
    fun planLimit(locale: String) = pick(
        locale,
        "Лимит планов питания на сегодня исчерпан. Попробуй завтра — текущий план остаётся доступным.",
        "Бүгінгі тамақтану жоспарларының шегі бітті. Ертең қайта көр — қазіргі жоспар сақталады."
    )

    fun photoLimit(locale: String) = pick(
        locale,
        "Лимит фото на сегодня исчерпан. Добавь блюдо вручную или попробуй завтра.",
        "Бүгінгі фото шегі бітті. Тағамды қолмен қос немесе ертең қайта көр."
    )

    fun refused(locale: String) = pick(
        locale,
        "AI-повар не смог обработать этот запрос. Попробуй изменить подсказку или фото.",
        "AI-аспаз бұл сұрауды өңдей алмады. Кеңесті немесе фотоны өзгертіп көр."
    )

    fun imageTooLarge(locale: String) = pick(
        locale,
        "Фото слишком большое. Попробуй другое изображение.",
        "Фото тым үлкен. Басқа суретті таңдап көр."
    )

    fun imageFailed(locale: String) = pick(
        locale,
        "Не удалось обработать изображение. Попробуй другое фото.",
        "Суретті өңдеу мүмкін болмады. Басқа фотоны таңдап көр."
    )

    fun emptyPlan(locale: String) = pick(
        locale,
        "Повар вернул пустой план. Попробуй ещё раз или измени предпочтения.",
        "Аспаз бос жоспар қайтарды. Қайта көр немесе қалауларды өзгерт."
    )

    private fun pick(locale: String, ru: String, kk: String): String =
        if (locale.startsWith("kk", ignoreCase = true)) kk else ru
}
