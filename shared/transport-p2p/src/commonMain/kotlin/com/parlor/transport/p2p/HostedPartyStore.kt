package com.parlor.transport.p2p

import com.parlor.core.ids.PartyId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.RoomId
import com.parlor.core.result.Result
import com.parlor.core.serialization.requireBoundedJsonNesting
import com.parlor.networking.invitation.JoinLinkCodec
import com.parlor.networking.protocol.GameProtocolRef
import com.parlor.networking.protocol.PARTY_WIRE_VERSION
import com.parlor.networking.protocol.PartyAdmissionContext
import com.parlor.networking.protocol.ProtocolVersion
import com.parlor.networking.room.HostedPartyCheckpoint
import com.parlor.networking.room.NetError
import com.parlor.networking.room.RoomInputPolicy
import com.parlor.storage.snapshot.SnapshotFileSystem
import com.parlor.storage.snapshot.SnapshotProtectionException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.Required
import kotlinx.serialization.json.Json

/** This envelope is installation-private. A checkpoint never contains a guest rejoin secret. */
@Serializable
internal data class StoredHostedParty(
    val partyId: PartyId,
    val roomId: RoomId,
    val roomCode: String,
    val roomTag: String,
    val host: PlayerId,
    val hostFingerprint: String,
    val maxRemotePlayers: Int,
    val checkpoint: HostedPartyCheckpoint,
    val memberships: List<StoredHostedPartyMember>,
    val savedAtEpochMillis: Long,
    @Required val protocol: ProtocolVersion = PARTY_WIRE_VERSION,
    @Required val schema: Int = 1,
) {
    fun requireValid(): StoredHostedParty = apply {
        require(schema == 1 && protocol == PARTY_WIRE_VERSION)
        require(savedAtEpochMillis > 0 && savedAtEpochMillis <= Long.MAX_VALUE - MAX_HOSTED_PARTY_AGE_MS)
        require(isValidPeerId(host))
        require(JoinLinkCodec.encodeParty(partyId, roomId, roomTag, roomCode, hostFingerprint) != null)
        require(maxRemotePlayers in 1..MAX_REMOTE_SEATS)
        require(memberships.size <= maxRemotePlayers)
        val lobby = checkpoint.lobby
        require(lobby.seatLimit <= maxRemotePlayers + 1)
        require(lobby.members.first().playerId == host)
        require(memberships.map(StoredHostedPartyMember::player).toSet() == lobby.members.drop(1).map { it.playerId }.toSet())
        require(memberships.size == lobby.members.size - 1)
        require(RoomInputPolicy.areValidDistinctDisplayNames(lobby.members.map { it.displayName }))
        val seats = lobby.members.associateBy { it.playerId }
        require(checkpoint.cursors.none { it.player == host })
        memberships.forEach { member ->
            member.requireValid()
            require(lobby.selectedGame in member.games)
            require(seats.getValue(member.player).displayName == member.displayName)
        }
    }

    fun canRestoreAt(now: Long): Boolean = now in savedAtEpochMillis..(savedAtEpochMillis + MAX_HOSTED_PARTY_AGE_MS)

    override fun toString(): String = "StoredHostedParty(redacted)"
}

@Serializable
internal data class StoredHostedPartyMember(
    val player: PlayerId,
    val displayName: String,
    val games: List<GameProtocolRef>,
    val fingerprint: String,
    val offerId: String,
    val generation: Long,
    val digest: String,
    val expiresAtEpochMillis: Long,
    val previousGeneration: Long? = null,
    val previousDigest: String? = null,
    val disconnectedUntil: Long? = null,
) {
    fun requireValid() {
        require(isValidPeerId(player))
        require(RoomInputPolicy.isValidDisplayName(displayName))
        require(displayName == RoomInputPolicy.normalizeDisplayName(displayName))
        require(PartyAdmissionContext(supportedGames = games).isValid())
        require(FINGERPRINT_PATTERN.matches(fingerprint))
        require(OFFER_ID_PATTERN.matches(offerId))
        require(generation in 1 until Long.MAX_VALUE && expiresAtEpochMillis > 0)
        require(DIGEST_PATTERN.matches(digest))
        require((previousGeneration == null) == (previousDigest == null))
        require(previousGeneration == null || previousGeneration in 1 until generation)
        require(previousDigest == null || DIGEST_PATTERN.matches(previousDigest))
        require(disconnectedUntil == null || disconnectedUntil > 0)
    }

    override fun toString(): String = "StoredHostedPartyMember(redacted)"
}

/** One atomic record, independent from game snapshots and the guest's resume credential. */
internal class HostedPartyStore(private val fileSystem: SnapshotFileSystem) {
    private val transactions = Mutex()

    suspend fun save(value: StoredHostedParty): Result<Unit, NetError> = transactions.withLock {
        protectedIo {
            val bytes = Json.encodeToString(StoredHostedParty.serializer(), value.requireValid()).encodeToByteArray()
            try {
                require(bytes.size <= MAX_CHECKPOINT_BYTES)
                fileSystem.write(FILE_NAME, bytes)
            } finally {
                bytes.fill(0)
            }
        }
    }

    suspend fun load(): Result<StoredHostedParty?, NetError> = transactions.withLock {
        protectedIo { readRecord() }
    }

    suspend fun discard(expected: PartyId): Result<Unit, NetError> = transactions.withLock {
        when (val current = protectedIo { readRecord()?.partyId }) {
            is Result.Failure -> current
            is Result.Success -> when (current.data) {
                null -> Result.Success(Unit)
                expected -> protectedIo { fileSystem.delete(FILE_NAME) }
                else -> Result.Failure(NetError.StaleRoom)
            }
        }
    }

    private suspend fun readRecord(): StoredHostedParty? {
        val bytes = fileSystem.readAuthenticated(FILE_NAME) ?: return null
        return try {
            require(bytes.size in 1..MAX_CHECKPOINT_BYTES)
            requireBoundedJsonNesting(bytes)
            Json.decodeFromString(StoredHostedParty.serializer(), bytes.decodeToString(throwOnInvalidSequence = true))
                .requireValid()
        } finally {
            bytes.fill(0)
        }
    }

    private suspend fun <T> protectedIo(action: suspend () -> T): Result<T, NetError> = try {
        Result.Success(action())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: SnapshotProtectionException) {
        Result.Failure(NetError.SecureStorageUnavailable)
    } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
        Result.Failure(NetError.SecureStorageUnavailable)
    }

    private companion object {
        const val FILE_NAME = "hosted-party.checkpoint"
        // Bounds the full 32-result history including 16 distinct players per match.
        const val MAX_CHECKPOINT_BYTES = 256 * 1024
    }
}

internal const val MAX_HOSTED_PARTY_AGE_MS = 24L * 60 * 60 * 1_000
private const val MAX_REMOTE_SEATS = 15
private val FINGERPRINT_PATTERN = Regex("p2f1-[a-z2-7]{51}[aq]")
private val OFFER_ID_PATTERN = Regex("[0-9a-f]{32}")
private val DIGEST_PATTERN = Regex("[0-9a-f]{64}")
private val PEER_ID_PATTERN = Regex("[a-zA-Z0-9_.-]{1,128}")
private fun isValidPeerId(player: PlayerId): Boolean = PEER_ID_PATTERN.matches(player.raw)
