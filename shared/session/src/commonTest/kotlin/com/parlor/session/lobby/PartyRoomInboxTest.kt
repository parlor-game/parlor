package com.parlor.session.lobby

import com.parlor.core.ids.GameId
import com.parlor.core.ids.PartyId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.networking.protocol.GameProtocolRef
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PARTY_WIRE_VERSION
import com.parlor.networking.protocol.PartyLobbyPhase
import com.parlor.networking.protocol.PartyProtocolHeader
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.ProtocolVersion
import com.parlor.networking.protocol.PublicPartyLobby
import com.parlor.networking.protocol.PublicPartySeat
import com.parlor.networking.protocol.RoomMessage
import com.parlor.networking.protocol.SessionEnvelopeHeader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PartyRoomInboxTest {
    private val party = PartyId("a".repeat(32))
    private val otherParty = PartyId("b".repeat(32))
    private val host = PlayerId("host")
    private val player = PlayerId("approved-player")
    private val excluded = PlayerId("disconnected-seat")
    private val mafia = GameProtocolRef(GameId("mafia"), 2)
    private val word = GameProtocolRef(GameId("word-impostor"), 1)

    @Test
    fun a_single_inbox_sends_control_and_the_current_matches_frames_to_separate_consumers() = runTest {
        val physical = Channel<RoomMessage>(16)
        val router = PartyRoomInbox(party, host, true, physical.receiveAsFlow(), backgroundScope)
        try {
            val first = PartyMatch(SessionId("mafia-first"), mafia, setOf(host, player), 1)
            val game = assertNotNull(router.openMatch(first))
            val matchResponse = async { game.first() }
            val controlResponse = async { router.controls.first() }
            val ready = PeerMessage.PartySetReady(
                PartyProtocolHeader(PARTY_WIRE_VERSION, party, "party-ready-00001", 0), player, 1, 1, true,
            )
            physical.send(ready.copy(header = ready.header.copy(partyId = otherParty)))
            physical.send(ready)
            physical.send(heartbeat(first, excluded))
            physical.send(heartbeat(first, player, partyOverride = otherParty))
            physical.send(heartbeat(first, player, protocol = ProtocolVersion(4, 3)))
            val frame = heartbeat(first, player)
            physical.send(frame)
            assertEquals(ready, controlResponse.await())
            assertEquals(frame, matchResponse.await())

            assertFalse(router.closeMatch(SessionId("previous-lease")))
            assertTrue(router.closeMatch(first.id))
            assertNull(router.openMatch(first.copy(roster = setOf(player))))
            val second = PartyMatch(SessionId("word-second"), word, setOf(host, player), 2)
            val nextGame = assertNotNull(router.openMatch(second))
            val nextResponse = async { nextGame.first() }
            physical.send(frame)
            physical.send(heartbeat(second.copy(game = mafia), player))
            physical.send(heartbeat(second, player))
            assertEquals(heartbeat(second, player), nextResponse.await())
        } finally {
            router.close()
        }
    }

    @Test
    fun a_peer_filters_other_parties_rooms_and_game_versions_before_delivering_host_data() = runTest {
        val physical = Channel<RoomMessage>(16)
        val router = PartyRoomInbox(party, player, false, physical.receiveAsFlow(), backgroundScope)
        try {
            val match = PartyMatch(SessionId("word-second"), word, setOf(host, player), 2)
            val game = assertNotNull(router.openMatch(match))
            val response = async { game.first() }
            val wanted = HostMessage.Heartbeat(heartbeat(match, player).header, 0)
            physical.send(heartbeat(match, player))
            physical.send(wanted.copy(header = wanted.header.copy(gameVersion = 2)))
            physical.send(wanted.copy(header = wanted.header.copy(partyId = null)))
            physical.send(wanted)
            assertEquals(wanted, response.await())
        } finally {
            router.close()
        }
    }

    @Test
    fun peer_routes_the_first_game_frame_after_its_matching_party_snapshot_is_installed() = runTest {
        val physical = Channel<RoomMessage>(16)
        val router = PartyRoomInbox(party, player, false, physical.receiveAsFlow(), backgroundScope)
        try {
            val match = PartyMatch(SessionId("word-first"), word, setOf(host, player), 1)
            val header = PartyProtocolHeader(PARTY_WIRE_VERSION, party, "party-snapshot-0001", 1)
            val snapshot = HostMessage.PartySnapshot(
                header, 1, 1,
                PublicPartyLobby(
                    PartyLobbyPhase.MatchStarting, word,
                    listOf(
                        PublicPartySeat(host, "Host", connected = true, ready = false, inActiveMatch = true),
                        PublicPartySeat(player, "Peer", connected = true, ready = true, inActiveMatch = true),
                    ),
                    4, match.id, 0,
                ),
            )
            val received = CompletableDeferred<Unit>()
            val install = CompletableDeferred<Unit>()
            val scopeInstalled = CompletableDeferred<Flow<RoomMessage>>()
            val installation = async {
                router.controls.first {
                    assertEquals(snapshot, it)
                    received.complete(Unit)
                    install.await()
                    scopeInstalled.complete(assertNotNull(router.openMatch(match)))
                    true
                }
            }
            physical.send(snapshot)
            received.await()
            val first = HostMessage.Heartbeat(heartbeat(match, player).header, 0)
            physical.send(first.copy(header = first.header.copy(gameId = mafia.gameId)))
            physical.send(first)
            runCurrent()
            install.complete(Unit)
            installation.await()
            assertEquals(first, withTimeout(1_000L) { scopeInstalled.await().first() })
        } finally {
            router.close()
        }
    }

    private fun heartbeat(
        match: PartyMatch,
        actor: PlayerId,
        partyOverride: PartyId = party,
        protocol: ProtocolVersion = PARTY_WIRE_VERSION,
    ) = PeerMessage.SessionHeartbeat(
        SessionEnvelopeHeader(
            protocol = protocol,
            sessionId = match.id,
            gameId = match.game.gameId,
            gameVersion = match.game.gameVersion,
            messageId = "party-game-heartbeat",
            sequence = 0,
            partyId = partyOverride,
        ),
        actor,
        lastAppliedRevision = 0,
    )
}
