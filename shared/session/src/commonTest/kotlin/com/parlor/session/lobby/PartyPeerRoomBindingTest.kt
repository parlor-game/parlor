package com.parlor.session.lobby

import com.parlor.core.ids.GameId
import com.parlor.core.ids.PartyId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.result.Result
import com.parlor.networking.protocol.CommandStatus
import com.parlor.networking.protocol.GameProtocolRef
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PARTY_WIRE_VERSION
import com.parlor.networking.protocol.PartyLobbyPhase
import com.parlor.networking.protocol.PartyProtocolHeader
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.PublicPartyLobby
import com.parlor.networking.protocol.PublicPartySeat
import com.parlor.networking.protocol.RoomMessage
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.room.RoomInfo
import com.parlor.networking.room.RoomMember
import com.parlor.networking.room.SendTarget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class PartyPeerRoomBindingTest {
    private val party = PartyId("a".repeat(32))
    private val unrelated = PartyId("b".repeat(32))
    private val host = PlayerId("host")
    private val remote = PlayerId("client")
    private val mafia = GameProtocolRef(GameId("mafia"), 2)
    private val word = GameProtocolRef(GameId("word-impostor"), 1)
    private val matchId = SessionId("first-match")

    @Test
    fun lobby_commands_and_match_changes_require_the_authenticated_partys_authority() = runTest {
        val room = PeerRoom()
        val peer = PartyPeerRoomBinding(room, setOf(mafia, word), backgroundScope) { "peer-command-0001" }
        room.messages.send(snapshot(1).let { it.copy(header = it.header.copy(partyId = unrelated)) })
        runCurrent()
        assertNull(peer.state.value)
        assertEquals(Result.Failure(NetError.NotConnected), peer.setReady(true))

        room.messages.send(snapshot(1))
        runCurrent()
        val pending = async { peer.setReady(true) }
        runCurrent()
        val sent = assertIs<PeerMessage.PartySetReady>(room.sent.single())
        assertEquals(1L, sent.clientSequence)
        assertEquals(1L, sent.expectedRevision)
        assertEquals(Result.Failure(NetError.CommandInFlight), peer.setReady(true))
        assertFalse(assertNotNull(peer.state.value).lobby.members.last().ready)
        val correct = HostMessage.PartyCommandResult(
            header(2), sent.header.messageId, CommandStatus.Applied, 2, 2,
        )
        room.messages.send(correct.copy(commandMessageId = "irrelevant-command-0001"))
        runCurrent()
        assertFalse(pending.isCompleted)
        room.messages.send(correct)
        room.messages.send(snapshot(2, nextSequence = 2))
        assertEquals(Result.Success(CommandStatus.Applied), pending.await())

        room.messages.send(snapshot(3, PartyLobbyPhase.MatchStarting, matchId, nextSequence = 2))
        runCurrent()
        val started = assertNotNull(peer.match.value)
        assertEquals(mafia.gameId, started.matchProtocol.gameId)
        room.messages.send(snapshot(4, PartyLobbyPhase.MatchStarting, matchId, word, nextSequence = 2))
        runCurrent()
        assertEquals(3L, assertNotNull(peer.state.value).revision)
        assertEquals(mafia.gameId, assertNotNull(peer.match.value).matchProtocol.gameId)

        room.messages.send(snapshot(5, game = word, nextSequence = 2))
        room.messages.send(snapshot(3, PartyLobbyPhase.MatchStarting, matchId, nextSequence = 2))
        runCurrent()
        assertNull(peer.match.value)
        assertEquals(word, assertNotNull(peer.state.value).lobby.selectedGame)
        assertEquals(0, room.leaveCount)

        room.messages.send(snapshot(6, PartyLobbyPhase.Ended, game = word, nextSequence = 2))
        runCurrent()
        assertEquals(1, room.leaveCount)
        assertEquals(PartyLobbyPhase.Ended, assertNotNull(peer.state.value).lobby.phase)
    }

    private fun header(revision: Long) = PartyProtocolHeader(PARTY_WIRE_VERSION, party, "host-message-0001", revision)

    private fun snapshot(
        revision: Long,
        phase: PartyLobbyPhase = PartyLobbyPhase.Lobby,
        match: SessionId? = null,
        game: GameProtocolRef = mafia,
        nextSequence: Long = 1,
    ) = HostMessage.PartySnapshot(
        header(revision), revision, nextSequence,
        PublicPartyLobby(
            phase, game,
            listOf(
                PublicPartySeat(host, "Host", connected = true, ready = false, inActiveMatch = match != null),
                PublicPartySeat(remote, "Peer", connected = true, ready = nextSequence > 1, inActiveMatch = match != null),
            ),
            4, match, 0,
        ),
    )

    private inner class PeerRoom : LocalRoom {
        override val isHost = false
        override val info = MutableStateFlow(RoomInfo("ABCDEF", "Host", host, RoomInfo.Status.Joined, partyId = party))
        override val members = MutableStateFlow<List<RoomMember>>(emptyList())
        override val selfPlayerId = remote
        val messages = Channel<RoomMessage>(16)
        override val incoming = messages.receiveAsFlow()
        val sent = mutableListOf<PeerMessage>()
        var leaveCount = 0

        override suspend fun send(target: SendTarget, message: HostMessage): Result<Unit, NetError> =
            Result.Failure(NetError.Unauthorized)

        override suspend fun sendToHost(message: PeerMessage): Result<Unit, NetError> {
            sent += message
            return Result.Success(Unit)
        }

        override suspend fun leave() {
            leaveCount++
        }
    }
}
