package com.parlor.session.lobby

import com.parlor.core.ids.PartyId
import com.parlor.core.ids.PlayerId
import com.parlor.core.result.Result
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PARTY_WIRE_VERSION
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.RoomMessage
import com.parlor.networking.protocol.SessionProtocol
import com.parlor.networking.room.ForegroundConnectionValidator
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.room.PeerEvent
import com.parlor.networking.room.RoomLifecycleState
import com.parlor.networking.room.RoomMember
import com.parlor.networking.room.SendTarget
import com.parlor.networking.room.SessionEndCommitStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Gives the existing game coordinators only the routing and lifecycle of their frozen roster. */
internal class PartyMatchRoom private constructor(
    private val physical: LocalRoom,
    private val partyId: PartyId,
    private val match: PartyMatch,
    private val inbox: PartyRoomInbox,
    internal val frozenMembers: List<RoomMember>,
    override val incoming: Flow<RoomMessage>,
    scope: CoroutineScope,
) : LocalRoom {
    private val observers = CoroutineScope(scope.coroutineContext + Job(scope.coroutineContext[Job]))
    private val closed = MutableStateFlow(false)
    private val remotePlayers = match.roster - physical.selfPlayerId
    private val retired = MutableStateFlow(emptySet<PlayerId>())

    override val matchProtocol = SessionProtocol(match.id, match.game.gameId, match.game.gameVersion, PARTY_WIRE_VERSION, partyId = partyId)

    override val info get() = physical.info
    override val isHost get() = physical.isHost
    override val selfPlayerId get() = physical.selfPlayerId
    override val foregroundReady get() = physical.foregroundReady
    override val acceptsLocalGameCommands get() = !closed.value && physical.acceptsLocalGameCommands
    override val acceptsRemoteGameCommands get() = !closed.value && physical.acceptsRemoteGameCommands

    override val members = combine(physical.members, retired, ::matchMembers)
        .stateIn(observers, SharingStarted.Eagerly, matchMembers(physical.members.value, emptySet()))

    private val matchLifecycle = MutableStateFlow(physical.lifecycle.value)
    override val lifecycle = matchLifecycle.asStateFlow()

    init {
        observers.launch {
            physical.lifecycle.collect { state ->
                matchLifecycle.update { current ->
                    if (current == RoomLifecycleState.Closed) current else state
                }
            }
        }
    }

    override val peerEvents = physical.peerEvents.filter { event ->
        when (event) {
            is PeerEvent.PeerJoined -> retainsSeat(event.playerId)
            is PeerEvent.PeerLeft -> retainsSeat(event.playerId)
            is PeerEvent.PeerReconnected -> retainsSeat(event.playerId)
            is PeerEvent.AdmissionRequested -> false
            else -> true
        }
    }.shareIn(observers, SharingStarted.Eagerly)

    override fun registerForegroundValidator(validator: ForegroundConnectionValidator): () -> Unit =
        if (closed.value) ({}) else physical.registerForegroundValidator(validator)

    override suspend fun send(target: SendTarget, message: HostMessage): Result<Unit, NetError> {
        if (!isHost || !canSend(message)) return Result.Failure(NetError.Unauthorized)
        return when (target) {
            is SendTarget.Direct -> if (retainsSeat(target.playerId)) {
                physical.send(target, message)
            } else {
                Result.Failure(NetError.Unauthorized)
            }
            SendTarget.Broadcast -> when (message) {
                // Per-player data, including command receipts, cannot broadcast.
                is HostMessage.PlayerSnapshot, is HostMessage.CommandResult -> Result.Failure(NetError.Unauthorized)
                else -> sendToRoster(message)
            }
        }
    }

    override suspend fun sendToHost(message: PeerMessage): Result<Unit, NetError> =
        if (!isHost && canSend(message) && message.matchActor() == selfPlayerId) {
            physical.sendToHost(message)
        } else {
            Result.Failure(NetError.Unauthorized)
        }

    override suspend fun commitValidatedSessionEnd(
        message: HostMessage.SessionEnded,
    ): Result<SessionEndCommitStatus, NetError> =
        if (!isHost && canSend(message)) physical.commitValidatedSessionEnd(message)
        else Result.Success(SessionEndCommitStatus.NotOwned)

    override suspend fun retireDisconnectedMember(playerId: PlayerId): Result<Unit, NetError> {
        if (!isHost || closed.value || playerId !in remotePlayers) return Result.Failure(NetError.Unauthorized)
        if (playerId in retired.value) return Result.Success(Unit)
        if (physical.members.value.any { it.playerId == playerId && it.connected }) {
            return Result.Failure(NetError.AlreadyConnected)
        }
        return withContext(NonCancellable) {
            if (!inbox.retireMatchSeat(match.id, playerId)) {
                Result.Failure(NetError.NotConnected)
            } else {
                retired.update { it + playerId }
                Result.Success(Unit)
            }
        }
    }

    override suspend fun leave() {
        if (closed.compareAndSet(expect = false, update = true)) {
            try {
                withContext(NonCancellable) { inbox.closeMatch(match.id) }
            } finally {
                matchLifecycle.value = RoomLifecycleState.Closed
                observers.cancel()
            }
        }
    }

    private fun canSend(message: RoomMessage): Boolean =
        !closed.value && message.matchHeader()?.belongsTo(partyId, match) == true

    private fun retainsSeat(player: PlayerId): Boolean = player in remotePlayers && player !in retired.value

    private fun matchMembers(members: List<RoomMember>, retiredSeats: Set<PlayerId>): List<RoomMember> =
        members.filter { it.playerId in match.roster && it.playerId !in retiredSeats }
            .map { it.copy(partySupportedGames = emptyList()) }

    private suspend fun sendToRoster(message: HostMessage): Result<Unit, NetError> {
        var delivered = false
        var failure: Result.Failure<NetError>? = null
        for (player in remotePlayers - retired.value) {
            when (val result = physical.send(SendTarget.Direct(player), message)) {
                is Result.Success -> delivered = true
                is Result.Failure -> if (result.error != NetError.NotConnected && failure == null) failure = result
            }
        }
        return failure ?: if (delivered) Result.Success(Unit) else Result.Failure(NetError.NotConnected)
    }

    companion object {
        suspend fun attach(
            physical: LocalRoom,
            match: PartyMatch,
            inbox: PartyRoomInbox,
            scope: CoroutineScope,
            authoritativeMembers: List<RoomMember> = emptyList(),
        ): PartyMatchRoom? {
            val partyId = physical.info.value.partyId ?: return null
            val incoming = inbox.openMatch(match) ?: return null
            val frozen = authoritativeMembers.filter { it.playerId in match.roster && it.playerId != physical.selfPlayerId }
                .map { it.copy(partySupportedGames = emptyList()) }
            return PartyMatchRoom(physical, partyId, match, inbox, frozen, incoming, scope)
        }
    }
}
