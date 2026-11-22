package com.parlor.app.shell.game.multiplayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import com.parlor.app.resources.Res
import com.parlor.app.resources.room_game_error
import com.parlor.app.resources.room_game_starting
import com.parlor.core.ids.CaseId
import com.parlor.engine.action.GameAction
import com.parlor.engine.event.GameEvent
import com.parlor.engine.state.GameState
import com.parlor.networking.protocol.SessionEndReason
import com.parlor.session.multidevice.ProcessMultiplayerSession
import com.parlor.session.multidevice.RetainedValueResult
import org.jetbrains.compose.resources.stringResource

/** Uses the registered game's real start barrier and seated projection on an already scoped match. */
@Composable
internal fun <S : GameState, A : GameAction, E : GameEvent> PartyMatchContent(
    spec: MultiplayerGameSpec<S, A, E>,
    session: ProcessMultiplayerSession,
    onEndHostMatch: (SessionEndReason) -> Unit,
    onReturnToParty: () -> Unit,
    onStarted: suspend () -> Unit,
    modifier: Modifier,
) {
    val protocol = checkNotNull(session.room.matchProtocol)
    require(protocol.gameId == spec.definition.id && protocol.gameVersion == spec.version)
    if (session.room.isHost) {
        val roster = checkNotNull(session.frozenRoster.value)
        val settings = session.route.contentId?.let(::CaseId) ?: spec.defaultCaseId
        val cancel = { onEndHostMatch(SessionEndReason.Cancelled) }
        if (spec.acceptsSettings(settings.raw, roster.size + 1)) {
            HostedGamePanel(spec, session, roster, settings, cancel, cancel, false, modifier, onStarted)
        } else {
            RoomGameStatus(stringResource(Res.string.room_game_error), cancel, modifier)
        }
    } else {
        PartyPeerMatchContent(spec, session, onReturnToParty, modifier)
    }
}

@Composable
private fun <S : GameState, A : GameAction, E : GameEvent> PartyPeerMatchContent(
    spec: MultiplayerGameSpec<S, A, E>,
    session: ProcessMultiplayerSession,
    onExit: () -> Unit,
    modifier: Modifier,
) {
    val lookup by produceState<RetainedValueResult<*>?>(null, session) {
        value = session.getOrCreateCheckpoint(spec.kind("party-start")) {
            GamePeerStartCheckpoint(spec.kind("party-start"))
        }
    }
    val checkpoint = (lookup as? RetainedValueResult.Ready<*>)?.value as? GamePeerStartCheckpoint
    if (checkpoint == null) {
        RoomGameStatus(stringResource(if (lookup == null) Res.string.room_game_starting else Res.string.room_game_error),
            onExit, modifier)
        return
    }
    LaunchedEffect(session, checkpoint) { checkpoint.start(spec, session, owner = null) }
    val start by checkpoint.state.collectAsState()
    when (val state = start) {
        GameStartState.Waiting -> RoomGameStatus(stringResource(Res.string.room_game_starting), onExit, modifier)
        is GameStartState.Failed -> RoomGameStatus(stringResource(Res.string.room_game_error), onExit, modifier,
            body = roomGameError(state.error))
        is GameStartState.Started -> PeerGamePanel(spec, session, state.value, onExit, onExit, false, modifier)
    }
}
