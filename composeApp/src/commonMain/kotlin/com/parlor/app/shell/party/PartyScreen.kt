package com.parlor.app.shell.party

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.parlor.app.resources.Res
import com.parlor.app.resources.party_action_error
import com.parlor.app.resources.party_cancel
import com.parlor.app.resources.party_cant_switch
import com.parlor.app.resources.party_end_match
import com.parlor.app.resources.party_end_match_confirm
import com.parlor.app.resources.party_leave
import com.parlor.app.resources.party_leave_confirm
import com.parlor.app.resources.party_private_cover
import com.parlor.app.shell.game.GameShellRegistry
import com.parlor.app.shell.game.PartyGameShell
import com.parlor.app.shell.game.multiplayer.roomGameError
import com.parlor.core.ids.SessionId
import com.parlor.core.result.Result
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorButtonVariant
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.networking.invitation.PartyJoinInvitation
import com.parlor.networking.protocol.CommandStatus
import com.parlor.networking.protocol.GameProtocolRef
import com.parlor.networking.protocol.SessionEndReason
import com.parlor.networking.room.NetError
import com.parlor.networking.transport.RoomTransport
import com.parlor.session.lobby.PartyGame
import com.parlor.session.lobby.PartyRejection
import com.parlor.session.lobby.ProcessPartySession
import com.parlor.session.lobby.ProcessPartySessionOwner
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PartyScreen(
    owner: ProcessPartySessionOwner,
    transport: RoomTransport,
    registry: GameShellRegistry,
    invitation: PartyJoinInvitation?,
    privateContentCovered: Boolean,
    backRequest: Long,
    onExit: () -> Unit,
) {
    val current by owner.session.collectAsState()
    val session = current
    val titles = linkedMapOf<PartyGame, String>()
    registry.all.forEach { binding ->
        val contract = binding.multiplayerContract
        if (binding is PartyGameShell && contract != null) {
            val option = PartyGame(GameProtocolRef(contract.gameId, contract.gameVersion), contract.supportedPlayerCounts)
            titles[option] = binding.catalogPresentation().title
        }
    }
    if (privateContentCovered) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(Res.string.party_private_cover), color = ParlorTheme.colors.textSecondary)
        }
    } else if (session == null) {
        PartyEntry(owner, transport, titles, invitation, backRequest, onExit)
    } else {
        ActiveParty(owner, session, registry, titles, backRequest, onExit)
    }
}

private sealed interface PartyConfirmation {
    data object Leave : PartyConfirmation
    data class EndMatch(val id: SessionId, val reason: SessionEndReason) : PartyConfirmation
}

private class PartyActions {
    var waiting by mutableStateOf(false)
        private set
    var networkError by mutableStateOf<NetError?>(null)
        private set
    var rejection by mutableStateOf<PartyRejection?>(null)
        private set

    suspend fun run(action: suspend PartyActions.() -> Unit) {
        if (waiting) return
        waiting = true
        networkError = null
        rejection = null
        try {
            action()
        } finally {
            waiting = false
        }
    }

    fun showNetwork(result: Result<*, NetError>): Boolean = when (result) {
        is Result.Success -> true
        is Result.Failure -> false.also { networkError = result.error }
    }

    fun showParty(result: Result<*, PartyRejection>): Boolean = when (result) {
        is Result.Success -> true
        is Result.Failure -> false.also { rejection = result.error }
    }
}

