package com.parlor.networking.protocol

import com.parlor.core.ids.PartyId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.networking.room.RoomInputPolicy
import kotlinx.serialization.Serializable

/** New party traffic stays outside the single-game 4.3 admission contract. */
val PARTY_WIRE_VERSION = ProtocolVersion(5, 0)

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
data class PublicPartyLobby(
    val phase: PartyLobbyPhase,
    val selectedGame: GameProtocolRef,
    val members: List<PublicPartySeat>,
    val seatLimit: Int,
    val activeMatchId: SessionId?,
    val matchOrdinal: Long,
) {
    init {
        require(selectedGame.isValid())
        require(seatLimit in 2..MAX_PARTY_SEATS && members.size in 1..seatLimit)
        require(members.map(PublicPartySeat::playerId).distinct().size == members.size)
        require(matchOrdinal >= 0L)
        val hasMatch = phase == PartyLobbyPhase.MatchStarting || phase == PartyLobbyPhase.InMatch
        require(hasMatch == (activeMatchId != null))
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
