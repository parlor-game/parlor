package com.parlor.session.lobby

import com.parlor.core.ids.PartyId
import com.parlor.core.result.Result
import com.parlor.networking.protocol.CommandStatus
import com.parlor.networking.protocol.GameProtocolRef
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PARTY_WIRE_VERSION
import com.parlor.networking.protocol.PartyLobbyPhase
import com.parlor.networking.protocol.PartyProtocolHeader
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.room.RoomLifecycleState
import com.parlor.networking.security.SecureIds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Installs only host-authored lobby state. Readiness is never predicted locally. */
internal class PartyPeerRoomBinding(
    private val room: LocalRoom,
    private val supportedGames: Set<GameProtocolRef>,
    parentScope: CoroutineScope,
    private val messageId: () -> String = SecureIds::id128,
) {
    private val partyId: PartyId = checkNotNull(room.info.value.partyId)
    private val hostId = room.info.value.hostPlayerId
    private val self = room.selfPlayerId

    init {
        require(!room.isHost && hostId != self && supportedGames.isNotEmpty())
    }

    private val scope = CoroutineScope(parentScope.coroutineContext + Job(parentScope.coroutineContext[Job]))
    private val transactions = Mutex()
    private val inbox = PartyRoomInbox(partyId, self, false, room.incoming, scope)
    private val installed = MutableStateFlow<HostMessage.PartySnapshot?>(null)
    private val active = MutableStateFlow<PartyMatchRoom?>(null)
    private var pending: ReadinessRequest? = null
    private var nextSequence = 1L
    private var ended = false
    val state = installed.asStateFlow()
    val match = active.asStateFlow()

    init {
        scope.launch {
            inbox.controls.collect { message ->
                when (message) {
                    is HostMessage.PartySnapshot -> if (install(message)) leave() else Unit
                    is HostMessage.PartyCommandResult -> acknowledge(message)
                    else -> Unit
                }
            }
        }
        scope.launch {
            room.lifecycle.collect {
                if (it == RoomLifecycleState.Expired || it == RoomLifecycleState.Closed) {
                    transactions.withLock { clearActiveMatch() }
                }
            }
        }
    }

    suspend fun setReady(ready: Boolean): Result<CommandStatus, NetError> {
        val request = transactions.withLock { prepareReadiness(ready) }
        if (request is Result.Failure) return request
        val queued = (request as Result.Success).data
        try {
            return withTimeoutOrNull(READINESS_TIMEOUT_MS) {
                when (val sent = room.sendToHost(queued.frame)) {
                    is Result.Success -> queued.receipt.await()
                    is Result.Failure -> sent
                }
            } ?: Result.Failure(NetError.Timeout)
        } finally {
            withContext(NonCancellable) {
                transactions.withLock { if (pending === queued) pending = null }
            }
        }
    }

    suspend fun leave(): Result<Unit, NetError> = transactions.withLock {
        withContext(NonCancellable) {
            room.finalLeave().also { result ->
                if (result is Result.Success) {
                    clearActiveMatch()
                    inbox.close()
                    scope.cancel()
                }
            }
        }
    }

    private fun prepareReadiness(ready: Boolean): Result<ReadinessRequest, NetError> {
        val snapshot = installed.value ?: return Result.Failure(NetError.NotConnected)
        if (ended || !room.acceptsLocalGameCommands) return Result.Failure(NetError.SessionSuspended)
        if (snapshot.lobby.phase != PartyLobbyPhase.Lobby) return Result.Failure(NetError.SessionStarted)
        if (pending != null) return Result.Failure(NetError.CommandInFlight)
        val frame = PeerMessage.PartySetReady(
            header = PartyProtocolHeader(PARTY_WIRE_VERSION, partyId, messageId(), 0),
            actor = self,
            clientSequence = nextSequence,
            expectedRevision = snapshot.revision,
            ready = ready,
        )
        return Result.Success(ReadinessRequest(frame).also { pending = it })
    }

    private suspend fun install(snapshot: HostMessage.PartySnapshot): Boolean = transactions.withLock {
        if (!canInstall(snapshot)) return@withLock false
        val public = snapshot.lobby
        val roster = public.members.filter { it.inActiveMatch }.map { it.playerId }.toSet()
        val proposedMatch = public.activeMatchId
        if (active.value?.protocol?.sessionId != proposedMatch || self !in roster) {
            clearActiveMatch()
        }
        if (proposedMatch != null && self in roster && active.value == null) {
            val ordinal = public.matchOrdinal + if (public.phase == PartyLobbyPhase.MatchStarting) 1 else 0
            val proposed = PartyMatch(proposedMatch, public.selectedGame, roster, ordinal)
            active.value = PartyMatchRoom.attach(room, proposed, inbox, scope) ?: return@withLock false
        }
        nextSequence = maxOf(nextSequence, snapshot.nextExpectedClientSequence)
        installed.value = snapshot
        ended = public.phase == PartyLobbyPhase.Ended
        ended
    }

    private fun canInstall(update: HostMessage.PartySnapshot): Boolean {
        val previous = installed.value
        if (ended || update.revision < 1 || update.header.sequence != update.revision ||
            update.nextExpectedClientSequence < 1
        ) return false
        if (update.lobby.selectedGame !in supportedGames ||
            update.lobby.members.none { it.playerId == self } ||
            update.lobby.members.none { it.playerId == hostId }
        ) return false
        if (previous == null) return true
        if (update.revision < previous.revision || update.lobby.matchOrdinal < previous.lobby.matchOrdinal) return false
        if (update.revision == previous.revision && update.lobby != previous.lobby) return false
        val match = active.value?.protocol
        return match == null || update.lobby.activeMatchId != match.sessionId ||
            (update.lobby.selectedGame.gameId == match.gameId && update.lobby.selectedGame.gameVersion == match.gameVersion)
    }

    private suspend fun acknowledge(result: HostMessage.PartyCommandResult) = transactions.withLock {
        val current = pending ?: return@withLock
        if (result.commandMessageId != current.frame.header.messageId || result.header.sequence != result.revision ||
            result.revision < current.frame.expectedRevision || result.nextExpectedClientSequence < 1
        ) return@withLock
        nextSequence = maxOf(nextSequence, result.nextExpectedClientSequence)
        current.receipt.complete(Result.Success(result.status))
    }

    private suspend fun clearActiveMatch() {
        active.value?.leave()
        active.value = null
    }

    private class ReadinessRequest(val frame: PeerMessage.PartySetReady) {
        val receipt = CompletableDeferred<Result<CommandStatus, NetError>>()
    }

    private companion object {
        const val READINESS_TIMEOUT_MS = 6_000L
    }
}
