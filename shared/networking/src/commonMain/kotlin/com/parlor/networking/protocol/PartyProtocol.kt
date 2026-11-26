package com.parlor.networking.protocol

import com.parlor.core.ids.PartyId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.networking.room.RoomInputPolicy
import kotlinx.serialization.Serializable

/** Party 5.1 adds the host's playlist and completed match receipts. Standalone rooms use 4.3. */
val PARTY_WIRE_VERSION = ProtocolVersion(5, 1)
const val MAX_PARTY_PLAYLIST_MATCHES = 32

@Serializable
data class PartyProtocolHeader(
    val protocol: ProtocolVersion,
    val partyId: PartyId,
    val messageId: String,
    val sequence: Long,
) {
    init {
        require(sequence >= 0L)
        require(messageId.length in MIN_MESSAGE_ID..MAX_MESSAGE_ID && messageId.all {
            it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it in "-_"
        }) { "Invalid party message identifier" }
    }

    private companion object {
        const val MIN_MESSAGE_ID = 16
        const val MAX_MESSAGE_ID = 128
    }
}

@Serializable
enum class PartyLobbyPhase { Lobby, MatchStarting, InMatch, Ended }

@Serializable
data class PublicPartySeat(
    val playerId: PlayerId,
    val displayName: String,
    val connected: Boolean,
    val ready: Boolean,
    val inActiveMatch: Boolean,
) {
    init {
        require(displayName == RoomInputPolicy.normalizeDisplayName(displayName) &&
            RoomInputPolicy.isValidDisplayName(displayName))
    }
}

@Serializable
data class PublicPartyPlaylist(
    val games: List<GameProtocolRef>,
    val completedMatchIds: List<SessionId>,
) {
    val nextGame: GameProtocolRef? get() = games.getOrNull(completedMatchIds.size)

    init {
        require(games.size in 1..MAX_PARTY_PLAYLIST_MATCHES && games.all(GameProtocolRef::isValid))
        require(completedMatchIds.size <= games.size && completedMatchIds.toSet().size == completedMatchIds.size)
        require(completedMatchIds.all { it.raw.length <= MAX_PARTY_MATCH_ID_LENGTH })
    }

    private companion object {
        const val MAX_PARTY_MATCH_ID_LENGTH = 64
    }
}

@Serializable
data class PublicPartyLobby(
    val phase: PartyLobbyPhase,
    val selectedGame: GameProtocolRef,
    val members: List<PublicPartySeat>,
    val seatLimit: Int,
    val activeMatchId: SessionId?,
    val matchOrdinal: Long,
    val roomName: String? = null,
    val playlist: PublicPartyPlaylist? = null,
) {
    init {
        require(selectedGame.isValid())
        require(seatLimit in 2..MAX_PARTY_SEATS && members.size in 1..seatLimit)
        require(members.map(PublicPartySeat::playerId).distinct().size == members.size)
        require(matchOrdinal >= 0L)
        require(roomName == null || (roomName == RoomInputPolicy.normalizeDisplayName(roomName) &&
            RoomInputPolicy.isValidDisplayName(roomName)))
        val hasMatch = phase == PartyLobbyPhase.MatchStarting || phase == PartyLobbyPhase.InMatch
        require(hasMatch == (activeMatchId != null))
        if (playlist != null) {
            require(playlist.completedMatchIds.size <= matchOrdinal && activeMatchId !in playlist.completedMatchIds)
            require(!hasMatch || playlist.nextGame == null || playlist.nextGame == selectedGame)
        }
    }

    private companion object {
        const val MAX_PARTY_SEATS = 16
    }
}


/** Explicit Party entry. A single-game request cannot stand in for this capability list. */
@Serializable
data class PartyAdmissionContext(
    val expectedPartyId: PartyId? = null,
    val supportedGames: List<GameProtocolRef>,
) {
    fun isValid(): Boolean = supportedGames.size in 1..MAX_GAME_CAPABILITIES &&
        supportedGames.all(GameProtocolRef::isValid) &&
        supportedGames.map(GameProtocolRef::gameId).distinct().size == supportedGames.size

    private companion object {
        const val MAX_GAME_CAPABILITIES = 16
    }
}
