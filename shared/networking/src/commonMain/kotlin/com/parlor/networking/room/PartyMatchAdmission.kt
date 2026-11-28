package com.parlor.networking.room

import com.parlor.core.ids.PartyId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.result.Result
import com.parlor.networking.protocol.GameProtocolRef

/** A host party room keeps logical membership alive across independently owned matches. */
interface PartyMatchAdmission {
    val partyId: PartyId
    val restoredCheckpoint: HostedPartyCheckpoint? get() = null

    suspend fun saveCheckpoint(checkpoint: HostedPartyCheckpoint): Result<Unit, NetError> = Result.Success(Unit)

    /** Advertise a restored room only after the session authority is installed. */
    suspend fun activateRestoredHost(): Result<Unit, NetError> = Result.Success(Unit)

    suspend fun discardCheckpoint(): Result<Unit, NetError> = Result.Success(Unit)

    /** Atomically switches lobby admission to the selected game's exact tuple and seat limit. */
    suspend fun selectGame(
        expected: GameProtocolRef,
        next: GameProtocolRef,
        seatLimit: Int,
        expectedMembers: Set<PlayerId>,
    ): Result<Unit, NetError>

    suspend fun selectGame(
        expected: GameProtocolRef,
        next: GameProtocolRef,
        seatLimit: Int,
        expectedMembers: Set<PlayerId>,
        checkpoint: HostedPartyCheckpoint,
    ): Result<Unit, NetError> = selectGame(expected, next, seatLimit, expectedMembers)

    /** Freeze exactly the connected peers in the current start attempt, without retiring other party seats. */
    suspend fun freezeMatch(id: SessionId, expectedPeers: Set<PlayerId>): Result<List<RoomMember>, NetError>

    suspend fun freezeMatch(
        id: SessionId,
        expectedPeers: Set<PlayerId>,
        checkpoint: HostedPartyCheckpoint,
    ): Result<List<RoomMember>, NetError> = freezeMatch(id, expectedPeers)

    /** Return to lobby admission only if this call owns the frozen match. */
    suspend fun reopenMatch(id: SessionId): Result<Unit, NetError>

    suspend fun reopenMatch(id: SessionId, checkpoint: HostedPartyCheckpoint): Result<Unit, NetError> = reopenMatch(id)
}
