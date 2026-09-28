package com.fitcoach.app.club

import com.fitcoach.app.data.club.ClubJsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class ClubJsonParserTest {

    @Test
    fun `parses full schema and ignores unknown keys`() {
        val json = """
            {
              "version": 1,
              "futureField": {"x": 1},
              "trainers": [{"name": "Данияр Ахметов", "role": "Тренер", "whatsapp": "+7 (700) 123-45-67", "instagram": "@daniyar", "specialties": ["Сила"], "extra": true}],
              "services": [{"title": "Абонемент", "price": 25000, "unit": "мес"}],
              "schedule": [{"day": 3, "time": "19:00", "title": "Круговая", "trainer": "Данияр", "durationMin": 50}],
              "promos": [{"id": "p1", "title": "Акция", "text": "Текст", "validUntil": "2030-01-01", "ctaText": "Го"}],
              "hashtags": ["#a", "#b"],
              "referralCode": "FRIEND10",
              "referralText": "Скидка другу"
            }
        """.trimIndent()

        val content = ClubJsonParser.parse(json).getOrThrow()
        assertEquals(1, content.trainers.size)
        assertEquals("77001234567", content.trainers[0].whatsappDigits)
        assertEquals("daniyar", content.trainers[0].instagramHandle)
        assertEquals("25 000 ₸ / мес", content.services[0].priceLabel)
        assertEquals(1, content.scheduleFor(3).size)
        assertTrue(content.scheduleFor(1).isEmpty())
        assertEquals(LocalDate.of(2030, 1, 1), content.promos[0].validUntilDate())
        assertEquals("FRIEND10", content.referralCode)
        assertEquals(listOf("#a", "#b"), content.hashtags)
    }

    @Test
    fun `missing optional sections default to empty`() {
        val content = ClubJsonParser.parse("""{"promos": [{"id": "x", "title": "T"}]}""").getOrThrow()
        assertTrue(content.trainers.isEmpty())
        assertTrue(content.services.isEmpty())
        assertEquals("", content.referralCode)
        assertNull(content.promos[0].validUntil)
        assertTrue(content.promos[0].isActive())
    }

    @Test
    fun `expired promos are filtered by activePromos`() {
        val content = ClubJsonParser.parse(
            """{"promos": [
                {"id": "old", "title": "Old", "validUntil": "2020-01-01"},
                {"id": "bad", "title": "Bad date", "validUntil": "31.12.2026"},
                {"id": "new", "title": "New", "validUntil": "2099-12-31"},
                {"id": "forever", "title": "Forever"}
            ]}"""
        ).getOrThrow()
        val active = content.activePromos(LocalDate.of(2026, 9, 28)).map { it.id }
        assertEquals(listOf("bad", "new", "forever"), active) // нечитаемая дата = бессрочно
    }

    @Test
    fun `invalid json returns failure`() {
        assertTrue(ClubJsonParser.parse("not json").isFailure)
    }

    @Test
    fun `bundled demo asset parses and is non-empty`() {
        val file = listOf("src/main/assets/club/club.json", "app/src/main/assets/club/club.json").map { File(it) }.firstOrNull { it.exists() }
        assertNotNull("club.json not found relative to ${File(".").absolutePath}", file)
        val content = ClubJsonParser.parse(file!!.readText()).getOrThrow()
        assertTrue(content.trainers.size >= 3)
        assertTrue(content.services.size >= 5)
        assertTrue(content.schedule.size >= 7)
        assertTrue(content.promos.isNotEmpty())
        assertTrue(content.hashtags.isNotEmpty())
        assertTrue(content.referralCode.isNotBlank())
        assertTrue(content.schedule.all { it.day in 1..7 && Regex("\\d{2}:\\d{2}").matches(it.time) })
        assertEquals(content.promos.size, content.promos.map { it.id }.toSet().size)
        val encoded = ClubJsonParser.encode(content)
        assertEquals(content, ClubJsonParser.parse(encoded).getOrThrow())
    }
}
