package com.parlor.app.shell.game

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import com.parlor.core.ids.GameId
import com.parlor.core.ids.RoomId
import com.parlor.networking.invitation.JoinLinkCodec
import com.parlor.networking.transport.HostedGameProtocol
import com.parlor.engine.definition.GameDefinition
import com.parlor.engine.testing.fakes.RoundRobinAnnounceGame
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertIs
import kotlin.test.assertNull

/** Desktop-runtime proof that shell resolution executes a third game's content. */
class GameShellRegistryCompositionTest {
    @Test
    fun invitations_can_only_open_the_registered_game_and_its_exact_version() {
        val definition = RoundRobinAnnounceGame()
        val binding = RenderingFixtureBinding(definition) { _, _ -> }
        val router = GameShellRouter(DefaultGameShellRegistry(listOf(binding)))
        val roomId = RoomId("a".repeat(32))
        val game = HostedGameProtocol(definition.id, 1)
        val fingerprint = "p2f1-zlmerarbaugm753v5mvipavkkhwxbvlu3cpx4unzvuvov7zu7dkq"
        fun invite(target: HostedGameProtocol) = assertIs<JoinLinkCodec.Decoded.Valid>(JoinLinkCodec.decode(
            assertNotNull(JoinLinkCodec.encode(target, roomId, "K7QX", "ABCDEF", fingerprint)),
        )).invitation

        val launch = assertNotNull(router.joinByInvitation(invite(game)))
        assertEquals(game.gameId, launch.gameId)
        assertEquals(binding, router.bindingFor(launch))
        assertNull(router.joinByInvitation(invite(game.copy(gameVersion = 2))))
        assertNull(router.joinByInvitation(invite(game.copy(gameId = GameId("another-game")))))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun registered_fixture_content_is_composed_by_the_resolved_binding() = runTest {
        val definition = RoundRobinAnnounceGame()
        var renderedLaunch: GameShellLaunch? = null
        var renderedBackRequest: GameShellBackRequest? = null
        val binding = RenderingFixtureBinding(definition) { launch, backRequest ->
            renderedLaunch = launch
            renderedBackRequest = backRequest
        }
        val router = GameShellRouter(DefaultGameShellRegistry(listOf(binding)))
        val launch = assertNotNull(router.newGame(definition.id))
        val compositionContext = coroutineContext + ImmediateFrameClock
        val recomposer = Recomposer(compositionContext)
        val composition = Composition(UnitApplier(), recomposer)
        val recomposerJob = launch(ImmediateFrameClock) {
            recomposer.runRecomposeAndApplyChanges()
        }

        try {
            composition.setContent {
                assertNotNull(router.bindingFor(launch)).Content(
                    launch = launch,
                    onExit = {},
                    backRequest = GameShellBackRequest(7L),
                )
            }
            runCurrent()
            assertEquals(launch, renderedLaunch)
            assertEquals(GameShellBackRequest(7L), renderedBackRequest)
        } finally {
            composition.dispose()
            recomposer.close()
            recomposerJob.join()
        }
    }
}

private class RenderingFixtureBinding(
    override val definition: GameDefinition<*, *, *>,
    private val onRendered: (GameShellLaunch, GameShellBackRequest) -> Unit,
) : GameShellBinding {
    override val capabilities = GameShellCapabilities(
        setOf(GameEntryMode.PassAndPlay, GameEntryMode.Host, GameEntryMode.Join),
    )
    override val multiplayerContract = GameShellMultiplayerContract(
        gameId = definition.id,
        gameVersion = 1,
        supportedPlayerCounts = definition.supportedPlayerCounts,
    )

    @Composable
    override fun catalogPresentation() = GameCatalogPresentation(
        title = definition.id.raw,
        subtitle = "fixture",
        tagline = "fixture",
        openLabel = "open",
        openContentDescription = "open fixture",
    )

    @Composable
    override fun Content(
        launch: GameShellLaunch,
        onExit: () -> Unit,
        backRequest: GameShellBackRequest,
        modifier: Modifier,
    ) {
        SideEffect { onRendered(launch, backRequest) }
    }
}

private class UnitApplier : AbstractApplier<Unit>(Unit) {
    override fun insertTopDown(index: Int, instance: Unit) = Unit
    override fun insertBottomUp(index: Int, instance: Unit) = Unit
    override fun remove(index: Int, count: Int) = Unit
    override fun move(from: Int, to: Int, count: Int) = Unit
    override fun onClear() = Unit
}

private object ImmediateFrameClock : MonotonicFrameClock {
    override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R = onFrame(0L)
}
