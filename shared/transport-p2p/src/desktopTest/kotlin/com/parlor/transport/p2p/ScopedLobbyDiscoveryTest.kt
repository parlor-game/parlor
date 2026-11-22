package com.parlor.transport.p2p

import com.parlor.core.ids.GameId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.RoomId
import com.parlor.core.result.Result
import com.parlor.networking.invitation.JoinLinkCodec
import com.parlor.networking.protocol.AdmissionLobbyInfo
import com.parlor.networking.protocol.AdmissionRejection
import com.parlor.networking.protocol.GameProtocolRef
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.RoomMessageCodec
import com.parlor.networking.room.DiscoveredRoom
import com.parlor.networking.room.LobbyAdvertisement
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.transport.HostConfig
import com.parlor.networking.transport.HostedGameProtocol
import com.parlor.networking.transport.JoinConfig
import dev.p2pkit.core.AppId
import dev.p2pkit.core.P2pKit
import dev.p2pkit.core.P2pMessage
import dev.p2pkit.core.Peer
import dev.p2pkit.core.PeerId
import dev.p2pkit.core.PeerIdentity
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
import kotlin.test.assertNull
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

    @Test
    fun invitation_requires_the_exact_game_tag_and_authenticates_before_sending_the_code() = runTest {
        val kit = FakeP2pKit(PeerId("join-by-link"))
        val unrelated = room("other-game", "K7QX", dominoes)
        val otherRoom = room("other-room", "ABCD", mafia)
        val wrongIdentity = room("wrong-fingerprint", "K7QX", mafia)
        val host = room("actual-host", "K7QX", mafia)
        val untrusted = FakeP2pSession(wrongIdentity, peerIdentity = PeerIdentity(wrongIdentity.id, fingerprint = null))
        val session = FakeP2pSession(host)
        val attempts = mutableListOf<PeerId>()
        val codec = RoomMessageCodec()
        val roomId = RoomId("f".repeat(32))
        val link = assertNotNull(JoinLinkCodec.encode(mafia, roomId, "K7QX", "ABCDEF", kit.localFingerprint.value))
        val invite = assertIs<JoinLinkCodec.Decoded.Valid>(JoinLinkCodec.decode(link)).invitation
        kit.peersFlow.value = listOf(unrelated, otherRoom, wrongIdentity)
        kit.connectHandler = { peer ->
            attempts += peer.id
            if (peer.id == wrongIdentity.id) {
                kit.peersFlow.value += host
                untrusted
            } else {
                session
            }
        }
        session.sendHandler = { frame ->
            val request = assertIs<PeerMessage.AdmissionRequest>(codec.decode(assertIs<P2pMessage.Binary>(frame).bytes))
            assertEquals(invite.roomId, request.expectedRoomId)
            assertEquals(invite.code, request.roomCode)
            session.incomingFlow.emit(P2pMessage.Binary(
                codec.encode(HostMessage.AdmissionRejected(AdmissionRejection.HostDeclined)),
            ))
        }
        var created = 0
        val transport = transport { created++; kit }
        val config = JoinConfig(invite.code, "Guest", expectedGameProtocol = invite.game, invitation = invite)
        assertEquals(Result.Failure(NetError.InvalidInput), transport.join(config.copy(expectedGameProtocol = dominoes)))
        assertEquals(0, created)
        assertEquals(Result.Failure(NetError.HostDeclined), transport.join(config))
        assertEquals(listOf(wrongIdentity.id, host.id), attempts)
        assertTrue(untrusted.sent.isEmpty())
        assertEquals(kit.localFingerprint, kit.lastExpectedFingerprint)
    }

    @Test
    fun host_creates_an_invitation_for_its_active_room_only() = runTest {
        val kit = FakeP2pKit(PeerId("host"))
        val transport = transport { kit }
        val room = assertIs<Result.Success<LocalRoom>>(
            transport.host(HostConfig("Guest", gameProtocol = mafia)),
        ).data
        try {
            val link = assertNotNull(room.createJoinLink())
            val invitation = assertIs<JoinLinkCodec.Decoded.Valid>(JoinLinkCodec.decode(link)).invitation
            assertEquals(mafia, invitation.game)
            assertEquals(room.info.value.code, invitation.code)
            assertEquals(room.info.value.roomId, invitation.roomId)
            assertEquals(room.info.value.roomTag, invitation.roomTag)
            assertEquals(kit.localFingerprint.value, invitation.hostFingerprint)
            assertIs<Result.Success<*>>(room.closeAdmissions())
            assertNull(room.createJoinLink())
        } finally {
            room.leave()
        }
    }

    @Test
    fun peer_rejects_a_different_game_in_the_encrypted_approval_preview() = runTest {
        val kit = FakeP2pKit(PeerId("peer"))
        val advertised = room("host", "K7QX", mafia)
        kit.peersFlow.value = listOf(advertised)
        val session = FakeP2pSession(advertised)
        kit.connectHandler = { session }
        val codec = RoomMessageCodec()
        session.sendHandler = { frame ->
            assertIs<PeerMessage.AdmissionRequest>(codec.decode(assertIs<P2pMessage.Binary>(frame).bytes))
            val preview = AdmissionLobbyInfo(
                roomId = RoomId("f".repeat(32)),
                game = GameProtocolRef(dominoes.gameId, dominoes.gameVersion),
                hostDisplayName = "Host",
                seatsTaken = 1,
                seatLimit = 5,
            )
            val pending = HostMessage.AdmissionPending(PlayerId(kit.localPeerId.value), preview, 60_000)
            session.incomingFlow.emit(P2pMessage.Binary(codec.encode(pending)))
        }

        val result = transport { kit }.join(JoinConfig("ABCDEF", "Guest", expectedGameProtocol = mafia))
        assertEquals(Result.Failure(NetError.WrongGame), result)
        assertEquals(1, session.sent.size)
        assertEquals(1, session.closeCalls)
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
