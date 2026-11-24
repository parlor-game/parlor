package com.parlor.app.shell.party

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.parlor.app.shell.game.DefaultGameShellRegistry
import com.parlor.app.shell.profile.LocalPreferredDisplayName
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
                                    val showRules = remember { mutableStateOf(false) }
                                    if (showRules.value) {
                                        PartyRulesContent("Mafia", onBack = { showRules.value = false }) {
                                            PartyDetail("Selected game rules")
                                        }
                                    } else {
                                        PartyLobbyContent(
                                            session, current.value, mapOf(game to "Mafia"), false, null,
                                            onLeave = {}, onViewMatch = {},
                                            commands = PartyLobbyCommands(
                                                {}, {}, {}, {}, setReady = { ready ->
                                                    tapped++
                                                    current.value = lobby(game, host, peer, ready)
                                                },
                                                viewRules = { showRules.value = true }, invite = {},
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                val rules = if (ar) "عرض قواعد اللعبة" else "View game rules"
                val back = if (ar) "العودة إلى المجموعة" else "Back to party"
                onNodeWithText(rules).performScrollTo().assertIsEnabled().performClick()
                onNodeWithText("Selected game rules").assertIsDisplayed()
                onNodeWithText(if (ar) "جاهز" else "Ready").assertDoesNotExist()
                onNodeWithText(back).assertIsDisplayed().performClick()
                onNodeWithText("Selected game rules").assertDoesNotExist()

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

    @Test
    fun join_failure_appears_by_its_button_on_a_narrow_screen() = runTest {
        val game = PartyGame(GameProtocolRef(GameId("mafia"), 1), 5..16)
        val transport = LobbyTransport(PlayerId("host"), PlayerId("player"), joinError = NetError.StaleRoom)
        val owner = ProcessPartySessionOwner(transport, backgroundScope)
        runComposeUiTest {
            setContent {
                ProvideAppLanguage(AppLanguage.English) {
                    ParlorTheme(reducedMotion = true) {
                        Box(Modifier.size(320.dp, 570.dp)) {
                            PartyEntry(owner, transport, mapOf(game to "Mafia"), null, 0) {}
                        }
                    }
                }
            }
            onNode(hasSetTextAction() and hasText("Your player name")).performTextInput("Guest")
            onNode(hasSetTextAction() and hasText("Room code")).performTextInput("ABCDEF")
            onNodeWithText("Request to join").assertIsDisplayed().assertIsEnabled().performClick()
            onNodeWithText("This room has closed or been replaced. Ask the host for a new invitation.").assertIsDisplayed()
        }
    }

    @Test
    fun party_join_prefills_the_saved_name_and_allows_a_room_alias() = runTest {
        val game = PartyGame(GameProtocolRef(GameId("mafia"), 1), 5..16)
        val transport = LobbyTransport(PlayerId("host"), PlayerId("player"))
        val owner = ProcessPartySessionOwner(transport, backgroundScope)
        runComposeUiTest {
            setContent {
                ProvideAppLanguage(AppLanguage.English) {
                    ParlorTheme(reducedMotion = true) {
                        CompositionLocalProvider(LocalPreferredDisplayName provides "Nour") {
                            PartyEntry(owner, transport, mapOf(game to "Mafia"), null, 0) {}
                        }
                    }
                }
            }
            val name = onNode(hasSetTextAction() and hasText("Your player name"))
            name.assertIsDisplayed()
            onNodeWithText("Nour").assertIsDisplayed()
            name.performTextReplacement("Party alias")
            onNode(hasSetTextAction() and hasText("Room code")).performTextInput("ABCDEF")
            onNodeWithText("Request to join").assertIsDisplayed().assertIsEnabled().performClick()
            waitUntil { owner.session.value is ProcessPartySession.Peer }
            runOnIdle { assertEquals("Party alias", transport.joinName) }
        }
    }

    @Test
    fun privacy_cover_temporarily_hides_the_in_window_leave_confirmation() = runTest {
        val game = PartyGame(GameProtocolRef(GameId("mafia"), 1), 5..16)
        val transport = LobbyTransport(PlayerId("host"), PlayerId("player"))
        val owner = ProcessPartySessionOwner(transport, backgroundScope)
        assertIs<Result.Success<ProcessPartySession>>(owner.join("Guest", "ABCDEF", setOf(game)))
        val registry = DefaultGameShellRegistry(emptyList())
        val covered = mutableStateOf(false)
        runComposeUiTest {
            setContent {
                ProvideAppLanguage(AppLanguage.English) {
                    ParlorTheme(reducedMotion = true) {
                        PartyScreen(owner, transport, registry, invitation = null, privateContentCovered = covered.value,
                            backRequest = 0, onExit = {})
                    }
                }
            }
            onNodeWithText("Leave party").performScrollTo().performClick()
            onNodeWithText("Leave this party?").assertIsDisplayed()
            runOnIdle { covered.value = true }
            onNodeWithText("Party hidden while Parlor is inactive").assertIsDisplayed()
            onNodeWithText("Leave this party?").assertDoesNotExist()
            onNodeWithText("Party room").assertDoesNotExist()
            runOnIdle { covered.value = false }
            onNodeWithText("Leave this party?").assertIsDisplayed()
            onNodeWithText("Cancel").performClick()
            onNodeWithText("Leave this party?").assertDoesNotExist()
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

    private class LobbyTransport(
        host: PlayerId,
        peer: PlayerId,
        private val joinError: NetError? = null,
    ) : RoomTransport {
        override val capability = TransportCapability(false, 0, 16_384)
        val foregroundReady = MutableStateFlow(true)
        var joinName: String? = null
            private set
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
        override suspend fun join(config: JoinConfig): Result<LocalRoom, NetError> {
            joinName = config.displayName
            return joinError?.let { Result.Failure(it) } ?: Result.Success(room)
        }
    }
}
