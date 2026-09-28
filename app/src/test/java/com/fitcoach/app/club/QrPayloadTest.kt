package com.fitcoach.app.club

import com.fitcoach.app.data.club.QrPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrPayloadTest {
    @Test
    fun `payload is brandId colon memberId`() {
        assertEquals("demo:FC-7K2M9", QrPayload.build("demo", "FC-7K2M9"))
    }

    @Test
    fun `payload trims whitespace`() {
        assertEquals("ironclub:AB12", QrPayload.build(" ironclub ", " AB12 "))
    }

    @Test
    fun `parse roundtrip`() {
        val (brand, member) = QrPayload.parse(QrPayload.build("demo", "X-1:2"))!!
        assertEquals("demo", brand)
        assertEquals("X-1:2", member)
    }

    @Test
    fun `parse rejects malformed`() {
        assertNull(QrPayload.parse("nocolon"))
        assertNull(QrPayload.parse(":member"))
        assertNull(QrPayload.parse("brand:"))
    }
}
