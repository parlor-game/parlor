package com.parlor.transport.p2p

import com.parlor.core.ids.GameId
import com.parlor.core.ids.PartyId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.result.Result
import com.parlor.networking.invitation.JoinLinkCodec
import com.parlor.networking.protocol.GameProtocolRef
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PARTY_WIRE_VERSION
import com.parlor.networking.protocol.PartyAdmissionContext
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.ProtocolVersion
import com.parlor.networking.protocol.SessionEndReason
import com.parlor.networking.protocol.SessionEnvelopeHeader
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.room.SendTarget
import com.parlor.networking.room.SessionEndCommitStatus
import com.parlor.networking.transport.HostConfig
import com.parlor.networking.transport.HostedGameProtocol
import com.parlor.networking.transport.HostedPartyProtocol
import com.parlor.networking.transport.JoinConfig
import dev.p2pkit.core.AppId
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.system.exitProcess

/** Real-LAN, synthetic same-process credential retention; no mobile/process-death claim. */
object ResumeProcessEndpoint {
    private val game = HostedGameProtocol(GameId("process-fixture-game"), 1)

    private fun header(sequence: Long, id: String, partyId: PartyId?) = SessionEnvelopeHeader(
        protocol = if (partyId == null) ProtocolVersion() else PARTY_WIRE_VERSION,
        sessionId = SessionId("process-fixture-session"),
        gameId = game.gameId, gameVersion = game.gameVersion, messageId = id, sequence = sequence,
        partyId = partyId,
    )

