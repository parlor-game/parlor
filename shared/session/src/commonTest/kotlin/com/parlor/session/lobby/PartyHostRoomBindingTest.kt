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
import com.parlor.networking.protocol.RoomMessage
import com.parlor.networking.protocol.SessionEndReason
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.room.PartyMatchAdmission
import com.parlor.networking.room.RoomInfo
import com.parlor.networking.room.RoomMember
import com.parlor.networking.room.SendTarget
import com.parlor.networking.transport.HostConfig
import com.parlor.networking.transport.RoomTransport
import com.parlor.networking.transport.TransportCapability
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PartyHostRoomBindingTest {
    private val party = PartyId("a".repeat(32))
    private val hostId = PlayerId("host")
    private val playerId = PlayerId("client")
    private val mafia = PartyGame(GameProtocolRef(GameId("mafia"), 2), 2..4)
    private val word = PartyGame(GameProtocolRef(GameId("word-impostor"), 1), 2..4)
    private val games = setOf(mafia.protocol, word.protocol)

    @Test
    fun confirmed_members_ready_once_and_stay_connected_through_an_explicit_game_switch() = runTest {
        val room = HostRoom()
        val host = PartyHostRoomBinding(room, mafia, games, backgroundScope, messageId = { "host-message-0001" })
        try {
            room.members.value = listOf(RoomMember(playerId, "Peer", connected = true))
            runCurrent()
            assertEquals(setOf(hostId), host.state.value.seats.keys)
            room.members.value = listOf(RoomMember(playerId, "Peer", connected = true, partySupportedGames = games.toList()))
            runCurrent()

            assertEquals(Result.Failure(PartyRejection.WaitingForReadiness), host.prepareMatch())
            room.messages.send(ready(1, host.state.value.revision))
            runCurrent()
            assertTrue(host.state.value.seats.getValue(playerId).ready)
            assertEquals(CommandStatus.Applied, room.sent.filterIsInstance<HostMessage.PartyCommandResult>().single().status)

            val first = assertIs<Result.Success<PartyMatchRoom>>(host.prepareMatch()).data
            assertEquals(mafia.protocol.gameId, first.matchProtocol.gameId)
            assertIs<Result.Success<PartyLobby>>(host.commitMatch(first.matchProtocol.sessionId))
            assertIs<Result.Success<PartyLobby>>(host.returnToLobby(first.matchProtocol.sessionId))
            assertNull(room.frozen)
            assertEquals(0, room.leaveCount)
            assertEquals(setOf(hostId, playerId), host.state.value.seats.keys)

            assertIs<Result.Success<PartyLobby>>(host.selectGame(word))
            room.messages.send(ready(2, host.state.value.revision))
            runCurrent()
            val second = assertIs<Result.Success<PartyMatchRoom>>(host.prepareMatch()).data
            assertEquals(word.protocol.gameId, second.matchProtocol.gameId)
            assertNotEquals(first.matchProtocol.sessionId, second.matchProtocol.sessionId)
            assertEquals(Result.Failure(PartyRejection.StaleMatch), host.returnToLobby(first.matchProtocol.sessionId))
            assertEquals(second.matchProtocol.sessionId, room.frozen)
            assertTrue(room.sent.filterIsInstance<HostMessage.PartySnapshot>()
                .any { it.lobby.selectedGame == word.protocol && it.lobby.members.any { seat -> seat.playerId == playerId } })
        } finally {
            assertEquals(Result.Success(Unit), host.leave())
            assertEquals(1, room.leaveCount)
        }
    }

    @Test
    fun match_room_is_not_returned_until_the_starting_roster_has_been_announced() = runTest {
        val room = HostRoom()
        val host = PartyHostRoomBinding(room, mafia, games, backgroundScope)
        val attempted = CompletableDeferred<HostMessage.PartySnapshot>()
        val release = CompletableDeferred<Unit>()
        room.beforeSend = { message ->
            if (message is HostMessage.PartySnapshot && message.lobby.phase == PartyLobbyPhase.MatchStarting) {
                attempted.complete(message)
                release.await()
            }
        }
        try {
            room.members.value = listOf(RoomMember(playerId, "Peer", connected = true, partySupportedGames = games.toList()))
            runCurrent()
            room.messages.send(ready(1, host.state.value.revision))
            runCurrent()

            val start = async { host.prepareMatch() }
            val announcement = attempted.await()
            assertFalse(start.isCompleted)
            release.complete(Unit)
            val matchRoom = assertIs<Result.Success<PartyMatchRoom>>(start.await()).data
            assertEquals(matchRoom.matchProtocol.sessionId, announcement.lobby.activeMatchId)
            assertEquals(mafia.protocol, announcement.lobby.selectedGame)
            assertTrue(announcement.lobby.members.single { it.playerId == playerId }.inActiveMatch)
        } finally {
            release.complete(Unit)
            host.leave()
        }
    }

    @Test
    fun process_owner_opens_exactly_one_party_and_only_the_current_owner_can_close_it() = runTest {
        var openCalls = 0
        var lastRoom: HostRoom? = null
        val transport = object : RoomTransport {
            override val capability = TransportCapability(false, 0, 16_384)
            override suspend fun host(config: HostConfig): Result<LocalRoom, NetError> {
                openCalls++
                val configured = requireNotNull(config.partyProtocol)
                assertNull(config.gameProtocol)
                assertEquals(mafia.protocol.gameId, configured.initialGame.gameId)
                assertEquals(mafia.playerCounts.last, configured.initialSeatLimit)
                return Result.Success(HostRoom(configured.id).also { lastRoom = it })
            }

            override suspend fun join(code: String, displayName: String): Result<LocalRoom, NetError> =
                Result.Failure(NetError.Unauthorized)
        }
        val owner = ProcessPartySessionOwner(transport, backgroundScope)
        val catalog = setOf(mafia, word)
        assertEquals(Result.Failure(NetError.InvalidInput), owner.host("Host", emptySet(), mafia))
        assertEquals(0, openCalls)
        val opened = assertIs<Result.Success<ProcessPartySession>>(owner.host("Host", catalog, mafia)).data
        val host = assertIs<ProcessPartySession.Host>(opened)
        assertEquals(host, owner.session.value)
        assertEquals(Result.Failure(NetError.AlreadyConnected), owner.host("Host", catalog, mafia))
        assertEquals(1, openCalls)
        assertEquals(Result.Failure(PartyRejection.IncompatibleGame), host.selectGame(word.copy(playerCounts = 2..3)))
        assertEquals(Result.Success(Unit), owner.leave(host))
        assertEquals(Result.Failure(NetError.StaleRoom), owner.leave(host))
        assertEquals(1, requireNotNull(lastRoom).leaveCount)
        assertNull(owner.session.value)
    }

    @Test
    fun rematch_label_source_tracks_completed_games_without_reclassifying_a_switch() = runTest {
        val room = HostRoom()
        val host = ProcessPartySession.Host(room, mafia, setOf(mafia, word), backgroundScope, roomName = null)
        try {
            room.members.value = listOf(RoomMember(playerId, "Peer", connected = true, partySupportedGames = games.toList()))
            runCurrent()
            for ((index, reason) in listOf(SessionEndReason.Cancelled, SessionEndReason.Completed).withIndex()) {
                val revision = room.sent.filterIsInstance<HostMessage.PartySnapshot>().last().revision
                room.messages.send(ready(index + 1L, revision))
                runCurrent()

                val session = assertIs<Result.Success<*>>(host.start()).data
                assertEquals(host.match.value, session)
                val matchId = checkNotNull(room.frozen)
                assertEquals(Result.Success(Unit), host.confirmStarted(matchId))
                assertEquals(Result.Success(Unit), host.returnToLobby(matchId, reason))
                assertEquals(mafia.protocol.gameId.takeIf { reason == SessionEndReason.Completed }, host.lastCompletedGame.value)
                runCurrent()
            }

            assertEquals(Result.Success(Unit), host.selectGame(word))
            assertEquals(mafia.protocol.gameId, host.lastCompletedGame.value)
        } finally {
            host.leave()
        }
    }

    private fun ready(sequence: Long, revision: Long) = PeerMessage.PartySetReady(
        PartyProtocolHeader(PARTY_WIRE_VERSION, party, "peer-message-000$sequence", 0),
        playerId, sequence, revision, ready = true,
    )

    private inner class HostRoom(private val identity: PartyId = party) : LocalRoom {
        override val info = MutableStateFlow(RoomInfo("ABCDEF", "Host", hostId, RoomInfo.Status.Hosting, partyId = identity))
        override val members = MutableStateFlow<List<RoomMember>>(emptyList())
        override val selfPlayerId = hostId
        override val isHost = true
        val messages = Channel<RoomMessage>(16)
        override val incoming = messages.receiveAsFlow()
        val sent = mutableListOf<HostMessage>()
        var beforeSend: suspend (HostMessage) -> Unit = {}
        var selected = mafia.protocol
        var frozen: SessionId? = null
        var leaveCount = 0
        override val partyMatches: PartyMatchAdmission = object : PartyMatchAdmission {
            override val partyId = identity

            override suspend fun selectGame(
                expected: GameProtocolRef,
                next: GameProtocolRef,
                seatLimit: Int,
                expectedMembers: Set<PlayerId>,
            ): Result<Unit, NetError> {
                if (expected != selected || expectedMembers != setOf(playerId)) return Result.Failure(NetError.StaleRoom)
                selected = next
                return Result.Success(Unit)
            }

            override suspend fun freezeMatch(id: SessionId, expectedPeers: Set<PlayerId>): Result<List<RoomMember>, NetError> {
                if (expectedPeers != setOf(playerId)) return Result.Failure(NetError.NotConnected)
                frozen = id
                return Result.Success(members.value)
            }

            override suspend fun reopenMatch(id: SessionId): Result<Unit, NetError> {
                if (frozen != id) return Result.Failure(NetError.StaleRoom)
                frozen = null
                return Result.Success(Unit)
            }
        }

        override suspend fun send(target: SendTarget, message: HostMessage): Result<Unit, NetError> {
            assertEquals(SendTarget.Direct(playerId), target)
            beforeSend(message)
            sent += message
            return Result.Success(Unit)
        }

        override suspend fun sendToHost(message: PeerMessage): Result<Unit, NetError> = Result.Failure(NetError.Unauthorized)

        override suspend fun leave() {
            leaveCount++
        }
    }
}
