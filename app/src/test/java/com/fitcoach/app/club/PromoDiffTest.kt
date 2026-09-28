package com.fitcoach.app.club

import com.fitcoach.app.data.club.ClubContent
import com.fitcoach.app.data.club.Promo
import com.fitcoach.app.data.club.PromoDiff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromoDiffTest {
    private val content = ClubContent(
        promos = listOf(
            Promo(id = "a", title = "A"),
            Promo(id = "b", title = "B"),
            Promo(id = "expired", title = "E", validUntil = "2000-01-01")
        )
    )

    @Test
    fun `first run reports nothing as new`() {
        assertTrue(PromoDiff.newPromos(content, seenIds = null).isEmpty())
    }

    @Test
    fun `only unseen active promos are new`() {
        val fresh = PromoDiff.newPromos(content, seenIds = setOf("a"))
        assertEquals(listOf("b"), fresh.map { it.id })
    }

    @Test
    fun `nothing new when all seen`() {
        assertTrue(PromoDiff.newPromos(content, seenIds = setOf("a", "b", "expired")).isEmpty())
    }

    @Test
    fun `expired promo is never reported even if unseen`() {
        assertTrue(PromoDiff.newPromos(content, seenIds = setOf("a", "b")).isEmpty())
    }
}
