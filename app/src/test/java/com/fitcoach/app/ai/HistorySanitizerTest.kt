package com.fitcoach.app.ai

import com.fitcoach.app.domain.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistorySanitizerTest {

    private var counter = 0L
    private fun msg(role: String, content: String, isError: Boolean = false) = ChatMessage(
        id = "m${counter}", role = role, content = content, timestamp = counter++, isError = isError
    )

    @Test
    fun `first message is always user and last is the new user message`() {
        val history = listOf(
            msg("assistant", "Привет! Чем помочь?"),
            msg("user", "Что поесть?"),
            msg("assistant", "Курица и рис.")
        )
        val turns = HistorySanitizer.build(history, "А после тренировки?")
        assertEquals("user", turns.first().role)
        assertEquals("user", turns.last().role)
        assertEquals("А после тренировки?", turns.last().content)
        assertEquals(listOf("user", "assistant", "user"), turns.map { it.role })
    }

    @Test
    fun `roles strictly alternate`() {
        val history = listOf(
            msg("user", "a"), msg("user", "b"), msg("assistant", "c"),
            msg("assistant", "d"), msg("user", "e"), msg("assistant", "f")
        )
        val turns = HistorySanitizer.build(history, "g")
        for (i in 1 until turns.size) {
            assertTrue("roles must alternate at $i", turns[i].role != turns[i - 1].role)
        }
    }

    @Test
    fun `error messages are dropped`() {
        val history = listOf(
            msg("user", "Болит спина"),
            msg("assistant", "Нет соединения с интернетом.", isError = true),
            msg("user", "Ещё раз: болит спина")
        )
        val turns = HistorySanitizer.build(history, "Что делать?")
        assertTrue(turns.none { it.content.contains("Нет соединения") })
        // два user подряд (после удаления ошибки) + новое сообщение → одна склеенная реплика
        assertEquals(1, turns.size)
        assertEquals("user", turns[0].role)
        assertTrue(turns[0].content.contains("Болит спина"))
        assertTrue(turns[0].content.contains("Что делать?"))
    }

    @Test
    fun `consecutive same-role messages are merged in order`() {
        val history = listOf(msg("user", "Раз"), msg("user", "Два"), msg("assistant", "Ок"), msg("assistant", "Ещё ок"))
        val turns = HistorySanitizer.build(history, "Три")
        assertEquals(3, turns.size)
        assertEquals("Раз\n\nДва", turns[0].content)
        assertEquals("Ок\n\nЕщё ок", turns[1].content)
        assertEquals("Три", turns[2].content)
    }

    @Test
    fun `blank and unknown roles are ignored`() {
        val history = listOf(msg("user", "   "), msg("system", "внутреннее"), msg("assistant", "Привет"))
        val turns = HistorySanitizer.build(history, "Вопрос")
        assertEquals(listOf(AiTurn("user", "Вопрос")), turns)
    }

    @Test
    fun `history is limited to maxTurns before the new message`() {
        val history = (1..30).map { i -> msg(if (i % 2 == 1) "user" else "assistant", "m$i") }
        val turns = HistorySanitizer.build(history, "new", maxTurns = 6)
        // 6 последних (m25..m30: user,assistant,...,assistant) + новое user
        assertEquals(7, turns.size)
        assertEquals("m25", turns.first().content)
        assertEquals("new", turns.last().content)
    }

    @Test
    fun `empty history yields only the user message`() {
        val turns = HistorySanitizer.build(emptyList(), "Привет")
        assertEquals(listOf(AiTurn("user", "Привет")), turns)
    }
}