    @JvmStatic
    fun main(args: Array<String>) {
        try {
            check(args.size in 2..3 && args[0] in setOf("host", "peer"))
            check(args.getOrNull(2)?.let { it in setOf("party", "invite") } ?: true)
            check(args[1].matches(Regex("[a-f0-9]{32}")))
            Executors.newFixedThreadPool(4).asCoroutineDispatcher().use { dispatcher ->
                executeEndpoint(args, dispatcher)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") failure: Throwable) {
            // Retain type only: no credential, endpoint, player state or exception text.
            println("ENDPOINT_FAIL type=${failure.javaClass.name}")
            exitProcess(1)
        }
    }

    private fun executeEndpoint(args: Array<String>, dispatcher: CoroutineDispatcher) = runBlocking {
        val scope = CoroutineScope(dispatcher + SupervisorJob())
        var room: LocalRoom? = null
        val party = if (args.getOrNull(2) == "party") {
            HostedPartyProtocol(PartyId(args[1]), game)
        } else {
            null
        }
        val inviteMode = args.getOrNull(2) == "invite"
        try {
            withTimeout(90_000) {
                val fixture = LoopbackDeviceFixture(dispatcher)
                val transport = fixture.transport(
                    AppId("com.parlor.p2p.process.${args[1]}"), args[0] + "-fixture", scope,
                )
                if (args[0] == "host") {
                    val result = transport.host(HostConfig(
                        "Process Fixture", maxRemotePlayers = 1,
                        gameProtocol = game.takeIf { party == null }, partyProtocol = party,
                    ))
                    check(result is Result.Success)
                    room = result.data
                    verifyHost(result.data, inviteMode)
                } else {
                    val config = joinInput(readln(), inviteMode, party)
                    val result = transport.join(config)
                    check(result is Result.Success)
                    room = result.data
                    if (inviteMode) println("PINNED_INVITE_JOIN_PASS")
                    verifyPeer(result.data, fixture, transport, party?.id) { room = it }
                }
            }
            println("ENDPOINT_PASS")
        } finally {
            withContext(NonCancellable) {
                try {
                    room?.leave()
                } finally {
                    scope.coroutineContext[Job]?.cancelAndJoin()
                }
            }
            println("TEARDOWN_PASS")
        }
    }

    private fun joinInput(raw: String, inviteMode: Boolean, party: HostedPartyProtocol?): JoinConfig {
        if (!inviteMode) {
            check(raw.matches(Regex("[A-Z0-9]{6}")))
            return JoinConfig(
                raw, "process-peer", expectedGameProtocol = game.takeIf { party == null },
                party = party?.let { PartyAdmissionContext(it.id, listOf(GameProtocolRef(game.gameId, game.gameVersion))) },
            )
        }
        val decoded = JoinLinkCodec.decode(raw)
        check(decoded is JoinLinkCodec.Decoded.Valid && decoded.invitation.game == game)
        return JoinConfig(decoded.invitation.code, "process-peer", expectedGameProtocol = game, invitation = decoded.invitation)
    }

    private suspend fun verifyHost(room: LocalRoom, inviteMode: Boolean) = coroutineScope {
        check(room.isHost)
        val partyId = room.info.value.partyId
        val admission = async(start = CoroutineStart.UNDISPATCHED) {
            val pending = withTimeout(35_000) { room.pendingAdmissions.first { it.size == 1 }.single() }
            check(pending.displayName == "process-peer" && !pending.isRejoin)
            check(room.approveAdmission(pending.playerId) is Result.Success)
            pending.playerId
        }
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(45_000) { room.incoming.first() }
        }
        if (inviteMode) {
            // Synthetic invite for the other owned process, in the bounded private fixture log.
            println("ROOM_INVITATION " + checkNotNull(room.createJoinLink()))
        } else {
            println("ROOM_CODE " + room.info.value.code)
        }
        System.out.flush()
        val player = admission.await()
        val message = first.await()
        check(message is PeerMessage.SessionHeartbeat && message.actor == player)
        check(message.header == header(0, "process-peer-heartbeat-0001", partyId))
        check(message.actor != PlayerId("forged-process-actor"))
        println("AUTHENTICATED_ACTOR_PASS")
        val match = SessionId("process-fixture-session")
        val roster = room.partyMatches?.freezeMatch(match, setOf(player)) ?: room.closeAdmissions()
        check(roster is Result.Success && roster.data.size == 1 && roster.data.count { it.playerId == player } == 1)
        println("FROZEN_ROSTER_PASS")
        val resumedInbound = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(45_000) { room.incoming.first() }
        }
        check(room.send(SendTarget.Direct(player), HostMessage.Heartbeat(
            header(1, "process-host-checkpoint-0001", partyId), 0,
        )) is Result.Success)
        withTimeout(15_000) { room.members.first { members -> members.none { it.playerId == player && it.connected } } }
        println("PHYSICAL_CLOSE_PASS")
        val resumed = resumedInbound.await()
        check(resumed is PeerMessage.SessionHeartbeat && resumed.actor == player)
        check(resumed.header == header(2, "process-peer-heartbeat-0002", partyId))
        withTimeout(10_000) { room.members.first { members -> members.any { it.playerId == player && it.connected } } }
        check(room.members.value.size == 1 && room.pendingAdmissions.value.isEmpty())
        println("REJOIN_SAME_SEAT_PASS")
        val afterMatch = async(start = CoroutineStart.UNDISPATCHED) {
            if (partyId != null) withTimeout(15_000) { room.incoming.first() } else null
        }
        check(room.send(SendTarget.Direct(player), HostMessage.SessionEnded(
            header(3, "process-host-terminal-0001", partyId), SessionEndReason.Cancelled, 0,
        )) is Result.Success)
        room.partyMatches?.let { leases ->
            val continuation = afterMatch.await()
            check(continuation is PeerMessage.SessionHeartbeat && continuation.actor == player)
            check(continuation.header == header(0, "process-after-match-0001", partyId))
            check(leases.reopenMatch(match) is Result.Success)
            val next = SessionId("process-fixture-session-2")
            val second = leases.freezeMatch(next, setOf(player))
            check(second is Result.Success && second.data.single().playerId == player)
            check(leases.reopenMatch(next) is Result.Success)
            println("PARTY_SAME_ROOM_REOPEN_PASS")
            check(room.send(SendTarget.Direct(player), HostMessage.Heartbeat(
                header(1, "process-after-match-0002", partyId), 0,
            )) is Result.Success)
        }
        withTimeout(15_000) { room.members.first { members -> members.none { it.playerId == player && it.connected } } }
        println("PEER_DISCONNECT_PASS")
    }

    private suspend fun verifyPeer(
        room: LocalRoom,
        fixture: LoopbackDeviceFixture,
        transport: P2pKitRoomTransport,
        partyId: PartyId?,
        onRoom: (LocalRoom) -> Unit,
    ) = coroutineScope {
        check(!room.isHost)
        val store = ResumableCredentialStore(fixture.secureStorage)
        val loaded = store.loadResumeCandidate()
        check(loaded is Result.Success && loaded.data != null)
        val before = checkNotNull(loaded.data)
        val selection = if (partyId == null) {
            val summary = transport.resumableSession()
            check(summary is Result.Success)
            checkNotNull(summary.data?.deletionSelection)
        } else {
            check(transport.resumableSession() == Result.Success(null))
            val summary = transport.resumablePartySession()
            check(summary is Result.Success && summary.data?.partyId == partyId)
            checkNotNull(summary.data?.selection)
        }
        val checkpoint = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(15_000) { room.incoming.first() }
        }
        check(room.sendToHost(PeerMessage.SessionHeartbeat(
            header(0, "process-peer-heartbeat-0001", partyId), PlayerId("forged-process-actor"), 0,
        )) is Result.Success)
        val received = checkpoint.await()
        check(received is HostMessage.Heartbeat && received.header == header(1, "process-host-checkpoint-0001", partyId))
        check(room.closeForRetry() is Result.Success)
        // Test runner synchronizes only after the separate host observed closure.
        check(readln() == "CLOSE_OBSERVED")
        val creations = fixture.realKitCreationAttempts
        check(transport.resumeLastSession(HostedGameProtocol(GameId("different-game"), 1), selection) ==
            Result.Failure(NetError.IncompatibleProtocol))
        check(fixture.realKitCreationAttempts == creations && store.loadResumeCandidate() == Result.Success(before))
        println("SELECTED_GAME_REJECTION_PASS")
        check(transport.resumeLastSession(HostedGameProtocol(game.gameId, 2), selection) ==
            Result.Failure(NetError.IncompatibleProtocol))
        check(fixture.realKitCreationAttempts == creations && store.loadResumeCandidate() == Result.Success(before))
        println("SELECTED_VERSION_REJECTION_PASS")
        val result = if (partyId == null) {
            transport.resumeLastSession(game, selection)
        } else {
            val wrongId = PartyId(if (partyId.raw.first() == 'a') "b".repeat(32) else "a".repeat(32))
            check(transport.resumeParty(wrongId, selection) == Result.Failure(NetError.IncompatibleProtocol))
            check(fixture.realKitCreationAttempts == creations)
            println("EXACT_PARTY_SCOPE_PASS")
            transport.resumeParty(partyId, selection)
        }
        check(result is Result.Success)
        val resumed = result.data
        onRoom(resumed)
        check(resumed.selfPlayerId == room.selfPlayerId && fixture.realKitCreationAttempts == creations + 1)
        println("SAME_SEAT_RESUME_PASS")
        val rotatedResult = store.loadResumeCandidate()
        check(rotatedResult is Result.Success)
        val rotated = checkNotNull(rotatedResult.data)
        check(rotated.membershipId == before.membershipId && rotated.playerId == before.playerId)
        check(rotated.generation > before.generation && rotated.secret != before.secret)
        if (partyId == null) {
            check(rotated.gameId == game.gameId.raw && rotated.gameVersion == game.gameVersion)
        } else {
            check(rotated.partyId == partyId && rotated.roomId == before.roomId && rotated.gameId == null)
        }
        println("ROTATED_CREDENTIAL_PASS")
        val terminal = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(15_000) { resumed.incoming.first() }
        }
        check(resumed.sendToHost(PeerMessage.SessionHeartbeat(
            header(2, "process-peer-heartbeat-0002", partyId), PlayerId("forged-process-actor"), 0,
        )) is Result.Success)
        val response = terminal.await()
        check(response is HostMessage.SessionEnded && response.header == header(3, "process-host-terminal-0001", partyId))
        check(response.reason == SessionEndReason.Cancelled && response.finalRevision == 0L)
        println("BIDIRECTIONAL_PASS")
        check(resumed.commitValidatedSessionEnd(response) == Result.Success(SessionEndCommitStatus.Committed))
        if (partyId != null) {
            check(resumed.commitValidatedSessionEnd(response) == Result.Success(SessionEndCommitStatus.NotOwned))
            check(store.loadResumeCandidate() == Result.Success(rotated))
            println("PARTY_MATCH_END_RETAINS_MEMBERSHIP_PASS")
            val followUp = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(15_000) { resumed.incoming.first() }
            }
            check(resumed.sendToHost(PeerMessage.SessionHeartbeat(
                header(0, "process-after-match-0001", partyId), resumed.selfPlayerId, 0,
            )) is Result.Success)
            val accepted = followUp.await()
            check(accepted is HostMessage.Heartbeat && accepted.header == header(1, "process-after-match-0002", partyId))
            check(resumed.finalLeave() is Result.Success)
        }
        check(store.loadResumeCandidate() == Result.Success(null))
        check(transport.resumeLastSession(game, selection) == Result.Failure(NetError.NotConnected))
        check(fixture.realKitCreationAttempts == creations + 1)
        println("REVOKED_MEMBERSHIP_PASS")
    }
}
