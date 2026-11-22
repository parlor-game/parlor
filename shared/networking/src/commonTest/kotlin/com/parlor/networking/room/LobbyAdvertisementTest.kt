package com.parlor.networking.room

import com.parlor.core.ids.GameId
import com.parlor.networking.protocol.ProtocolVersion
import com.parlor.networking.transport.HostedGameProtocol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LobbyAdvertisementTest {
    @Test
    fun all_shipping_game_ids_fit_within_the_lan_name_budget() {
        val games = listOf("whodunit", "mafia", "last-light", "word-impostor", "ghamza", "egyptian-dominoes")
        games.forEach { id ->
            val game = HostedGameProtocol(GameId(id), 9_999)
            val advertisement = assertNotNull(LobbyAdvertisement.encodeGame(game, "K7QX"))
            assertEquals(LobbyAdvertisement.Entry.GameRoom(ProtocolVersion(), game, "K7QX"),
                LobbyAdvertisement.parse(advertisement))
        }
        assertNull(LobbyAdvertisement.encodeGame(
            HostedGameProtocol(GameId("a".repeat(32)), 9_999), "K7QX", ProtocolVersion(99, 99),
        ))
    }

    @Test
    fun malformed_game_advertisements_do_not_produce_room_candidates() {
        val invalid = listOf(
            "4.3|g|Mafia|2|K7QX",
            "04.3|g|mafia|2|K7QX",
            "4.+3|g|mafia|2|K7QX",
            "4.3|g|mafia|02|K7QX",
            "4.3|g|mafia|0|K7QX",
            "4.3|g|mafia|10000|K7QX",
            "4.3|g|mafia|2|K7qx",
            "4.3|g|mafia|2|K7IX",
            "4.3|g|mafia|2|K7QX|",
            "4.3|g|mafia|2|K7QX ",
            "4.3|g|party|2|K7QX",
            "4.3|p|-|-|K7QX",
        )
        invalid.forEach { fields ->
            assertEquals(LobbyAdvertisement.Entry.Unrecognized, LobbyAdvertisement.parse("parlor-lobby|1|$fields"))
        }
    }

    @Test
    fun party_legacy_and_future_schemas_are_distinct_from_a_game_room() {
        assertEquals(LobbyAdvertisement.Entry.PartyRoom(ProtocolVersion(5, 0), "K7QX"),
            LobbyAdvertisement.parse("parlor-lobby|1|5.0|p|-|-|K7QX"))
        assertEquals(LobbyAdvertisement.Entry.Legacy, LobbyAdvertisement.parse("parlor-room|old"))
        assertEquals(LobbyAdvertisement.Entry.UnsupportedSchema, LobbyAdvertisement.parse("parlor-lobby|2|future"))
        assertEquals(LobbyAdvertisement.Entry.Unrecognized, LobbyAdvertisement.parse("parlor-lobby|"))
    }
}
