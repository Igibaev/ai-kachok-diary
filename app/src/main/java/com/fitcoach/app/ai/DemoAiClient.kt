package com.fitcoach.app.ai

import com.fitcoach.app.domain.model.ChatMessage
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Офлайн-сценарий для демо владельцу клуба: ответы по ключевым словам на русском и казахском.
 * В текстах используются плейсхолдеры {club}, {coach}, {city}.
 */
data class DemoScenario(
    val id: String,
    /** Ключевые слова/основы (RU + KK), регистр не важен. */
    val keywords: List<String>,
    val ru: String,
    val kk: String,
    /** Уместно ли мягко предложить тренера клуба (не чаще 1 раза на 3 ответа). */
    val suggestTrainer: Boolean = false,
    /** Всегда добавлять подсказку про WhatsApp (например, беременность → к врачу и тренеру). */
    val alwaysTrainerHint: Boolean = false,
    /** Текст уже содержит инструкцию про тренера/WhatsApp — считаем рекомендацией, но подсказку не дублируем. */
    val countsAsTrainerHint: Boolean = false
)

@Singleton
class DemoAiClient @Inject constructor(
    private val brand: AiBrand
) : AiClient {

    override val label: String = "Демо (офлайн)"

    private val scenarios: List<DemoScenario> = DemoScenariosHealth.all + DemoScenariosLifestyle.all
    private val fallback: DemoScenario = DemoScenariosLifestyle.fallback

    /** Сколько ответов прошло с последней рекомендации тренера (для правила «раз в 3 ответа»). */
    private var answersSinceTrainerHint = TRAINER_HINT_EVERY

    override suspend fun chat(
        system: String,
        history: List<ChatMessage>,
        userMessage: String,
        locale: String
    ): Result<String> {
        delay(Random.nextLong(MIN_DELAY_MS, MAX_DELAY_MS + 1))
        return Result.success(answer(userMessage, locale))
    }

    /** Синхронный ответ без задержки — удобно для тестов и превью. */
    fun answer(userMessage: String, locale: String): String {
        val kazakh = isKazakh(userMessage, locale)
        val scenario = route(userMessage)
        val base = if (kazakh) scenario.kk else scenario.ru

        val hint = when {
            scenario.alwaysTrainerHint -> {
                answersSinceTrainerHint = 0
                trainerHint(kazakh)
            }
            scenario.countsAsTrainerHint -> {
                answersSinceTrainerHint = 0
                ""
            }
            scenario.suggestTrainer && answersSinceTrainerHint >= TRAINER_HINT_EVERY -> {
                answersSinceTrainerHint = 0
                trainerHint(kazakh)
            }
            else -> {
                answersSinceTrainerHint++
                ""
            }
        }
        return fill(base) + hint
    }

    /** Подбор сценария: максимум совпавших ключевых слов; при равенстве — более ранний в списке. */
    fun route(userMessage: String): DemoScenario {
        // Пунктуация → пробелы, по краям пробелы: ключ «сон » не сработает на «персональный».
        val text = " " + normalize(userMessage).replace(PUNCTUATION, " ") + " "
        var best: DemoScenario? = null
        var bestScore = 0
        for (s in scenarios) {
            val score = s.keywords.count { text.contains(normalize(it)) }
            if (score > bestScore) {
                bestScore = score
                best = s
            }
        }
        return best ?: fallback
    }

    private fun fill(text: String): String = text
        .replace("{club}", brand.clubName)
        .replace("{coach}", brand.coachName)
        .replace("{city}", brand.clubCity.ifBlank { "" })

    private fun trainerHint(kazakh: Boolean): String {
        val viaApp = brand.hasWhatsapp
        return if (kazakh) {
            if (viaApp) "\n\n📲 Қаласаң — жоғарыдағы «Жаттықтырушыдан сұрау» батырмасын бас, {club} жаттықтырушысы WhatsApp-та жауап береді."
            else "\n\n📲 Қаласаң — {club} ресепшенінде жаттықтырушыға жазылуға болады."
        } else {
            if (viaApp) "\n\n📲 Хочешь — нажми «Спросить тренера» вверху, и тренер {club} ответит тебе в WhatsApp."
            else "\n\n📲 Хочешь — на ресепшене {club} можно записаться к тренеру."
        }.let(::fill)
    }

    companion object {
        const val MIN_DELAY_MS = 600L
        const val MAX_DELAY_MS = 1200L
        const val TRAINER_HINT_EVERY = 3

        private val KAZAKH_LETTERS = setOf('ә', 'і', 'ң', 'ғ', 'ү', 'ұ', 'қ', 'ө', 'һ')
        private val KAZAKH_WORDS = listOf(
            "жаттығу", "тамақ", "маған", "керек", "неге", "болады", "ауырады",
            "жеймін", "салмақ", "менің", "емес", "деген"
        )

        /** Казахский: явная локаль kk, специфичные буквы или частые казахские слова. */
        fun isKazakh(text: String, locale: String): Boolean {
            if (locale.startsWith("kk", ignoreCase = true)) return true
            val lower = text.lowercase()
            if (lower.any { it in KAZAKH_LETTERS }) return true
            return KAZAKH_WORDS.any { lower.contains(it) }
        }

        private val PUNCTUATION = Regex("[^\\p{L}\\p{N}\\s+-]")

        fun normalize(s: String): String = s.lowercase().replace('ё', 'е')
    }
}
