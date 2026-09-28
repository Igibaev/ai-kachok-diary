package com.fitcoach.app.l10n

import com.fitcoach.app.domain.program.ProgramCatalog
import org.junit.Assert.assertTrue
import org.junit.Test

/** Каждая доменная строка каталога программ (названия, подсказки техники, группы мышц, нагрузка, замены) должна иметь перевод на казахский. */
class DomainTranslationsCoverageTest {

    @Test
    fun `every program catalog string has a Kazakh translation`() {
        val missing = sortedSetOf<String>()
        fun check(text: String) {
            if (text.isNotBlank() && !KK_MAP.containsKey(text)) missing += text
        }
        ProgramCatalog.programs.forEach { program ->
            check(program.title)
            program.phases.forEach { phase ->
                phase.templates.forEach { t ->
                    check(t.title)
                    t.exercises.forEach { ex ->
                        check(ex.name); check(ex.tip); check(ex.muscleGroup)
                        ex.alternatives.forEach { check(it) }
                        ex.sets.forEach { check(it.weight) }
                    }
                }
            }
        }
        assertTrue("Нет перевода на казахский: $missing", missing.isEmpty())
    }

    @Test
    fun `restriction replacement tip is translated`() {
        val tip = DomainTranslations.RESTRICTION_PREFIX + "Жим гантелей сидя"
        val kk = DomainTranslations.translate(tip, "kk")
        assertTrue(kk, kk.startsWith("Шектеу себебінен ауыстыру: ") && !kk.contains("Жим гантелей сидя"))
    }
}
