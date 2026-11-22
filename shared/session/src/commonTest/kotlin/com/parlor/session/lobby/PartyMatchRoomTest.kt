package com.parlor.session.lobby

import com.parlor.core.ids.GameId
import com.parlor.core.ids.PartyId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.result.Result
import com.parlor.engine.state.Player
import com.parlor.networking.protocol.GameProtocolRef
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PARTY_WIRE_VERSION
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.RoomMessage
import com.parlor.networking.protocol.SessionEndReason
import com.parlor.networking.protocol.SessionEnvelopeHeader
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.room.RoomInfo
import com.parlor.networking.room.RoomLifecycleState
import com.parlor.networking.room.RoomMember
import com.parlor.networking.room.SendTarget
import com.parlor.networking.room.SessionEndCommitStatus
import com.parlor.session.multidevice.CommandApplication
import com.parlor.session.multidevice.HostAuthoritativeSessionCoordinator
import com.parlor.session.multidevice.PlayerSnapshotPayload
import com.parlor.session.multidevice.ValidatedSessionStart
import com.parlor.session.multidevice.awaitAuthoritativeSessionStart
import com.parlor.session.multidevice.hostSessionProtocol
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class PartyMatchRoomTest {
    private val party = PartyId("a".repeat(32))
    private val host = PlayerId("physical-host")
    private val player = PlayerId("playing-seat")
    private val excluded = PlayerId("benched-seat")
    private val firstGame = GameProtocolRef(GameId("mafia"), 2)
    private val secondGame = GameProtocolRef(GameId("word-impostor"), 1)
    private val match = PartyMatch(SessionId("first-match"), firstGame, setOf(host, player), 1)

    @Test
    fun host_frames_never_reach_the_benched_seat_and_old_matches_cannot_use_the_room() = runTest {
        val physical = PhysicalRoom(isHost = true)
        val inbox = PartyRoomInbox(party, host, true, physical.incoming, backgroundScope)
        val first = assertNotNull(PartyMatchRoom.attach(physical, match, inbox, backgroundScope))
        try {
            assertEquals(listOf(player), first.members.value.map(RoomMember::playerId))
            assertEquals(party, first.matchProtocol.partyId)
            val heartbeat = HostMessage.Heartbeat(header(match), 0)
            assertEquals(Result.Success(Unit), first.send(SendTarget.Broadcast, heartbeat))
            assertEquals(Result.Failure(NetError.Unauthorized), first.send(SendTarget.Direct(excluded), heartbeat))
            val snapshot = HostMessage.PlayerSnapshot(header(match), 0, 1, byteArrayOf(1), byteArrayOf(2))
            assertEquals(Result.Failure(NetError.Unauthorized), first.send(SendTarget.Broadcast, snapshot))
            val expected = listOf<Pair<SendTarget, HostMessage>>(SendTarget.Direct(player) to heartbeat)
            assertEquals(expected, physical.sent)

            assertEquals(Result.Success(Unit), first.finalLeave())
            assertEquals(RoomLifecycleState.Closed, first.lifecycle.value)
            assertEquals(0, physical.leaveCalls)
            val secondMatch = PartyMatch(SessionId("second-match"), secondGame, setOf(host, excluded), 2)
            val second = assertNotNull(PartyMatchRoom.attach(physical, secondMatch, inbox, backgroundScope))
            assertEquals(Result.Failure(NetError.Unauthorized), first.send(SendTarget.Direct(player), heartbeat))
            assertEquals(Result.Failure(NetError.Unauthorized), second.send(SendTarget.Broadcast, heartbeat))
            val updated = HostMessage.Heartbeat(header(secondMatch), 0)
            assertEquals(Result.Success(Unit), second.send(SendTarget.Broadcast, updated))
            assertEquals(SendTarget.Direct(excluded) to updated, physical.sent.last())
            second.leave()
        } finally {
            first.leave()
            inbox.close()
        }
    }

    @Test
    fun peer_commits_only_the_active_match_end_and_keeps_the_physical_room_open() = runTest {
        val physical = PhysicalRoom(isHost = false)
        val inbox = PartyRoomInbox(party, player, false, physical.incoming, backgroundScope)
        val gameRoom = assertNotNull(PartyMatchRoom.attach(physical, match, inbox, backgroundScope))
        try {
            val terminal = HostMessage.SessionEnded(header(match), SessionEndReason.Cancelled, 0)
            val wrong = terminal.copy(header = terminal.header.copy(partyId = PartyId("b".repeat(32))))
            assertEquals(Result.Success(SessionEndCommitStatus.NotOwned), gameRoom.commitValidatedSessionEnd(wrong))
            assertEquals(Result.Success(SessionEndCommitStatus.Committed), gameRoom.commitValidatedSessionEnd(terminal))
            val heartbeat = PeerMessage.SessionHeartbeat(header(match), excluded, 0)
            assertEquals(Result.Failure(NetError.Unauthorized), gameRoom.sendToHost(heartbeat))
            assertEquals(Result.Success(Unit), gameRoom.sendToHost(heartbeat.copy(actor = player)))
            assertEquals(1, physical.terminalCalls)
            gameRoom.closeForRetry()
            assertEquals(0, physical.leaveCalls)
            assertEquals(Result.Success(SessionEndCommitStatus.NotOwned), gameRoom.commitValidatedSessionEnd(terminal))
        } finally {
            gameRoom.leave()
            inbox.close()
        }
    }

    @Test
    fun an_existing_authoritative_game_barrier_uses_the_host_selected_match_and_only_its_party() = runTest {
        val hostRoom = PhysicalRoom(isHost = true)
        val peerRoom = PhysicalRoom(isHost = false)
        hostRoom.onHostFrame = { frame -> peerRoom.wire.send(frame) }
        peerRoom.onPeerFrame = { frame -> hostRoom.wire.send(frame) }
        val hostInbox = PartyRoomInbox(party, host, true, hostRoom.incoming, backgroundScope)
        val peerInbox = PartyRoomInbox(party, player, false, peerRoom.incoming, backgroundScope)
        val partyHost = assertNotNull(PartyMatchRoom.attach(hostRoom, match, hostInbox, backgroundScope))
        val partyPeer = assertNotNull(PartyMatchRoom.attach(peerRoom, match, peerInbox, backgroundScope))
        val protocol = partyHost.hostSessionProtocol(firstGame.gameId, firstGame.gameVersion) {
            error("Host attempted to invent an independent session ID")
        }
        assertFailsWith<IllegalArgumentException> {
            partyHost.hostSessionProtocol(secondGame.gameId, secondGame.gameVersion) { "unexpected-id" }
        }
        val players = listOf(Player(host, "Host", 0), Player(player, "Peer", 1))
        val coordinator = HostAuthoritativeSessionCoordinator(
            room = partyHost,
            protocol = protocol,
            remotePlayers = setOf(player),
            scope = backgroundScope,
            applyCommand = { _, _ -> CommandApplication.InvalidAction },
            snapshotFor = { PlayerSnapshotPayload(byteArrayOf(1), byteArrayOf(2)) },
            heartbeatIntervalMs = 0L,
            requireStartHandshake = true,
        )
        try {
            val receiving = async {
                awaitAuthoritativeSessionStart(partyPeer, firstGame.gameId, firstGame.gameVersion) { offer, context ->
                    offer.header.partyId == party && context.sessionId == match.id && offer.players == players
                }
            }
            val sent = assertIs<Result.Success<HostMessage.SessionStarting>>(
                coordinator.startSession("case", "mode", players, sessionNonce = 1L),
            ).data
            val accepted = assertIs<Result.Success<ValidatedSessionStart>>(receiving.await()).data
            assertEquals(sent, accepted.offer)
            assertEquals(party, accepted.protocol.partyId)
            assertEquals(match.id, accepted.protocol.sessionId)
        } finally {
            coordinator.close()
            partyHost.leave()
            partyPeer.leave()
            hostInbox.close()
            peerInbox.close()
        }
    }

    private fun header(match: PartyMatch) = SessionEnvelopeHeader(
        protocol = PARTY_WIRE_VERSION,
        sessionId = match.id,
        gameId = match.game.gameId,
        gameVersion = match.game.gameVersion,
        messageId = "test-party-match-msg",
        sequence = 1,
        partyId = party,
    )

    private inner class PhysicalRoom(override val isHost: Boolean) : LocalRoom {
        override val selfPlayerId = if (isHost) host else player
        override val info = MutableStateFlow(RoomInfo("123456", "Host", host, RoomInfo.Status.Joined, partyId = party))
        override val members = MutableStateFlow(listOf(RoomMember(player, "Player", true), RoomMember(excluded, "Benched", true)))
        val wire = Channel<RoomMessage>(16)
        override val incoming = wire.receiveAsFlow()
        var onHostFrame: suspend (HostMessage) -> Unit = {}
        var onPeerFrame: suspend (PeerMessage) -> Unit = {}
        val sent = mutableListOf<Pair<SendTarget, HostMessage>>()
        var terminalCalls = 0
        var leaveCalls = 0

        override suspend fun send(target: SendTarget, message: HostMessage): Result<Unit, NetError> {
            sent.add(target to message)
            onHostFrame(message)
            return Result.Success(Unit)
        }

        override suspend fun sendToHost(message: PeerMessage): Result<Unit, NetError> {
            onPeerFrame(message)
            return Result.Success(Unit)
        }

        override suspend fun commitValidatedSessionEnd(
            message: HostMessage.SessionEnded,
        ): Result<SessionEndCommitStatus, NetError> {
            terminalCalls++
            return Result.Success(SessionEndCommitStatus.Committed)
        }

        override suspend fun leave() {
            leaveCalls++
        }
    }
}
