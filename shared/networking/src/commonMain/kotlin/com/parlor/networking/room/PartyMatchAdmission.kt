package com.parlor.networking.room

import com.parlor.core.ids.PartyId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.result.Result

/** A host party room keeps logical membership alive across independently owned matches. */
interface PartyMatchAdmission {
    val partyId: PartyId

    /** Freeze exactly the connected peers in the current start attempt, without retiring other party seats. */
    suspend fun freezeMatch(id: SessionId, expectedPeers: Set<PlayerId>): Result<List<RoomMember>, NetError>

    /** Return to lobby admission only if this call owns the frozen match. */
    suspend fun reopenMatch(id: SessionId): Result<Unit, NetError>
}
