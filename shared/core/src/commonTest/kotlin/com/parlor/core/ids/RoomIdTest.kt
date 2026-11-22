package com.parlor.core.ids

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RoomIdTest {
    @Test
    fun room_ids_are_canonical_and_redacted_in_diagnostics() {
        val raw = "0123456789abcdef".repeat(2)
        assertEquals(raw, RoomId(raw).raw)
        assertEquals("RoomId(redacted)", RoomId(raw).toString())
        listOf("", "ABCDEF", raw.uppercase(), raw + "0", raw.dropLast(1) + "/").forEach {
            assertFailsWith<IllegalArgumentException> { RoomId(it) }
        }
    }
}