@Composable
private fun ActiveParty(
    owner: ProcessPartySessionOwner,
    session: ProcessPartySession,
    registry: GameShellRegistry,
    titles: Map<PartyGame, String>,
    backRequest: Long,
    onExit: () -> Unit,
) {
    val lobby by session.lobby.collectAsState()
    val match by session.match.collectAsState()
    val activeMatch = match
    val protocol = activeMatch?.room?.matchProtocol
    val matchUi = protocol?.let { registry.byId(it.gameId) as? PartyGameShell }
    val scope = rememberCoroutineScope()
    val actions = remember(session) { PartyActions() }
    var hiddenForMatch by remember(session) { mutableStateOf<SessionId?>(null) }
    var confirm by remember(session) { mutableStateOf<PartyConfirmation?>(null) }
    val showingMatch = protocol != null && hiddenForMatch != protocol.sessionId
    val host = session as? ProcessPartySession.Host
    val requestExit = {
        if (confirm != null) {
            confirm = null
        } else if (showingMatch && protocol != null && host != null) {
            confirm = PartyConfirmation.EndMatch(protocol.sessionId, SessionEndReason.Cancelled)
        } else if (showingMatch && protocol != null) {
            hiddenForMatch = protocol.sessionId
        } else {
            confirm = PartyConfirmation.Leave
        }
    }
    LaunchedEffect(backRequest) { if (backRequest > 0) requestExit() }
    val error = when {
        actions.networkError != null -> roomGameError(checkNotNull(actions.networkError))
        actions.rejection == PartyRejection.IneligibleCount || actions.rejection == PartyRejection.IncompatibleGame ->
            stringResource(Res.string.party_cant_switch)
        actions.rejection != null -> stringResource(Res.string.party_action_error)
        else -> null
    }

    if (showingMatch && activeMatch != null && protocol != null && matchUi != null) {
        matchUi.PartyMatch(
            session = activeMatch,
            onEndHostMatch = { reason ->
                if (host != null && reason == SessionEndReason.Completed) {
                    scope.launch { actions.run { showParty(host.returnToLobby(protocol.sessionId, reason)) } }
                } else {
                    confirm = PartyConfirmation.EndMatch(protocol.sessionId, reason)
                }
            },
            onReturnToParty = { hiddenForMatch = protocol.sessionId },
            onHostStarted = { if (host != null) actions.showParty(host.confirmStarted(protocol.sessionId)) },
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        PartyLobbyContent(
            session = session,
            lobby = lobby,
            titles = titles,
            waiting = actions.waiting,
            error = error,
            onLeave = { confirm = PartyConfirmation.Leave },
            onViewMatch = { hiddenForMatch = null },
            commands = PartyLobbyCommands(
                selectGame = { game -> scope.launch { actions.run { host?.let { showParty(it.selectGame(game)) } } } },
                start = { scope.launch { actions.run { host?.let { showParty(it.start()) } } } },
                approve = { player -> scope.launch { actions.run { host?.let { showNetwork(it.approve(player)) } } } },
                reject = { player -> scope.launch { actions.run { host?.let { showNetwork(it.reject(player)) } } } },
                setReady = { ready ->
                    scope.launch {
                        actions.run {
                            val peer = session as? ProcessPartySession.Peer ?: return@run
                            val result = peer.setReady(ready)
                            if (showNetwork(result) && (result as Result.Success).data != CommandStatus.Applied) {
                                showParty(Result.Failure(PartyRejection.StaleRevision))
                            }
                        }
                    }
                },
            ),
        )
    }
    confirm?.let { request ->
        PartyConfirmationDialog(request, actions.waiting, { confirm = null }) {
            scope.launch {
                actions.run {
                    when (request) {
                        PartyConfirmation.Leave -> if (showNetwork(owner.leave(session))) onExit()
                        is PartyConfirmation.EndMatch -> if (host != null) {
                            if (showParty(host.returnToLobby(request.id, request.reason))) confirm = null
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PartyConfirmationDialog(
    request: PartyConfirmation,
    waiting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val leave = request is PartyConfirmation.Leave
    val title = stringResource(if (leave) Res.string.party_leave_confirm else Res.string.party_end_match_confirm)
    val action = stringResource(if (leave) Res.string.party_leave else Res.string.party_end_match)
    val cancel = stringResource(Res.string.party_cancel)
    AlertDialog(
        onDismissRequest = { if (!waiting) onDismiss() },
        title = { Text(title) },
        confirmButton = {
            ParlorButton(action, onConfirm, action, loading = waiting, variant = ParlorButtonVariant.Destructive)
        },
        dismissButton = { ParlorButton(cancel, onDismiss, cancel, enabled = !waiting, variant = ParlorButtonVariant.Ghost) },
    )
}
