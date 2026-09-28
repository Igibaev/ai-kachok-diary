package com.fitcoach.app.ai

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoAiClientTest {

    private val brand = AiBrand(
        coachName = "Арман",
        clubName = "Fit Almaty",
        clubCity = "Алматы",
        appName = "Fit Almaty App",
        hasWhatsapp = true,
        model = "claude-opus-5"
    )

    private fun client() = DemoAiClient(brand)

    private val kazakhLetters = setOf('ә', 'і', 'ң', 'ғ', 'ү', 'ұ', 'қ', 'ө', 'һ')
    private fun hasKazakhLetters(s: String) = s.lowercase().any { it in kazakhLetters }

    // ---------- каталог сценариев ----------

    @Test
    fun `catalog has at least 25 scenarios with unique ids and real Kazakh text`() {
        val all = DemoScenariosHealth.all + DemoScenariosLifestyle.all
        assertTrue("expected >= 25 scenarios, got ${all.size}", all.size >= 25)
        assertEquals("ids must be unique", all.size, all.map { it.id }.toSet().size)
        all.forEach { s ->
            assertTrue("${s.id}: keywords empty", s.keywords.isNotEmpty())
            assertTrue("${s.id}: ru blank", s.ru.isNotBlank())
            assertTrue("${s.id}: kk blank", s.kk.isNotBlank())
            assertTrue("${s.id}: kk text has no Kazakh letters (transliteration?)", hasKazakhLetters(s.kk))
            assertFalse("${s.id}: ru text must not be Kazakh", hasKazakhLetters(s.ru))
        }
        assertTrue(hasKazakhLetters(DemoScenariosLifestyle.fallback.kk))
    }

    // ---------- маршрутизация RU ----------

    @Test
    fun `russian keywords route to the expected scenario`() {
        val c = client()
        val cases = mapOf(
            "Что поесть после тренировки?" to "post_workout_food",
            "Можно ли есть бешбармак при похудении?" to "kazakh_cuisine",
            "Что поесть сейчас?" to "eat_now",
            "Болит спина, что делать?" to "back_pain",
            "Болит колено после приседаний" to "knee_pain",
            "Тренажёр занят, чем заменить?" to "machine_busy",
            "Мотивируй меня" to "motivation",
            "Сколько пить воды?" to "water",
            "Как улучшить сон?" to "sleep",
            "Кардио или силовые?" to "cardio_vs_strength",
            "Сколько белка нужно?" to "protein",
            "Вес не уходит, застой" to "plateau",
            "Хочу набрать массу" to "muscle_gain",
            "Разминка перед тренировкой" to "warmup",
            "Техника приседа" to "squat_technique",
            "Новичок, с чего начать?" to "beginner",
            "Я беременна, можно ли тренироваться?" to "pregnancy",
            "У меня высокое давление" to "pressure",
            "Запиши меня к тренеру" to "book_trainer",
            "Привет!" to "greeting",
            "Спасибо" to "thanks",
            "Как сбросить лишний вес?" to "weight_loss",
            "Сколько раз в неделю тренироваться?" to "schedule",
            "Хочу пресс кубиками" to "abs",
            "Держу пост, как тренироваться?" to "fasting"
        )
        cases.forEach { (question, expected) ->
            assertEquals("routing for «$question»", expected, c.route(question).id)
        }
    }

    // ---------- маршрутизация KK ----------

    @Test
    fun `kazakh keywords route to the expected scenario`() {
        val c = client()
        val cases = mapOf(
            "Жаттығудан кейін не жеймін?" to "post_workout_food",
            "Арқам ауырады" to "back_pain",
            "Тізем ауырады" to "knee_pain",
            "Мені жаттықтырушыға жазып қой" to "book_trainer",
            "Сәлем!" to "greeting",
            "Рахмет" to "thanks",
            "Мен жүктімін, жаттығуға бола ма?" to "pregnancy",
            "Қан қысымым жоғары" to "pressure",
            "Тренажёр бос емес" to "machine_busy",
            "Қанша су ішу керек?" to "water",
            "Бесбармақ жесем бола ма?" to "kazakh_cuisine",
            "Шаршадым, жалқаумын" to "motivation",
            "Салмақ түспейді" to "plateau"
        )
        cases.forEach { (question, expected) ->
            assertEquals("routing for «$question»", expected, c.route(question).id)
        }
    }

    @Test
    fun `unknown question falls back`() {
        assertEquals("fallback", client().route("asdfgh qwerty 12345").id)
    }

    // ---------- язык ответа ----------

    @Test
    fun `answer is in Kazakh when message is Kazakh even with ru locale`() {
        val answer = client().answer("Арқам ауырады", "ru")
        assertTrue(hasKazakhLetters(answer))
    }

    @Test
    fun `answer is in Kazakh when locale is kk even for a Russian message`() {
        val answer = client().answer("Привет", "kk")
        assertTrue(hasKazakhLetters(answer))
    }

    @Test
    fun `answer is in Russian for Russian message and ru locale`() {
        val answer = client().answer("Что поесть после тренировки?", "ru")
        assertFalse(hasKazakhLetters(answer))
    }

    @Test
    fun `language detection helpers`() {
        assertTrue(DemoAiClient.isKazakh("Тамақ керек", "ru"))
        assertTrue(DemoAiClient.isKazakh("hello", "kk-KZ"))
        assertFalse(DemoAiClient.isKazakh("Что поесть?", "ru"))
    }

    // ---------- бренд и подсказка тренера ----------

    @Test
    fun `placeholders are replaced with brand values`() {
        val c = client()
        val all = DemoScenariosHealth.all + DemoScenariosLifestyle.all + DemoScenariosLifestyle.fallback
        all.forEach { s ->
            val ru = c.answer(s.keywords.firstOrNull() ?: "asdfgh", "ru")
            assertFalse("${s.id}: placeholder left in «$ru»", ru.contains("{club}") || ru.contains("{coach}") || ru.contains("{city}"))
        }
        assertTrue(c.answer("Я беременна", "ru").contains("Fit Almaty"))
    }

    @Test
    fun `trainer hint is shown at most once per three answers`() {
        val c = client()
        val hints = (1..3).count { c.answer("У меня высокое давление", "ru").contains("Спросить тренера") }
        assertEquals(1, hints)
    }

    @Test
    fun `red flag scenarios always mention doctor and trainer hint`() {
        val c = client()
        val pregnancy = c.answer("Я беременна", "ru")
        assertTrue(pregnancy.contains("врач"))
        assertTrue(pregnancy.contains("Спросить тренера"))
        val pressure = c.answer("Қан қысымым жоғары", "kk")
        assertTrue(pressure.contains("дәрігер"))
    }

    @Test
    fun `chat returns success with simulated delay`() = runTest {
        val result = client().chat("system", emptyList(), "Спасибо", "ru")
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isNotBlank())
    }
}
