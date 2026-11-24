package com.parlor.core.ids

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameIdTest {
    @Test
    fun accepts_shipping_identifiers_and_boundary_lengths() {
        listOf(
            "whodunit", "mafia", "last-light", "word-impostor", "ghamza", "egyptian-dominoes",
            "a", "a".repeat(32), "game-7a",
        ).forEach { raw ->
            assertTrue(GameId.isCanonical(raw), raw)
            GameId(raw).requireCanonical()
        }
    }

    @Test
    fun rejects_noncanonical_identifiers_without_reinterpreting_them() {
        listOf(
            "", "game ", " game", "Game", "7game", "game_name", "-game", "game-", "game--name",
            "game/room", "game\n", "gáme", "لعبة", "a".repeat(33), "party", "parlor", "parlor-x",
        ).forEach { raw ->
            assertFalse(GameId.isCanonical(raw), raw)
        }
        assertFailsWith<IllegalArgumentException> { GameId("Party").requireCanonical() }
    }
}
