package com.parlor.transport.p2p

import com.parlor.core.ids.GameId
import com.parlor.core.result.Result
import com.parlor.networking.protocol.AdmissionRejection
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.RoomMessageCodec
import com.parlor.networking.room.DiscoveredRoom
import com.parlor.networking.room.LobbyAdvertisement
import com.parlor.networking.room.NetError
import com.parlor.networking.transport.HostedGameProtocol
import com.parlor.networking.transport.JoinConfig
import dev.p2pkit.core.AppId
import dev.p2pkit.core.P2pKit
import dev.p2pkit.core.P2pMessage
import dev.p2pkit.core.Peer
import dev.p2pkit.core.PeerId
import dev.p2pkit.core.Platform
import dev.p2pkit.core.TransportKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ScopedLobbyDiscoveryTest {
    private val mafia = HostedGameProtocol(GameId("mafia"), 1)
    private val dominoes = HostedGameProtocol(GameId("egyptian-dominoes"), 1)

    @Test
    fun browser_filters_by_game_bounds_the_rows_and_removes_lost_rooms() = runTest {
        val kit = FakeP2pKit(PeerId("browser"))
        val transport = transport { kit }
        val otherGame = room("dominoes", "K7QX", dominoes)
        val mafiaRooms = List(36) { n -> room("mafia-$n", "ABCD", mafia) }
        kit.peersFlow.value = listOf(otherGame, mafiaRooms.first()) + mafiaRooms
        val browsing = async { transport.discoverRooms(mafia).take(2).toList() }
        runCurrent()
        assertEquals(listOf("start", "startDiscovery"), kit.callLog)

        kit.peersFlow.value = listOf(otherGame)
        val (found, lost) = browsing.await()
        val foundRooms = assertIs<Result.Success<List<DiscoveredRoom>>>(found).data
        assertEquals(32, foundRooms.size)
        assertTrue(foundRooms.all { room ->
            room.roomTag == "ABCD" && room.code.isEmpty() && room.hostPlayerId == null
        })
        assertEquals(Result.Success(emptyList()), lost)
        assertEquals(1, kit.stopCalls)
    }

    @Test
    fun selected_room_requires_the_same_game_and_dials_only_that_advertisement() = runTest {
        val browser = FakeP2pKit(PeerId("browser"))
        val joining = FakeP2pKit(PeerId("joining"))
        val otherMafiaRoom = room("other", "ABCD", mafia)
        val selectedRoom = room("selected", "WXYZ", mafia)
        browser.peersFlow.value = listOf(selectedRoom)
        joining.peersFlow.value = listOf(otherMafiaRoom, selectedRoom)
        val attempts = mutableListOf<PeerId>()
        val codec = RoomMessageCodec()
        val session = FakeP2pSession(selectedRoom)
        session.sendHandler = { frame ->
            val request = assertIs<PeerMessage.AdmissionRequest>(codec.decode(assertIs<P2pMessage.Binary>(frame).bytes))
            assertEquals("ABCDEF", request.roomCode)
            session.incomingFlow.emit(P2pMessage.Binary(
                codec.encode(HostMessage.AdmissionRejected(AdmissionRejection.HostDeclined)),
            ))
        }
        joining.connectHandler = { peer ->
            attempts += peer.id
            session
        }
        var kitCreations = 0
        val transport = transport { if (kitCreations++ == 0) browser else joining }
        val selection = assertIs<Result.Success<List<DiscoveredRoom>>>(
            transport.discoverRooms(mafia).first(),
        ).data.single().selection
        val join = JoinConfig(
            code = "ABCDEF",
            displayName = "Guest",
            expectedGameProtocol = mafia,
            discoveredRoom = assertNotNull(selection),
        )

        assertEquals(Result.Failure(NetError.StaleRoom), transport.join(join.copy(expectedGameProtocol = dominoes)))
        assertEquals(1, kitCreations)
        assertEquals(Result.Failure(NetError.HostDeclined), transport.join(join))
        assertEquals(listOf(selectedRoom.id), attempts)
        assertEquals(1, browser.stopCalls)
        assertEquals(1, joining.stopCalls)
    }

    private fun TestScope.transport(kit: () -> P2pKit) = P2pKitRoomTransport(
        appId = AppId("com.parlor.test"),
        deviceName = "fixture",
        scope = backgroundScope,
        kitFactory = object : P2pKitFactory {
            override suspend fun createKit(appId: AppId, deviceName: String): P2pKit = kit()
        },
    )

    private fun room(id: String, tag: String, game: HostedGameProtocol) = Peer(
        id = PeerId(id),
        name = assertNotNull(LobbyAdvertisement.encodeGame(game, tag)),
        platform = Platform.UNKNOWN,
        supportedTransports = setOf(TransportKind.LAN),
    )
}
