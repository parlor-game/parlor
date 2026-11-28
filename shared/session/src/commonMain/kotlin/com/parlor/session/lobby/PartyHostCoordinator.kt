package com.parlor.session.lobby

import com.parlor.core.ids.PartyId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.result.Result
import com.parlor.networking.protocol.CommandStatus
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PARTY_WIRE_VERSION
import com.parlor.networking.protocol.PartyLobbyPhase
import com.parlor.networking.protocol.PartyProtocolHeader
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.PublicPartyLobby
import com.parlor.networking.protocol.PublicPartyPlaylist
import com.parlor.networking.protocol.PublicPartySeat
import com.parlor.networking.room.NetError
import com.parlor.networking.room.PartyMatchAdmission
import com.parlor.networking.room.HostedPartyCheckpoint
import com.parlor.networking.room.HostedPartyCursor
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class PartyReadyCommand(
    val partyId: PartyId,
    val sequence: Long,
    val expectedRevision: Long,
    val ready: Boolean,
)

internal data class PartyReadyReceipt(
    val status: CommandStatus,
    val revision: Long,
    val nextSequence: Long,
)

/** Serializes the host's party operations. Peer readiness never mutates another member. */
internal class PartyHostCoordinator(
    initial: PartyLobby,
    private val matchAdmission: PartyMatchAdmission? = null,
    initialCursors: List<HostedPartyCursor> = emptyList(),
) {
    init { require(matchAdmission == null || matchAdmission.partyId == initial.id) }

    private val mutations = Mutex()
    private val lobby = MutableStateFlow(initial)
    private val nextByPlayer = initialCursors.associate { it.player to it.nextSequence }.toMutableMap()
    val state = lobby.asStateFlow()

    suspend fun admit(seat: PartySeat) = mutate { it.admit(seat) }
    suspend fun saveCurrent(): Result<Unit, NetError> = mutations.withLock {
        matchAdmission?.saveCheckpoint(lobby.value.checkpoint()) ?: Result.Success(Unit)
    }

    suspend fun configurePlaylist(games: List<PartyGame>) = mutate { it.configurePlaylist(games) }
    suspend fun selectGame(game: PartyGame): Result<PartyLobby, PartyRejection> = mutations.withLock {
        val previous = lobby.value
        val selected = previous.selectGame(game)
        if (selected !is Result.Success || matchAdmission == null || selected.data === previous) {
            return@withLock apply(selected)
        }
        withContext(NonCancellable) {
            when (val switched = matchAdmission.selectGame(previous.game.protocol, game.protocol, game.playerCounts.last,
                previous.seats.keys - previous.host, selected.data.checkpoint())) {
                is Result.Failure -> Result.Failure(switched.error.toPartyRejection())
                is Result.Success -> apply(selected)
            }
        }
    }
    suspend fun presence(player: PlayerId, connected: Boolean) = mutate { it.updatePresence(player, connected) }
    suspend fun prepareMatch(): Result<PartyLobby, PartyRejection> = mutations.withLock {
        val previous = lobby.value
        val start = previous.prepareMatch()
        if (start !is Result.Success || matchAdmission == null) return@withLock apply(start)
        val match = (start.data.phase as PartyPhase.Starting).match
        // Publish the host attempt before acquiring its room lease. If the
        // freeze fails, roll back through a new revision so its ID cannot be reused.
        lobby.value = start.data
        val fence = start.data.checkpoint()
        val frozen = withContext(NonCancellable) { matchAdmission.freezeMatch(match.id, match.roster - previous.host, fence) }
        if (frozen is Result.Failure) {
            apply(start.data.returnToLobby(match.id))
            return@withLock Result.Failure(frozen.error.toPartyRejection())
        }
        start
    }

    suspend fun commitMatch(match: SessionId) = mutate { it.commitMatch(match) }

    suspend fun returnToLobby(
        match: SessionId, completed: Boolean = false, winners: Set<PlayerId>? = null,
    ): Result<PartyLobby, PartyRejection> = mutations.withLock {
        val returned = lobby.value.returnToLobby(match, completed, winners)
        if (returned !is Result.Success || matchAdmission == null) return@withLock apply(returned)
        val reopened = withContext(NonCancellable) { matchAdmission.reopenMatch(match, returned.data.checkpoint()) }
        if (reopened is Result.Failure) return@withLock Result.Failure(reopened.error.toPartyRejection())
        apply(returned)
    }

    suspend fun removeSeat(player: PlayerId): Result<PartyLobby, PartyRejection> = mutations.withLock {
        commit(lobby.value.removeSeat(player)).also { result ->
            if (result is Result.Success) nextByPlayer.remove(player)
        }
    }

    suspend fun close() = mutations.withLock {
        lobby.value = lobby.value.endParty()
        nextByPlayer.clear()
    }

    /** This method is called with the identity of the authenticated physical channel. */
    suspend fun handleReady(
        actor: PlayerId,
        message: PeerMessage.PartySetReady,
        responseId: String,
    ): HostMessage.PartyCommandResult = mutations.withLock {
        val header = message.header
        val receipt = if (header.protocol == PARTY_WIRE_VERSION && header.sequence == 0L) {
            processReady(actor, PartyReadyCommand(header.partyId, message.clientSequence, message.expectedRevision, message.ready))
        } else {
            PartyReadyReceipt(CommandStatus.IncompatibleVersion, lobby.value.revision, nextByPlayer[actor] ?: FIRST_SEQUENCE)
        }
        HostMessage.PartyCommandResult(
            header = PartyProtocolHeader(PARTY_WIRE_VERSION, lobby.value.id, responseId, lobby.value.revision),
            commandMessageId = header.messageId,
            status = receipt.status,
            revision = receipt.revision,
            nextExpectedClientSequence = receipt.nextSequence,
        )
    }

    suspend fun snapshotFor(actor: PlayerId, messageId: String): HostMessage.PartySnapshot? = mutations.withLock {
        val current = lobby.value
        if (actor !in current.seats) return@withLock null
        HostMessage.PartySnapshot(
            header = PartyProtocolHeader(PARTY_WIRE_VERSION, current.id, messageId, current.revision),
            revision = current.revision,
            nextExpectedClientSequence = nextByPlayer[actor] ?: FIRST_SEQUENCE,
            lobby = current.publicLobby(),
        )
    }

    /**
     * Actor comes from the authenticated transport, never the frame. The single
     * per-seat high-water mark bounds memory and remains stable across reconnects.
     */
    suspend fun ready(actor: PlayerId, command: PartyReadyCommand): PartyReadyReceipt =
        mutations.withLock { processReady(actor, command) }

    private suspend fun processReady(actor: PlayerId, command: PartyReadyCommand): PartyReadyReceipt {
        val current = lobby.value
        val expected = nextByPlayer[actor] ?: FIRST_SEQUENCE
        val status = when {
            command.partyId != current.id -> CommandStatus.IncompatibleVersion
            current.phase == PartyPhase.Ended -> CommandStatus.SessionEnded
            current.seats[actor]?.member?.connected != true -> CommandStatus.Unauthorized
            command.sequence < FIRST_SEQUENCE -> CommandStatus.Unauthorized
            command.sequence < expected -> CommandStatus.Duplicate
            command.sequence > expected || expected >= Long.MAX_VALUE - 1 -> CommandStatus.SequenceGap
            else -> acceptOrderedReadiness(actor, command, expected)
        }
        return PartyReadyReceipt(status, lobby.value.revision, nextByPlayer[actor] ?: FIRST_SEQUENCE)
    }

    private suspend fun acceptOrderedReadiness(actor: PlayerId, command: PartyReadyCommand, expected: Long): CommandStatus {
        val previous = lobby.value
        val result = previous.setReady(actor, command.ready, command.expectedRevision)
        val proposed = (result as? Result.Success)?.data ?: previous
        return withContext(NonCancellable) {
            if (!save(proposed.checkpoint(cursors = nextByPlayer + (actor to expected + 1)))) {
                return@withContext CommandStatus.InvalidAction
            }
            apply(result)
            nextByPlayer[actor] = expected + 1
            when (result) {
                is Result.Success -> CommandStatus.Applied
                is Result.Failure -> when (result.error) {
                    PartyRejection.StaleRevision -> CommandStatus.StaleRevision
                    PartyRejection.InvalidPhase -> CommandStatus.InvalidAction
                    else -> CommandStatus.Unauthorized
                }
            }
        }
    }

    private suspend fun save(checkpoint: HostedPartyCheckpoint): Boolean =
        matchAdmission?.saveCheckpoint(checkpoint) !is Result.Failure

    private fun PartyLobby.checkpoint(cursors: Map<PlayerId, Long> = nextByPlayer): HostedPartyCheckpoint {
        val public = publicLobby()
        // A process cannot recover the private in-flight engine. Fence its match ID
        // and revision, then recover the already committed lobby progress.
        val safeLobby = public.copy(phase = PartyLobbyPhase.Lobby, activeMatchId = null,
            members = public.members.map { it.copy(ready = false, inActiveMatch = false) })
        return HostedPartyCheckpoint(revision, safeLobby, cursors.mapNotNull { (player, next) ->
            if (player != host && player in seats) HostedPartyCursor(player, next) else null
        })
    }

    private suspend fun mutate(change: (PartyLobby) -> Result<PartyLobby, PartyRejection>) =
        mutations.withLock { commit(change(lobby.value)) }

    private suspend fun commit(result: Result<PartyLobby, PartyRejection>): Result<PartyLobby, PartyRejection> =
        withContext(NonCancellable) {
            if (result is Result.Success && result.data !== lobby.value && !save(result.data.checkpoint())) {
                return@withContext Result.Failure(PartyRejection.StorageUnavailable)
            }
            apply(result)
        }

    private fun apply(result: Result<PartyLobby, PartyRejection>): Result<PartyLobby, PartyRejection> =
        result.also { if (it is Result.Success) lobby.value = it.data }

    private companion object {
        const val FIRST_SEQUENCE = 1L
    }
}

