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
import com.parlor.networking.protocol.PublicPartySeat
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
internal class PartyHostCoordinator(initial: PartyLobby) {
    private val mutations = Mutex()
    private val lobby = MutableStateFlow(initial)
    private val nextByPlayer = mutableMapOf<PlayerId, Long>()
    val state = lobby.asStateFlow()

    suspend fun admit(seat: PartySeat) = mutate { it.admit(seat) }
    suspend fun selectGame(game: PartyGame) = mutate { it.selectGame(game) }
    suspend fun presence(player: PlayerId, connected: Boolean) = mutate { it.updatePresence(player, connected) }
    suspend fun prepareMatch() = mutate { it.prepareMatch() }
    suspend fun commitMatch(match: SessionId) = mutate { it.commitMatch(match) }
    suspend fun returnToLobby(match: SessionId) = mutate { it.returnToLobby(match) }

    suspend fun removeSeat(player: PlayerId): Result<PartyLobby, PartyRejection> = mutations.withLock {
        apply(lobby.value.removeSeat(player)).also { result ->
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

    private fun processReady(actor: PlayerId, command: PartyReadyCommand): PartyReadyReceipt {
        val current = lobby.value
        val expected = nextByPlayer[actor] ?: FIRST_SEQUENCE
        val status = when {
            command.partyId != current.id -> CommandStatus.IncompatibleVersion
            current.phase == PartyPhase.Ended -> CommandStatus.SessionEnded
            current.seats[actor]?.member?.connected != true -> CommandStatus.Unauthorized
            command.sequence < FIRST_SEQUENCE -> CommandStatus.Unauthorized
            command.sequence < expected -> CommandStatus.Duplicate
            command.sequence > expected || expected == Long.MAX_VALUE -> CommandStatus.SequenceGap
            else -> acceptOrderedReadiness(actor, command, expected)
        }
        return PartyReadyReceipt(status, lobby.value.revision, nextByPlayer[actor] ?: FIRST_SEQUENCE)
    }

    private fun acceptOrderedReadiness(actor: PlayerId, command: PartyReadyCommand, expected: Long): CommandStatus {
        val result = apply(lobby.value.setReady(actor, command.ready, command.expectedRevision))
        nextByPlayer[actor] = expected + 1
        return when (result) {
            is Result.Success -> CommandStatus.Applied
            is Result.Failure -> when (result.error) {
                PartyRejection.StaleRevision -> CommandStatus.StaleRevision
                PartyRejection.InvalidPhase -> CommandStatus.InvalidAction
                else -> CommandStatus.Unauthorized
            }
        }
    }

    private suspend fun mutate(change: (PartyLobby) -> Result<PartyLobby, PartyRejection>) =
        mutations.withLock { apply(change(lobby.value)) }

    private fun apply(result: Result<PartyLobby, PartyRejection>): Result<PartyLobby, PartyRejection> =
        result.also { if (it is Result.Success) lobby.value = it.data }

    private companion object {
        const val FIRST_SEQUENCE = 1L
    }
}

private fun PartyLobby.publicLobby(): PublicPartyLobby {
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
    )
}
