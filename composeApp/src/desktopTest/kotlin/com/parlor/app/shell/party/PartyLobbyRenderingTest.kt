package com.parlor.app.shell.party

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.parlor.core.ids.GameId
import com.parlor.core.ids.PartyId
import com.parlor.core.ids.PlayerId
import com.parlor.core.result.Result
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.networking.protocol.GameProtocolRef
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PartyLobbyPhase
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.PublicPartyLobby
import com.parlor.networking.protocol.PublicPartySeat
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.room.RoomInfo
import com.parlor.networking.room.RoomMember
import com.parlor.networking.room.SendTarget
import com.parlor.networking.transport.JoinConfig
import com.parlor.networking.transport.HostConfig
import com.parlor.networking.transport.RoomTransport
import com.parlor.networking.transport.TransportCapability
import com.parlor.session.lobby.PartyGame
import com.parlor.session.lobby.ProcessPartySession
import com.parlor.session.lobby.ProcessPartySessionOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalTestApi::class)
class PartyLobbyRenderingTest {
    @Test
    fun peer_can_reach_and_toggle_readiness_at_200_percent_in_english_and_arabic() = runTest {
        val game = PartyGame(GameProtocolRef(GameId("mafia"), 1), 3..10)
        val host = PlayerId("host")
        val peer = PlayerId("player")
        val transport = LobbyTransport(host, peer)
        val result = ProcessPartySessionOwner(transport, backgroundScope).join("Guest", "ABCDEF", setOf(game))
        val session = assertIs<Result.Success<ProcessPartySession>>(result).data
        for (language in listOf(AppLanguage.English, AppLanguage.Arabic)) {
            runComposeUiTest {
                val current = mutableStateOf(lobby(game, host, peer, ready = false))
                var tapped = 0
                val ar = language == AppLanguage.Arabic
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                        ProvideAppLanguage(language) {
                            ParlorTheme(reducedMotion = true) {
                                Box(Modifier.size(320.dp, 640.dp)) {
                                    PartyLobbyContent(
                                        session, current.value, mapOf(game to "Mafia"), false, null,
                                        onLeave = {}, onViewMatch = {},
                                        commands = PartyLobbyCommands({}, {}, {}, {}) { ready ->
                                            tapped++
                                            current.value = lobby(game, host, peer, ready)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                // One copy labels the player status and one labels the action.
                val markReady = if (ar) "جاهز" else "Ready"
                val markNotReady = if (ar) "غير جاهز" else "Not ready"
                onNodeWithText(markReady).performScrollTo().assertIsEnabled().performClick()
                onNodeWithText(markNotReady).performScrollTo().assertIsDisplayed().assertIsEnabled()
                runOnIdle { assertEquals(1, tapped) }
                runOnIdle { transport.foregroundReady.value = false }
                onNodeWithText(markNotReady).performScrollTo().assertIsNotEnabled()
                runOnIdle { transport.foregroundReady.value = true }
            }
        }
    }

    private fun lobby(game: PartyGame, host: PlayerId, peer: PlayerId, ready: Boolean) = PublicPartyLobby(
        PartyLobbyPhase.Lobby, game.protocol,
        listOf(
            PublicPartySeat(host, "Host", connected = true, ready = false, inActiveMatch = false),
            PublicPartySeat(peer, "Guest", connected = true, ready = ready, inActiveMatch = false),
        ),
        game.playerCounts.last, null, 0,
    )

    private class LobbyTransport(host: PlayerId, peer: PlayerId) : RoomTransport {
        override val capability = TransportCapability(false, 0, 16_384)
        val foregroundReady = MutableStateFlow(true)
        private val room = object : LocalRoom {
            override val info = MutableStateFlow(RoomInfo("ABCDEF", "Host", host, RoomInfo.Status.Joined,
                partyId = PartyId("a".repeat(32))))
            override val members = MutableStateFlow<List<RoomMember>>(emptyList())
            override val isHost = false
            override val incoming = emptyFlow<com.parlor.networking.protocol.RoomMessage>()
            override val selfPlayerId = peer
            override val foregroundReady = this@LobbyTransport.foregroundReady
            override suspend fun send(target: SendTarget, message: HostMessage): Result<Unit, NetError> =
                Result.Failure(NetError.Unauthorized)
            override suspend fun sendToHost(message: PeerMessage): Result<Unit, NetError> = Result.Success(Unit)
            override suspend fun leave() = Unit
        }

        override suspend fun host(config: HostConfig): Result<LocalRoom, NetError> = Result.Failure(NetError.Unauthorized)
        override suspend fun join(code: String, displayName: String): Result<LocalRoom, NetError> =
            Result.Failure(NetError.Unauthorized)
        override suspend fun join(config: JoinConfig): Result<LocalRoom, NetError> = Result.Success(room)
    }
}