internal fun PartyLobby.publicLobby(): PublicPartyLobby {
    val (visiblePhase, match) = when (val status = phase) {
        PartyPhase.Lobby -> PartyLobbyPhase.Lobby to null
        is PartyPhase.Starting -> PartyLobbyPhase.MatchStarting to status.match
        is PartyPhase.Playing -> PartyLobbyPhase.InMatch to status.match
        PartyPhase.Ended -> PartyLobbyPhase.Ended to null
    }
    return PublicPartyLobby(
        phase = visiblePhase,
        selectedGame = game.protocol,
        members = seats.values.map { seat ->
            val player = seat.member
            PublicPartySeat(
                playerId = player.playerId,
                displayName = player.displayName,
                connected = player.connected,
                ready = seat.ready,
                inActiveMatch = match?.roster?.contains(player.playerId) == true,
            )
        },
        seatLimit = game.playerCounts.last,
        activeMatchId = match?.id,
        matchOrdinal = matchOrdinal,
        roomName = roomName,
        playlist = playlist?.let { progress ->
            PublicPartyPlaylist(
                progress.games.map(PartyGame::protocol),
                progress.results.map(PartyMatchResult::matchId),
                progress.results.mapNotNull(PartyMatchResult::score),
            )
        },
    )
}

private fun NetError.toPartyRejection(): PartyRejection = when (this) {
    NetError.NotConnected, NetError.RoomFull -> PartyRejection.IneligibleCount
    NetError.StaleRoom -> PartyRejection.StaleRevision
    NetError.CommandInFlight -> PartyRejection.WaitingForReadiness
    NetError.SecureStorageUnavailable -> PartyRejection.StorageUnavailable
    else -> PartyRejection.InvalidPhase
}
