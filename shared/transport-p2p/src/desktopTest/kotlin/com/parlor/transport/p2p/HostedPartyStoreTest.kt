package com.parlor.transport.p2p

import com.parlor.core.ids.PartyId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.RoomId
import com.parlor.core.result.Result
import com.parlor.networking.protocol.GameProtocolRef
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PARTY_WIRE_VERSION
import com.parlor.networking.protocol.PartyAdmissionContext
import com.parlor.networking.protocol.PartyLobbyPhase
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.PublicPartyLobby
import com.parlor.networking.protocol.PublicPartySeat
import com.parlor.networking.protocol.RoomMessageCodec
import com.parlor.networking.room.HostedPartyCheckpoint
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.transport.HostConfig
import com.parlor.networking.transport.HostedPartyProtocol
import com.parlor.storage.snapshot.SnapshotFileSystem
import dev.p2pkit.core.AppId
import dev.p2pkit.core.P2pKit
import dev.p2pkit.core.P2pMessage
import dev.p2pkit.core.Peer
import dev.p2pkit.core.PeerFingerprint
import dev.p2pkit.core.PeerId
import dev.p2pkit.core.PeerIdentity
import dev.p2pkit.core.Platform
import dev.p2pkit.core.TransportKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HostedPartyStoreTest {
    private val party = PartyId("a".repeat(32))
    private val host = PlayerId("original-host")
    private val game = GameProtocolRef(loopbackGameProtocol.gameId, loopbackGameProtocol.gameVersion)
    private val codec = RoomMessageCodec()
    private val emptyLobby = HostedPartyCheckpoint(1L, PublicPartyLobby(
        PartyLobbyPhase.Lobby, game, listOf(PublicPartySeat(host, "Host", true, false, false)),
        seatLimit = 2, activeMatchId = null, matchOrdinal = 0,
    ))

    @Test
    fun restored_transport_keeps_the_exact_room_and_rejects_another_installation_before_advertising() = runTest {
        val files = ProtectedTestFiles()
        val originalKit = FakeP2pKit(PeerId(host.raw))
        val initial = assertIs<Result.Success<LocalRoom>>(transport(originalKit, files, backgroundScope).host(
            HostConfig("Host", maxRemotePlayers = 1, partyProtocol = HostedPartyProtocol(party, loopbackGameProtocol, 2)),
        )).data
        val initialInfo = initial.info.value
        assertEquals(Result.Success(Unit), requireNotNull(initial.partyMatches).saveCheckpoint(emptyLobby))
        initial.leave() // Process loss tears down sockets without an intentional Party close.

        val other = FakeP2pKit(PeerId("different-installation"))
        assertEquals(Result.Failure(NetError.Unauthorized), transport(other, files, backgroundScope).restoreHostedParty(party))
        assertEquals(0, other.startAdvertisingCalls)
        assertEquals(1, other.stopCalls)

        val freshKit = FakeP2pKit(PeerId(host.raw))
        val fresh = transport(freshKit, files, backgroundScope)
        assertEquals(Result.Success(party), fresh.resumableHostedPartyId())
        val restored = assertIs<Result.Success<LocalRoom>>(fresh.restoreHostedParty(party)).data
        try {
            assertEquals(initialInfo, restored.info.value)
            val recovery = requireNotNull(restored.partyMatches)
            assertEquals(emptyLobby, recovery.restoredCheckpoint)
            assertEquals(0, freshKit.startAdvertisingCalls)
            assertEquals(Result.Success(Unit), recovery.activateRestoredHost())
            assertEquals(1, freshKit.startAdvertisingCalls)
            assertEquals(Result.Success(Unit), recovery.discardCheckpoint())
            assertEquals(Result.Success(null), fresh.resumableHostedPartyId())
        } finally {
            restored.leave()
        }
    }

    @Test
    fun approved_guest_rejoins_on_original_generation_after_host_process_loss() = runTest {
        val files = ProtectedTestFiles()
        val prior = FakeP2pKit(PeerId(host.raw))
        val initial = assertIs<Result.Success<LocalRoom>>(transport(prior, files, backgroundScope).host(
            HostConfig("Host", maxRemotePlayers = 1, partyProtocol = HostedPartyProtocol(party, loopbackGameProtocol, 2)),
        )).data
        val info = initial.info.value
        val peer = Peer(PeerId("guest-on-original-device"), "Guest", Platform.UNKNOWN, setOf(TransportKind.LAN))
        val seat = PlayerId(peer.id.value)
        val originalSession = FakeP2pSession(peer)
        val secret = try {
            assertEquals(Result.Success(Unit), requireNotNull(initial.partyMatches).saveCheckpoint(emptyLobby))
            attach(prior, originalSession)
            originalSession.incomingFlow.emit(P2pMessage.Binary(codec.encode(PeerMessage.AdmissionRequest(
                protocol = PARTY_WIRE_VERSION, actor = seat, roomCode = info.code,
                displayName = peer.name, party = PartyAdmissionContext(party, listOf(game)), expectedRoomId = info.roomId,
            ))))
            initial.pendingAdmissions.first { it.size == 1 }
            assertEquals(Result.Success(Unit), initial.approveAdmission(seat))
            originalSession.sent.filterIsInstance<P2pMessage.Binary>()
                .map { codec.decode(it.bytes) }.filterIsInstance<HostMessage.AdmissionOffered>().single().offer.secret
        } finally {
            initial.leave()
        }

        val next = FakeP2pKit(PeerId(host.raw))
        val reopened = assertIs<Result.Success<LocalRoom>>(transport(next, files, backgroundScope).restoreHostedParty(party)).data
        try {
            val lease = requireNotNull(reopened.partyMatches)
            assertEquals(info, reopened.info.value)
            assertEquals(seat, reopened.members.value.single().playerId)
            assertFalse(reopened.members.value.single().connected)
            assertEquals(Result.Success(Unit), lease.activateRestoredHost())
            val request = PeerMessage.ResumeRequested(
                protocol = PARTY_WIRE_VERSION, actor = seat, roomCode = info.code,
                displayName = peer.name, secret = secret, generation = 1L, roomId = info.roomId, partyId = party,
            )
            val impostor = FakeP2pSession(peer, peerIdentity = PeerIdentity(peer.id, PeerFingerprint("p2f1-" + "a".repeat(52))))
            attach(next, impostor)
            impostor.incomingFlow.emit(P2pMessage.Binary(codec.encode(request)))
            runCurrent()
            assertTrue(impostor.sent.filterIsInstance<P2pMessage.Binary>().map { codec.decode(it.bytes) }
                .any { it is HostMessage.AdmissionRejected })
            assertFalse(reopened.members.value.single().connected)

            val returning = FakeP2pSession(peer)
            attach(next, returning)
            returning.incomingFlow.emit(P2pMessage.Binary(codec.encode(request)))
            runCurrent()
            assertEquals(seat, reopened.members.value.single().playerId)
            assertTrue(reopened.members.value.single().connected)
            val committed = returning.sent.filterIsInstance<P2pMessage.Binary>().map { codec.decode(it.bytes) }
                .filterIsInstance<HostMessage.ResumeCommitted>().single()
            assertEquals(2L, committed.generation)
            assertTrue(reopened.pendingAdmissions.value.isEmpty())
        } finally {
            reopened.leave()
        }
    }

    private suspend fun attach(kit: FakeP2pKit, session: FakeP2pSession) {
        kit.incomingSessionsFlow.emit(session)
        session.incomingFlow.subscriptionCount.first { it > 0 }
    }

    @Test
    fun malformed_authority_is_rejected_without_using_legacy_reads_or_erasing_the_record() = runTest {
        val files = ProtectedTestFiles()
        val store = HostedPartyStore(files)
        assertEquals(Result.Success(null), store.load())
        assertNull(files.bytes)
        files.bytes = "[".repeat(5000).encodeToByteArray()
        assertEquals(Result.Failure(NetError.SecureStorageUnavailable), store.load())
        assertFalse(files.legacyRead)
        assertTrue(requireNotNull(files.bytes).isNotEmpty())
    }

    private fun transport(kit: FakeP2pKit, files: SnapshotFileSystem, scope: CoroutineScope): P2pKitRoomTransport =
        P2pKitRoomTransport(
            AppId("com.parlor.host-restart"), "host", scope,
            object : P2pKitFactory {
                override suspend fun createKit(appId: AppId, deviceName: String): P2pKit = kit
            },
            diagnostics = NoOpP2pDiagnostics,
            hostedPartyFiles = files,
        )

    private class ProtectedTestFiles : SnapshotFileSystem {
        var bytes: ByteArray? = null
        var legacyRead = false

        override suspend fun read(name: String): ByteArray? {
            legacyRead = true
            error("Host authority must never be read as a legacy save")
        }

        override suspend fun readAuthenticated(name: String): ByteArray? = bytes?.copyOf()

        override suspend fun write(name: String, bytes: ByteArray) {
            this.bytes = bytes.copyOf()
        }

        override suspend fun delete(name: String) {
            bytes = null
        }

        override suspend fun list(): List<String> = emptyList()
    }
}
