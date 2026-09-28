package com.fitcoach.app.l10n

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale

/**
 * Перевод доменных строк (названия программ, упражнений, фаз, бейджей, титулы enum'ов),
 * которые живут в Kotlin на русском как канонический ключ. Русский текст — ключ, казахский — значение.
 *
 * Использование: `program.title.tr()` в UI, `title.tr(lang)` в воркерах/сервисах, где язык известен из профиля.
 */
object DomainTranslations {

    const val RU = "ru"
    const val KK = "kk"

    /** RU → KK. */
    val kk: Map<String, String> get() = KK_MAP

    /** Язык приложения: выбранный через AppCompatDelegate, иначе системный. */
    fun currentLanguage(): String {
        val locales = runCatching { AppCompatDelegate.getApplicationLocales() }.getOrNull()
        val lang = if (locales != null && !locales.isEmpty) locales[0]?.language else null
        return (lang ?: Locale.getDefault().language).lowercase(Locale.ROOT)
    }

    /** Локаль приложения для форматирования дат. */
    fun currentLocale(): Locale {
        val locales = runCatching { AppCompatDelegate.getApplicationLocales() }.getOrNull()
        return if (locales != null && !locales.isEmpty) locales[0] ?: Locale.getDefault() else Locale.getDefault()
    }

    fun isKazakh(language: String = currentLanguage()): Boolean = language.startsWith(KK)

    fun translate(text: String, language: String = currentLanguage()): String {
        if (!isKazakh(language) || text.isBlank()) return text
        KK_MAP[text]?.let { return it }
        return translateComposite(text)
    }

    /**
     * Составные строки: «Фаза I — База», «Верх тела · сила», «Фаза 2» и т.п.
     * Переводим по частям, неизвестные части оставляем как есть.
     */
    private fun translateComposite(text: String): String {
        PHASE_ROMAN.matchEntire(text)?.let { m ->
            val (num, name) = m.destructured
            return "$num кезең — ${KK_MAP[name] ?: name}"
        }
        PHASE_NUM.matchEntire(text)?.let { m -> return "${m.groupValues[1]}-кезең" }
        // Подсказка замены из ProgramCatalog.applyRestrictions: «Замена из-за ограничения: <упражнение>».
        if (text.startsWith(RESTRICTION_PREFIX)) {
            val name = text.removePrefix(RESTRICTION_PREFIX)
            return "Шектеу себебінен ауыстыру: ${KK_MAP[name] ?: name}"
        }
        if (text.contains(" · ")) {
            return text.split(" · ").joinToString(" · ") { part ->
                KK_MAP[part] ?: KK_MAP[part.replaceFirstChar { c -> c.uppercase() }]?.replaceFirstChar { c -> c.lowercase() } ?: part
            }
        }
        if (text.contains(" — ")) {
            return text.split(" — ").joinToString(" — ") { part -> KK_MAP[part] ?: part }
        }
        return text
    }

    const val RESTRICTION_PREFIX = "Замена из-за ограничения: "
    private val PHASE_ROMAN = Regex("^Фаза (I{1,3}|IV|V) — (.+)$")
    private val PHASE_NUM = Regex("^Фаза (\\d+)$")
}

/** Перевод доменной строки на текущий язык приложения (RU остаётся как есть). */
fun String.tr(): String = DomainTranslations.translate(this)

/** Перевод доменной строки на заданный язык (`"ru"` / `"kk"`), например из профиля пользователя в воркере. */
fun String.tr(language: String): String = DomainTranslations.translate(this, language)

/** Контекст с ресурсами на заданном языке — для уведомлений из воркеров, где Activity нет. */
fun Context.withLanguage(language: String): Context {
    val lang = language.ifBlank { DomainTranslations.currentLanguage() }
    val config = Configuration(resources.configuration)
    config.setLocale(Locale.forLanguageTag(lang))
    return createConfigurationContext(config)
}
