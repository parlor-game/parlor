package com.parlor.session.lobby

import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.result.Result
import com.parlor.networking.protocol.GameProtocolRef
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.room.PeerEvent
import com.parlor.networking.room.RoomMember
import com.parlor.networking.room.SendTarget
import com.parlor.networking.security.SecureIds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Owns the host party until an explicit party-level leave. Match rooms can come and go independently. */
internal class PartyHostRoomBinding(
    private val room: LocalRoom,
    initialGame: PartyGame,
    supportedGames: Set<GameProtocolRef>,
    parentScope: CoroutineScope,
    private val messageId: () -> String = SecureIds::id128,
    roomName: String? = null,
) {
    private val admissions = checkNotNull(room.partyMatches)
    private val hostId = room.selfPlayerId

    init {
        require(room.isHost && room.info.value.partyId == admissions.partyId && room.info.value.hostPlayerId == hostId)
    }

    private val scope = CoroutineScope(parentScope.coroutineContext + Job(parentScope.coroutineContext[Job]))
    private val transactions = Mutex()
    private val publishing = Mutex()
    private var broadcastRevision = 0L
    private val host = PartyHostCoordinator(
        initial = PartyLobby.open(
            admissions.partyId,
            PartySeat(RoomMember(hostId, room.info.value.hostDisplayName, connected = true), supportedGames),
            initialGame,
            roomName,
        ),
        matchAdmission = admissions,
    )
    private val inbox = PartyRoomInbox(admissions.partyId, hostId, true, room.incoming, scope)
    private var active: Pair<SessionId, PartyMatchRoom>? = null
    val state = host.state
    val pending = room.pendingAdmissions

    init {
        scope.launch {
            room.members.collect { synchronizeMembers() }
        }
        scope.launch {
            host.state.collect {
                publishState()
            }
        }
        scope.launch {
            room.peerEvents.collect { event ->
                when (event) {
                    is PeerEvent.PeerJoined -> publishState(event.playerId)
                    is PeerEvent.PeerReconnected -> publishState(event.playerId)
                    else -> Unit
                }
            }
        }
        scope.launch {
            inbox.controls.collect { message ->
                if (message is PeerMessage.PartySetReady) receiveReady(message)
            }
        }
    }

    suspend fun approve(player: PlayerId): Result<Unit, NetError> =
        if (state.value.phase == PartyPhase.Lobby) room.approveAdmission(player)
        else Result.Failure(NetError.SessionStarted)

    suspend fun reject(player: PlayerId): Result<Unit, NetError> = room.rejectAdmission(player)

    suspend fun selectGame(game: PartyGame): Result<PartyLobby, PartyRejection> = transactions.withLock {
        if (!room.acceptsLocalGameCommands) return@withLock Result.Failure(PartyRejection.InvalidPhase)
        reconcileMembers()
        host.selectGame(game)
    }

    suspend fun prepareMatch(): Result<PartyMatchRoom, PartyRejection> {
        val match = transactions.withLock {
            if (!room.acceptsLocalGameCommands) return@withLock Result.Failure(PartyRejection.InvalidPhase)
            reconcileMembers()
            when (val prepared = host.prepareMatch()) {
                is Result.Failure -> prepared
                is Result.Success -> attachMatch(prepared.data)
            }
        }
        // Peers need the scoped roster before the game runtime sends its first start offer.
        if (match is Result.Success) publishState()
        return match
    }

    suspend fun commitMatch(match: SessionId): Result<PartyLobby, PartyRejection> = transactions.withLock {
        if (active?.first != match) return@withLock Result.Failure(PartyRejection.StaleMatch)
        reconcileMembers()
        host.commitMatch(match)
    }

    suspend fun returnToLobby(match: SessionId): Result<PartyLobby, PartyRejection> = transactions.withLock {
        if (active?.first != match) return@withLock Result.Failure(PartyRejection.StaleMatch)
        withContext(NonCancellable) {
            host.returnToLobby(match).also { result ->
                if (result is Result.Success) {
                    active?.second?.leave()
                    active = null
                }
            }
        }
    }

    suspend fun leave(): Result<Unit, NetError> = publishing.withLock {
        transactions.withLock {
            withContext(NonCancellable) {
                if (state.value.phase != PartyPhase.Ended) {
                    host.close()
                    publishTerminal()
                }
                room.finalLeave().also { result ->
                    if (result is Result.Success) {
                        active?.second?.leave()
                        active = null
                        inbox.close()
                        scope.cancel()
                    }
                }
            }
        }
    }

    private suspend fun publishTerminal() {
        // A reachable peer can revoke its credential immediately. When delivery
        // is impossible the transport's existing bounded rejoin expiry owns cleanup.
        val changes = snapshotFrames()
        withTimeoutOrNull(TERMINAL_DELIVERY_MS) {
            coroutineScope {
                changes.forEach { (recipient, message) ->
                    launch { room.send(recipient, message) }
                }
            }
        }
    }

    private suspend fun attachMatch(prepared: PartyLobby): Result<PartyMatchRoom, PartyRejection> =
        withContext(NonCancellable) {
            val match = (prepared.phase as PartyPhase.Starting).match
            val matchRoom = PartyMatchRoom.attach(room, match, inbox, scope, prepared.seats.values.map(PartySeat::member))
            if (matchRoom == null) {
                host.returnToLobby(match.id)
                Result.Failure(PartyRejection.InvalidPhase)
            } else {
                active = match.id to matchRoom
                Result.Success(matchRoom)
            }
        }

    private suspend fun synchronizeMembers() = transactions.withLock { reconcileMembers() }

    /** Only the transport's confirmed authenticated seats can become party members. */
    private suspend fun reconcileMembers() {
        val observed = room.members.value.associateBy(RoomMember::playerId)
        for ((player, seat) in state.value.seats) {
            if (player == hostId) continue
            val current = observed[player]
            when {
                current == null -> host.removeSeat(player)
                current.connected != seat.member.connected -> host.presence(player, current.connected)
            }
        }
        for (member in observed.values) {
            if (member.playerId == hostId || member.playerId in state.value.seats) continue
            if (member.partySupportedGames.isEmpty()) continue
            host.admit(PartySeat(member, member.partySupportedGames.toSet()))
        }
    }

    private suspend fun receiveReady(message: PeerMessage.PartySetReady) {
        val receipt = transactions.withLock {
            reconcileMembers()
            if (room.acceptsRemoteGameCommands) host.handleReady(message.actor, message, messageId()) else null
        } ?: return
        withTimeoutOrNull(CONTROL_DELIVERY_MS) { room.send(SendTarget.Direct(message.actor), receipt) }
        // A rejected command can consume a client sequence without changing the lobby revision.
        publishState(message.actor)
    }

    private suspend fun publishState(onePlayer: PlayerId? = null) = publishing.withLock {
        val (revision, messages) = transactions.withLock {
            val current = state.value.revision
            if (onePlayer == null && current <= broadcastRevision) null else current to snapshotFrames(onePlayer)
        } ?: return@withLock
        val delivered = coroutineScope {
            messages.map { (recipient, message) ->
                async {
                    withTimeoutOrNull(CONTROL_DELIVERY_MS) { room.send(recipient, message) } is Result.Success
                }
            }.awaitAll()
        }
        if (onePlayer == null && delivered.all { it }) broadcastRevision = revision
    }

    private suspend fun snapshotFrames(onePlayer: PlayerId? = null) =
        state.value.seats.values.mapNotNull { seat ->
            val recipient = seat.member.playerId
            if (recipient == hostId || !seat.member.connected || (onePlayer != null && onePlayer != recipient)) {
                null
            } else {
                host.snapshotFor(recipient, messageId())?.let { SendTarget.Direct(recipient) to it }
            }
        }

    private companion object {
        const val TERMINAL_DELIVERY_MS = 1_500L
        const val CONTROL_DELIVERY_MS = 2_000L
    }
}
