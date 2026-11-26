package com.parlor.session.lobby

import com.parlor.core.ids.PartyId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.result.Result
import com.parlor.networking.protocol.GameProtocolRef
import com.parlor.networking.protocol.MAX_PARTY_PLAYLIST_MATCHES
import com.parlor.networking.room.RoomMember

data class PartyGame(val protocol: GameProtocolRef, val playerCounts: IntRange) {
    init {
        require(protocol.isValid())
        require(!playerCounts.isEmpty() && playerCounts.first >= 2 && playerCounts.last <= MAX_MATCH_PLAYERS)
    }
}

/** Host-only receipt for a verified finished match. Per-game scoring is separate. */
data class PartyMatchResult(val matchId: SessionId, val game: GameProtocolRef)

@ConsistentCopyVisibility
data class PartyPlaylist internal constructor(
    val games: List<PartyGame>,
    val results: List<PartyMatchResult> = emptyList(),
) {
    val nextGame: PartyGame? get() = games.getOrNull(results.size)
}

internal data class PartySeat(
    val member: RoomMember,
    val capabilities: Set<GameProtocolRef>,
    val ready: Boolean = false,
) {
    init {
        require(capabilities.isNotEmpty() && capabilities.size <= MAX_CAPABILITIES)
        require(capabilities.all(GameProtocolRef::isValid))
        require(capabilities.map(GameProtocolRef::gameId).distinct().size == capabilities.size)
    }

    companion object {
        private const val MAX_CAPABILITIES = 16
    }
}

internal data class PartyMatch(
    val id: SessionId,
    val game: GameProtocolRef,
    val roster: Set<PlayerId>,
    val ordinal: Long,
)

internal sealed interface PartyPhase {
    data object Lobby : PartyPhase
    data class Starting(val match: PartyMatch) : PartyPhase
    data class Playing(val match: PartyMatch) : PartyPhase
    data object Ended : PartyPhase
}

enum class PartyRejection {
    InvalidPhase,
    UnknownPlayer,
    DuplicatePlayer,
    IneligibleCount,
    IncompatibleGame,
    WaitingForReadiness,
    StaleRevision,
    StaleMatch,
}

