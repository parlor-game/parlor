package com.parlor.session.lobby

import com.parlor.core.ids.PartyId
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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
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
    override val incoming: Flow<RoomMessage>,
    scope: CoroutineScope,
) : LocalRoom {
    private val observers = CoroutineScope(scope.coroutineContext + Job(scope.coroutineContext[Job]))
    private val closed = MutableStateFlow(false)
    private val remotePlayers = match.roster - physical.selfPlayerId

    val protocol = SessionProtocol(match.id, match.game.gameId, match.game.gameVersion, PARTY_WIRE_VERSION, partyId = partyId)

    override val info get() = physical.info
    override val isHost get() = physical.isHost
    override val selfPlayerId get() = physical.selfPlayerId
    override val foregroundReady get() = physical.foregroundReady
    override val acceptsLocalGameCommands get() = !closed.value && physical.acceptsLocalGameCommands
    override val acceptsRemoteGameCommands get() = !closed.value && physical.acceptsRemoteGameCommands

    override val members = physical.members.map(::matchMembers)
        .stateIn(observers, SharingStarted.Eagerly, matchMembers(physical.members.value))

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
            is PeerEvent.PeerJoined -> event.playerId in remotePlayers
            is PeerEvent.PeerLeft -> event.playerId in remotePlayers
            is PeerEvent.PeerReconnected -> event.playerId in remotePlayers
            is PeerEvent.AdmissionRequested -> false
            else -> true
        }
    }.shareIn(observers, SharingStarted.Eagerly)

    override fun registerForegroundValidator(validator: ForegroundConnectionValidator): () -> Unit =
        if (closed.value) ({}) else physical.registerForegroundValidator(validator)

    override suspend fun send(target: SendTarget, message: HostMessage): Result<Unit, NetError> {
        if (!isHost || !canSend(message)) return Result.Failure(NetError.Unauthorized)
        return when (target) {
            is SendTarget.Direct -> if (target.playerId in remotePlayers) {
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

    private fun matchMembers(members: List<RoomMember>): List<RoomMember> =
        members.filter { it.playerId in match.roster }.map { it.copy(partySupportedGames = emptyList()) }

    private suspend fun sendToRoster(message: HostMessage): Result<Unit, NetError> {
        var delivered = false
        var failure: Result.Failure<NetError>? = null
        for (player in remotePlayers) {
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
        ): PartyMatchRoom? {
            val partyId = physical.info.value.partyId ?: return null
            val incoming = inbox.openMatch(match) ?: return null
            return PartyMatchRoom(physical, partyId, match, inbox, incoming, scope)
        }
    }
}
