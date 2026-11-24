package com.parlor.app.shell.multiplayer

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.parlor.app.lifecycle.AppLifecycleCoordinator
import com.parlor.core.ids.GameId
import com.parlor.core.result.Result
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.networking.room.DiscoveredRoom
import com.parlor.networking.room.DiscoveredRoomSelection
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.transport.HostConfig
import com.parlor.networking.transport.HostedGameProtocol
import com.parlor.networking.transport.RoomTransport
import com.parlor.networking.transport.TransportCapability
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.koin.compose.KoinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NearbyRoomSelectionTest {
    @Test
    fun selected_room_still_requires_a_code_and_keeps_its_opaque_target() = runComposeUiTest {
        val game = HostedGameProtocol(GameId("mafia"), 2)
        val selection = object : DiscoveredRoomSelection {}
        val lifecycle = AppLifecycleCoordinator({}, {}).also(AppLifecycleCoordinator::notifyActive)
        val transport = NearbyFixture(game, selection)
        val submissions = mutableListOf<Pair<String, DiscoveredRoomSelection?>>()

        setContent {
            KoinApplication(application = { modules(module { single { lifecycle } }) }) {
                ProvideAppLanguage(AppLanguage.English) {
                    ParlorTheme(reducedMotion = true) {
                        JoinPromptScreen(
                            onConfirm = { code, target -> submissions += code to target },
                            onCancel = {},
                            transport = transport,
                            game = game,
                        )
                    }
                }
            }
        }
        onNodeWithContentDescription("Find nearby rooms").performClick()
        onNodeWithContentDescription("K7QX", substring = true).performClick()
        waitForIdle()
        assertEquals(1, transport.started)
        assertEquals(1, transport.stopped)
        val join = onNodeWithContentDescription("Connect to the host using the entered room code.")
        join.assertIsNotEnabled()
        onNode(hasSetTextAction()).performTextInput("ABCDEF")
        join.performClick()
        assertEquals("ABCDEF" to selection, submissions.single())
    }

    private class NearbyFixture(
        private val expected: HostedGameProtocol,
        private val selection: DiscoveredRoomSelection,
    ) : RoomTransport {
        override val capability = TransportCapability(true, 25, 8_192)
        var started = 0
        var stopped = 0

        override fun discoverRooms(game: HostedGameProtocol): Flow<Result<List<DiscoveredRoom>, NetError>> = flow {
            assertEquals(expected, game)
            started++
            try {
                emit(Result.Success(listOf(DiscoveredRoom("", "K7QX", roomTag = "K7QX", selection = selection))))
                awaitCancellation()
            } finally {
                stopped++
            }
        }

        override suspend fun host(config: HostConfig): Result<LocalRoom, NetError> = error("Unexpected host")
        override suspend fun join(code: String, displayName: String): Result<LocalRoom, NetError> = error("Unexpected join")
    }
}
