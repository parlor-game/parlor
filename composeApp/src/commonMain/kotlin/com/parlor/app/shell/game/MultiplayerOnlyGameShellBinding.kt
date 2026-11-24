package com.parlor.app.shell.game

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.parlor.app.shell.game.multiplayer.MultiplayerGameSpec
import com.parlor.app.shell.game.multiplayer.MultiplayerShellContent
import com.parlor.app.shell.game.multiplayer.PartyMatchContent
import com.parlor.engine.action.GameAction
import com.parlor.engine.event.GameEvent
import com.parlor.engine.state.GameState
import com.parlor.networking.protocol.SessionEndReason
import com.parlor.session.multidevice.ProcessMultiplayerSession

/** Reuses the registry's existing Host/Join routes without installing a second navigation engine. */
internal abstract class MultiplayerOnlyGameShellBinding<S : GameState, A : GameAction, E : GameEvent> :
    GameShellBinding, PartyGameShell, MultiplayerGameSpec<S, A, E> {
    override val capabilities = GameShellCapabilities(setOf(GameEntryMode.Host, GameEntryMode.Join))
    override val multiplayerContract: GameShellMultiplayerContract
        get() = GameShellMultiplayerContract(definition.id, version, definition.supportedPlayerCounts)

    @Composable
    final override fun PartyRules(playerCount: Int) {
        Settings(defaultCaseId, playerCount, enabled = false, onChange = {})
    }

    @Composable
    override fun PartyMatch(
        session: ProcessMultiplayerSession,
        onEndHostMatch: (SessionEndReason) -> Unit,
        onReturnToParty: () -> Unit,
        onHostStarted: suspend () -> Unit,
        modifier: Modifier,
    ) = PartyMatchContent(this, session, onEndHostMatch, onReturnToParty, onHostStarted, modifier)

    @Composable
    override fun Content(
        launch: GameShellLaunch, onExit: () -> Unit, backRequest: GameShellBackRequest, modifier: Modifier,
    ) {
        require(launch.gameId == definition.id)
        MultiplayerShellContent(this, launch, capabilities, catalogPresentation(), onExit, backRequest, modifier)
    }
}
