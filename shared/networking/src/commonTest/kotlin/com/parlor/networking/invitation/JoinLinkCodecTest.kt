package com.parlor.networking.invitation

import com.parlor.core.ids.GameId
import com.parlor.core.ids.PartyId
import com.parlor.core.ids.RoomId
import com.parlor.networking.protocol.ProtocolVersion
import com.parlor.networking.transport.HostedGameProtocol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class JoinLinkCodecTest {
    private val roomId = RoomId("1".repeat(32))
    private val game = HostedGameProtocol(GameId("egyptian-dominoes"), 2)
    private val fingerprint = "p2f1-zlmerarbaugm753v5mvipavkkhwxbvlu3cpx4unzvuvov7zu7dkq"
    private val canonical = assertNotNull(JoinLinkCodec.encode(game, roomId, "K7QX", "ABCDEF", fingerprint))

    @Test
    fun invitation_round_trips_without_putting_the_code_in_debug_text() {
        val decoded = assertIs<JoinLinkCodec.Decoded.Valid>(JoinLinkCodec.decode(canonical))
        assertEquals(JoinInvitation(game, roomId, "K7QX", "ABCDEF", fingerprint), decoded.invitation)
        assertFalse("ABCDEF" in decoded.toString() || fingerprint in decoded.toString())
        val reordered = canonical.substringBefore('?') + "?" +
            canonical.substringAfter('?').split('&').reversed().joinToString("&")
        assertEquals(decoded, JoinLinkCodec.decode(reordered))
    }

    @Test
    fun malformed_or_ambiguous_links_fail_closed() {
        val invalid = listOf(
            canonical.replace("://join?", "://user@join?"),
            canonical.replace("://join?", "://join:123?"),
            canonical.replace("://join?", "://join/?"),
            canonical.replace("://join?", "://join.example?"),
            canonical.replace("ABCDEF", "%41BCDEF"),
            canonical.replace("ABCDEF", "abcdef"),
            canonical.replace(roomId.raw, "A".repeat(32)),
            canonical.replace("g=egyptian-dominoes", "g=party"),
            canonical.replace("gv=2", "gv=02"),
            canonical.replace("gv=2", "gv=0"),
            canonical.replace("t=K7QX", "t=I7QX"),
            canonical.replace(fingerprint, fingerprint.dropLast(1) + "b"),
            canonical + "&g=mafia",
            canonical + "&ip=127.0.0.1",
            canonical + "#preview",
            canonical + " ",
            canonical + "x".repeat(256),
        )
        invalid.forEach { assertIs<JoinLinkCodec.Decoded.Invalid>(JoinLinkCodec.decode(it)) }
    }

    @Test
    fun party_links_are_bound_to_the_group_and_cannot_decode_as_game_links() {
        val partyId = PartyId("a".repeat(32))
        val link = assertNotNull(JoinLinkCodec.encodeParty(partyId, roomId, "K7QX", "ABCDEF", fingerprint))
        val found = assertNotNull(JoinLinkCodec.decodeParty(link))
        assertEquals(PartyJoinInvitation(partyId, roomId, "K7QX", "ABCDEF", fingerprint), found)
        assertFalse(found.toString().contains(partyId.raw) || found.toString().contains(found.code))
        assertIs<JoinLinkCodec.Decoded.IncompatibleVersion>(JoinLinkCodec.decode(link))
        assertNull(JoinLinkCodec.decodeParty(canonical))
        assertNull(JoinLinkCodec.decodeParty(link + "&g=mafia"))
        assertNull(JoinLinkCodec.decodeParty(link + "&party=" + partyId.raw))
        assertNull(JoinLinkCodec.decodeParty(link.replace("p=5.0", "p=4.3")))
        assertNull(JoinLinkCodec.decodeParty(link.replace(partyId.raw, "G".repeat(32))))
    }

    @Test
    fun version_mismatch_is_detected_before_routing() {
        val protocol = ProtocolVersion()
        assertIs<JoinLinkCodec.Decoded.IncompatibleVersion>(
            JoinLinkCodec.decode(canonical.replace("p=${protocol.major}.${protocol.minor}", "p=9.0")),
        )
        assertIs<JoinLinkCodec.Decoded.IncompatibleVersion>(JoinLinkCodec.decode("parlor://join?v=2"))
    }
}
