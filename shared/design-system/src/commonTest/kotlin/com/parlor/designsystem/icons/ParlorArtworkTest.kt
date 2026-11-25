package com.parlor.designsystem.icons

import kotlin.test.Test
import kotlin.test.assertEquals

class ParlorArtworkTest {
    @Test
    fun each_game_has_a_distinct_packaged_cover() {
        assertEquals(6, ParlorArtwork.entries.size)
        assertEquals(6, ParlorArtwork.entries.map { it.cover }.toSet().size)
    }
}