@ConsistentCopyVisibility
internal data class PartyLobby private constructor(
    val id: PartyId,
    val host: PlayerId,
    val game: PartyGame,
    val seats: Map<PlayerId, PartySeat>,
    val revision: Long,
    val matchOrdinal: Long,
    val phase: PartyPhase,
    val roomName: String?,
    val playlist: PartyPlaylist? = null,
) {
    fun admit(seat: PartySeat): Result<PartyLobby, PartyRejection> {
        val player = seat.member.playerId
        return when {
            phase != PartyPhase.Lobby -> Result.Failure(PartyRejection.InvalidPhase)
            player in seats -> Result.Failure(PartyRejection.DuplicatePlayer)
            seats.size >= game.playerCounts.last -> Result.Failure(PartyRejection.IneligibleCount)
            game.protocol !in seat.capabilities -> Result.Failure(PartyRejection.IncompatibleGame)
            else -> Result.Success(copy(
                seats = seats + (player to seat.copy(capabilities = seat.capabilities.toSet(), ready = false)),
                revision = revision + 1,
            ))
        }
    }

    fun selectGame(selection: PartyGame): Result<PartyLobby, PartyRejection> = when {
        phase != PartyPhase.Lobby -> Result.Failure(PartyRejection.InvalidPhase)
        playlist?.nextGame?.let { it != selection } == true -> Result.Failure(PartyRejection.InvalidPhase)
        selection == game -> Result.Success(this)
        seats.size > selection.playerCounts.last -> Result.Failure(PartyRejection.IneligibleCount)
        seats.values.any { selection.protocol !in it.capabilities } -> Result.Failure(PartyRejection.IncompatibleGame)
        else -> Result.Success(copy(game = selection, seats = seats.resetReadiness(), revision = revision + 1))
    }

    fun configurePlaylist(games: List<PartyGame>): Result<PartyLobby, PartyRejection> = when {
        phase != PartyPhase.Lobby || playlist?.let { it.results.isNotEmpty() && it.nextGame != null } == true ->
            Result.Failure(PartyRejection.InvalidPhase)
        games.size !in 1..MAX_PARTY_PLAYLIST_MATCHES -> Result.Failure(PartyRejection.IncompatibleGame)
        games.any { seats.size !in it.playerCounts } -> Result.Failure(PartyRejection.IneligibleCount)
        games.any { game -> seats.values.any { game.protocol !in it.capabilities } } ->
            Result.Failure(PartyRejection.IncompatibleGame)
        playlist?.let { it.results.isEmpty() && it.games == games } == true -> Result.Success(this)
        else -> Result.Success(copy(seats = seats.resetReadiness(), revision = revision + 1, playlist = PartyPlaylist(games.toList())))
    }

    /** The authenticated member can set readiness only for the selected game's current revision. */
    fun setReady(actor: PlayerId, ready: Boolean, expectedRevision: Long): Result<PartyLobby, PartyRejection> {
        if (phase != PartyPhase.Lobby) return Result.Failure(PartyRejection.InvalidPhase)
        if (playlist?.nextGame?.let { it != game } == true) return Result.Failure(PartyRejection.InvalidPhase)
        if (revision != expectedRevision) return Result.Failure(PartyRejection.StaleRevision)
        val seat = seats[actor] ?: return Result.Failure(PartyRejection.UnknownPlayer)
        if (!seat.member.connected) return Result.Failure(PartyRejection.UnknownPlayer)
        return Result.Success(if (seat.ready == ready) this else replaceSeat(seat.copy(ready = ready)))
    }

    /** A disconnected seat stays in the party but requires explicit readiness after reconnect. */
    fun updatePresence(actor: PlayerId, connected: Boolean): Result<PartyLobby, PartyRejection> {
        if (phase == PartyPhase.Ended) return Result.Failure(PartyRejection.InvalidPhase)
        val seat = seats[actor] ?: return Result.Failure(PartyRejection.UnknownPlayer)
        if (seat.member.connected == connected) return Result.Success(this)
        return Result.Success(replaceSeat(seat.copy(member = seat.member.copy(connected = connected), ready = false)))
    }

    fun removeSeat(player: PlayerId): Result<PartyLobby, PartyRejection> = when {
        phase == PartyPhase.Ended || player == host -> Result.Failure(PartyRejection.InvalidPhase)
        player !in seats -> Result.Failure(PartyRejection.UnknownPlayer)
        else -> Result.Success(copy(seats = seats - player, revision = revision + 1))
    }

    fun prepareMatch(): Result<PartyLobby, PartyRejection> {
        if (phase != PartyPhase.Lobby) return Result.Failure(PartyRejection.InvalidPhase)
        if (playlist?.nextGame?.let { it != game } == true) return Result.Failure(PartyRejection.InvalidPhase)
        val connected = seats.values.filter { it.member.connected }
        if (connected.size !in game.playerCounts || connected.none { it.member.playerId == host }) {
            return Result.Failure(PartyRejection.IneligibleCount)
        }
        if (connected.any { it.member.playerId != host && !it.ready }) {
            return Result.Failure(PartyRejection.WaitingForReadiness)
        }
        val roster = connected.map { it.member.playerId }.toSet()
        // Each start attempt gets a fresh identity even when its ordinal never commits.
        val matchId = SessionId("${id.raw}-${revision + 1}")
        return Result.Success(copy(
            phase = PartyPhase.Starting(PartyMatch(matchId, game.protocol, roster, matchOrdinal + 1)),
            revision = revision + 1,
        ))
    }

    fun commitMatch(matchId: SessionId): Result<PartyLobby, PartyRejection> {
        if (phase is PartyPhase.Playing) {
            return if (phase.match.id == matchId) Result.Success(this) else Result.Failure(PartyRejection.StaleMatch)
        }
        val current = (phase as? PartyPhase.Starting)?.match ?: return Result.Failure(PartyRejection.InvalidPhase)
        if (current.id != matchId) return Result.Failure(PartyRejection.StaleMatch)
        // Called after the game coordinator's irreversible Ready/Commit
        // barrier. A disconnect after that barrier must not undo the result.
        return Result.Success(copy(
            phase = PartyPhase.Playing(current), matchOrdinal = current.ordinal, revision = revision + 1,
        ))
    }

    fun returnToLobby(matchId: SessionId, completed: Boolean = false): Result<PartyLobby, PartyRejection> {
        val current = when (phase) {
            is PartyPhase.Starting -> phase.match
            is PartyPhase.Playing -> phase.match
            else -> return Result.Failure(PartyRejection.InvalidPhase)
        }
        if (current.id != matchId) return Result.Failure(PartyRejection.StaleMatch)
        if (completed && phase !is PartyPhase.Playing) return Result.Failure(PartyRejection.InvalidPhase)
        val progress = playlist?.let {
            if (completed && it.nextGame != null) {
                it.copy(results = it.results + PartyMatchResult(current.id, current.game))
            } else {
                it
            }
        }
        return Result.Success(copy(
            phase = PartyPhase.Lobby, seats = seats.resetReadiness(), revision = revision + 1, playlist = progress,
        ))
    }

    fun endParty(): PartyLobby =
        if (phase == PartyPhase.Ended) this else copy(phase = PartyPhase.Ended, revision = revision + 1)

    /** Candidates must match the explicit active match and still belong to its frozen roster. */
    fun acceptsGameplay(matchId: SessionId, target: GameProtocolRef, actor: PlayerId): Boolean {
        val match = (phase as? PartyPhase.Playing)?.match ?: return false
        return match.id == matchId && match.game == target && actor in match.roster &&
            seats[actor]?.member?.connected == true
    }

    private fun replaceSeat(seat: PartySeat): PartyLobby =
        copy(seats = seats + (seat.member.playerId to seat), revision = revision + 1)

    private fun Map<PlayerId, PartySeat>.resetReadiness(): Map<PlayerId, PartySeat> =
        mapValues { (_, seat) -> seat.copy(ready = false) }

    companion object {
        fun open(id: PartyId, host: PartySeat, game: PartyGame, roomName: String? = null): PartyLobby {
            require(host.member.connected && game.protocol in host.capabilities)
            return PartyLobby(
                id = id, host = host.member.playerId, game = game,
                seats = mapOf(host.member.playerId to host.copy(capabilities = host.capabilities.toSet(), ready = false)),
                revision = 1, matchOrdinal = 0, phase = PartyPhase.Lobby, roomName = roomName,
            )
        }
    }
}

private const val MAX_MATCH_PLAYERS = 16
