package com.parlor.networking.room

import com.parlor.core.ids.PlayerId
import com.parlor.networking.protocol.PartyLobbyPhase
import com.parlor.networking.protocol.PublicPartyLobby
import kotlinx.serialization.Serializable

/** Host-owned durable lobby data. This type is never part of the room wire protocol. */
@Serializable
data class HostedPartyCheckpoint(
    val revision: Long,
    val lobby: PublicPartyLobby,
    val cursors: List<HostedPartyCursor> = emptyList(),
) {
    init {
        require(lobby.phase == PartyLobbyPhase.Lobby)
        require(revision in 1 until Long.MAX_VALUE)
        require(cursors.size < MAX_PARTY_SEATS)
        require(cursors.map(HostedPartyCursor::player).distinct().size == cursors.size)
        require(cursors.all { cursor -> lobby.members.any { it.playerId == cursor.player } })
    }

    private companion object {
        const val MAX_PARTY_SEATS = 16
    }
}

@Serializable
data class HostedPartyCursor(val player: PlayerId, val nextSequence: Long) {
    init {
        require(nextSequence in 2 until Long.MAX_VALUE)
    }
}
