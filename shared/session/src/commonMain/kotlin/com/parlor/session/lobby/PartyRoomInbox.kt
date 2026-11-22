package com.parlor.session.lobby

import com.parlor.core.ids.PartyId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PARTY_WIRE_VERSION
import com.parlor.networking.protocol.PartyProtocolHeader
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.RoomMessage
import com.parlor.networking.protocol.SessionEnvelopeHeader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Owns the only collector on the physical room inbox. The lobby and its
 * active game never compete for a message from the transport's channel.
 */
internal class PartyRoomInbox(
    private val partyId: PartyId,
    private val self: PlayerId,
    private val isHost: Boolean,
    incoming: Flow<RoomMessage>,
    scope: CoroutineScope,
) {
    private class MatchInbox(val match: PartyMatch) {
        val messages = Channel<RoomMessage>(MATCH_CAPACITY)
        val retired = mutableSetOf<PlayerId>()
    }

    private class ControlFrame(val message: RoomMessage) {
        val installed = if (message is HostMessage.PartySnapshot) CompletableDeferred<Unit>() else null
    }

    private val mutations = Mutex()
    private val control = Channel<ControlFrame>(CONTROL_CAPACITY)
    private var active: MatchInbox? = null
    private var closed = false
    val controls: Flow<RoomMessage> = flow {
        for (frame in control) {
            try {
                emit(frame.message)
            } finally {
                frame.installed?.complete(Unit)
            }
        }
    }

    private val collector = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        try {
            incoming.collect { message ->
                if (message.isPartyControl()) {
                    if (message.isControlForThisParty()) {
                        val frame = ControlFrame(message)
                        control.send(frame)
                        // Install the peer's match scope before routing any following game frame.
                        frame.installed?.await()
                    }
                } else {
                    mutations.withLock { routeMatch(message) }
                }
            }
        } finally {
            withContext(NonCancellable) {
                mutations.withLock {
                    closed = true
                    active?.messages?.cancel()
                    active = null
                    control.close()
                }
            }
        }
    }

    /** Returns a dedicated single-consumer stream for an accepted match. */
    suspend fun openMatch(match: PartyMatch): Flow<RoomMessage>? = mutations.withLock {
        if (closed || active != null || self !in match.roster) return@withLock null
        val inbox = MatchInbox(match)
        active = inbox
        inbox.messages.receiveAsFlow()
    }

    suspend fun retireMatchSeat(id: SessionId, player: PlayerId): Boolean = mutations.withLock {
        val current = active ?: return@withLock false
        if (!isHost || current.match.id != id || player == self || player !in current.match.roster) return@withLock false
        current.retired.add(player)
        true
    }

    suspend fun closeMatch(id: SessionId): Boolean = mutations.withLock {
        val inbox = active ?: return@withLock false
        if (inbox.match.id != id) return@withLock false
        // Cancel drops buffered old-match data before a replacement gets a stream.
        inbox.messages.cancel()
        active = null
        true
    }

    suspend fun close() {
        collector.cancel()
        collector.join()
    }

    private fun routeMatch(message: RoomMessage) {
        val inbox = active ?: return
        val header = message.matchHeader() ?: return
        if (!header.belongsTo(partyId, inbox.match)) return
        if (isHost != (message is PeerMessage)) return
        val actor = message.matchActor()
        if (isHost && (actor == null || actor !in inbox.match.roster || actor in inbox.retired)) return
        val accepted = inbox.messages.trySend(message)
        if (accepted.isFailure && !accepted.isClosed) {
            inbox.messages.close(IllegalStateException("Party match inbox exceeded capacity"))
        }
    }

    private fun RoomMessage.isPartyControl(): Boolean =
        this is HostMessage.PartySnapshot || this is HostMessage.PartyCommandResult || this is PeerMessage.PartySetReady

    private fun RoomMessage.isControlForThisParty(): Boolean {
        val header: PartyProtocolHeader = when (this) {
            is HostMessage.PartySnapshot -> if (isHost) return false else header
            is HostMessage.PartyCommandResult -> if (isHost) return false else header
            is PeerMessage.PartySetReady -> if (isHost) header else return false
            else -> return false
        }
        return header.partyId == partyId && header.protocol == PARTY_WIRE_VERSION
    }

    private companion object {
        const val CONTROL_CAPACITY = 16
        const val MATCH_CAPACITY = 32
    }
}

internal fun RoomMessage.matchHeader(): SessionEnvelopeHeader? = when (this) {
    is HostMessage.PlayerSnapshot -> header
    is HostMessage.CommandResult -> header
    is HostMessage.Heartbeat -> header
    is HostMessage.SessionEnded -> header
    is HostMessage.SessionStarting -> header
    is HostMessage.SessionStartCommitted -> header
    is PeerMessage.ClientCommand -> header
    is PeerMessage.CommandOutcomeRequest -> header
    is PeerMessage.SnapshotRequest -> header
    is PeerMessage.SessionHeartbeat -> header
    is PeerMessage.SessionStartCommitAck -> header
    is PeerMessage.SessionStartReady -> header
    else -> null
}

internal fun RoomMessage.matchActor(): PlayerId? = when (this) {
    is PeerMessage.ClientCommand -> actor
    is PeerMessage.CommandOutcomeRequest -> actor
    is PeerMessage.SnapshotRequest -> actor
    is PeerMessage.SessionHeartbeat -> actor
    is PeerMessage.SessionStartCommitAck -> actor
    is PeerMessage.SessionStartReady -> actor
    else -> null
}

internal fun SessionEnvelopeHeader.belongsTo(partyId: PartyId, match: PartyMatch): Boolean =
    this.partyId == partyId && protocol == PARTY_WIRE_VERSION &&
        sessionId == match.id && gameId == match.game.gameId && gameVersion == match.game.gameVersion
