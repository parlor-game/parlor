package com.parlor.transport.p2p

import com.parlor.core.ids.PartyId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.RoomId
import com.parlor.core.result.Result
import com.parlor.networking.protocol.GameProtocolRef
import com.parlor.networking.protocol.PartyLobbyPhase
import com.parlor.networking.protocol.PublicPartyLobby
import com.parlor.networking.protocol.PublicPartySeat
import com.parlor.networking.room.HostedPartyCheckpoint
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.transport.HostConfig
import com.parlor.networking.transport.HostedPartyProtocol
import com.parlor.storage.snapshot.SnapshotFileSystem
import dev.p2pkit.core.AppId
import dev.p2pkit.core.P2pKit
import dev.p2pkit.core.PeerId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HostedPartyStoreTest {
    private val party = PartyId("a".repeat(32))
    private val host = PlayerId("original-host")
    private val game = GameProtocolRef(loopbackGameProtocol.gameId, loopbackGameProtocol.gameVersion)
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
