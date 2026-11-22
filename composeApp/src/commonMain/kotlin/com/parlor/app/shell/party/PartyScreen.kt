package com.parlor.app.shell.party

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.parlor.app.permissions.rememberP2pPermissionGate
import com.parlor.app.resources.Res
import com.parlor.app.resources.party_invite_players
import com.parlor.app.resources.party_action_error
import com.parlor.app.resources.party_cancel
import com.parlor.app.resources.party_cant_switch
import com.parlor.app.resources.party_end_match
import com.parlor.app.resources.party_end_match_confirm
import com.parlor.app.resources.party_leave
import com.parlor.app.resources.party_leave_confirm
import com.parlor.app.resources.party_invite_loading
import com.parlor.app.resources.party_rules_back
import com.parlor.app.resources.party_private_cover
import com.parlor.app.shell.game.GameShellRegistry
import com.parlor.app.shell.game.PartyGameShell
import com.parlor.app.shell.game.P2pPermissionRoute
import com.parlor.app.shell.game.multiplayer.roomGameError
import com.parlor.core.ids.SessionId
import com.parlor.core.result.Result
import com.parlor.designsystem.components.InvitationContent
import com.parlor.designsystem.components.ParlorButtonVariant
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.networking.invitation.PartyJoinInvitation
import com.parlor.networking.protocol.CommandStatus
import com.parlor.networking.protocol.GameProtocolRef
import com.parlor.networking.protocol.PartyLobbyPhase
import com.parlor.networking.protocol.SessionEndReason
import com.parlor.networking.protocol.SessionProtocol
import com.parlor.networking.room.NetError
import com.parlor.networking.room.RoomLifecycleState
import com.parlor.networking.transport.RoomTransport
import com.parlor.session.lobby.PartyGame
import com.parlor.session.lobby.PartyRejection
import com.parlor.session.lobby.ProcessPartySession
import com.parlor.session.lobby.ProcessPartySessionOwner
import kotlinx.coroutines.CoroutineScope
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
    val gate = rememberP2pPermissionGate(transport.localNetworkAccess)
    var permissionAcknowledged by remember { mutableStateOf(false) }
    val titles = linkedMapOf<PartyGame, String>()
    registry.all.forEach { binding ->
        val contract = binding.multiplayerContract
        if (binding is PartyGameShell && contract != null) {
            val option = PartyGame(GameProtocolRef(contract.gameId, contract.gameVersion), contract.supportedPlayerCounts)
            titles[option] = binding.catalogPresentation().title
        }
    }
    if (session != null) {
        ActiveParty(owner, session, registry, titles, privateContentCovered, backRequest, onExit)
    } else if (privateContentCovered) {
        PartyPrivacyCover()
    } else if (permissionAcknowledged) {
        PartyEntry(owner, transport, titles, invitation, backRequest, onExit)
    } else {
        LaunchedEffect(backRequest) { if (backRequest > 0) onExit() }
        P2pPermissionRoute(gate, onContinue = { permissionAcknowledged = true }, onBack = onExit)
    }
}

@Composable
private fun PartyPrivacyCover() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(Res.string.party_private_cover), color = ParlorTheme.colors.textSecondary)
    }
}

private enum class PartyLobbyPage { Rules, Invite }

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
    privateContentCovered: Boolean,
    backRequest: Long,
    onExit: () -> Unit,
) {
    val observedLobby by session.lobby.collectAsState()
    val lobby = observedLobby
    val lifecycle by session.lifecycle.collectAsState()
    val match by session.match.collectAsState()
    val activeMatch = match
    val protocol = activeMatch?.room?.matchProtocol
    val matchUi = protocol?.let { registry.byId(it.gameId) as? PartyGameShell }
    val scope = rememberCoroutineScope()
    val actions = remember(session) { PartyActions() }
    var hiddenForMatch by remember(session) { mutableStateOf<SessionId?>(null) }
    var confirm by remember(session) { mutableStateOf<PartyConfirmation?>(null) }
    var lobbyPage by remember(session, lobby?.selectedGame, lobby?.phase) { mutableStateOf<PartyLobbyPage?>(null) }
    val showingMatch = matchVisible(protocol, hiddenForMatch, lifecycle)
    val host = session as? ProcessPartySession.Host
    val requestExit = {
        if (confirm != null) {
            confirm = null
        } else if (lobbyPage != null) {
            lobbyPage = null
        } else if (showingMatch && protocol != null) {
            if (host == null) {
                hiddenForMatch = protocol.sessionId
            } else {
                confirm = PartyConfirmation.EndMatch(protocol.sessionId, SessionEndReason.Cancelled)
            }
        } else {
            confirm = PartyConfirmation.Leave
        }
    }
    LaunchedEffect(backRequest) { if (backRequest > 0 && !privateContentCovered) requestExit() }
    val error = partyError(actions)

    val confirmation = confirm
    val selected = titles.keys.firstOrNull { it.protocol == lobby?.selectedGame }
    val visiblePage = lobbyPage.takeIf { lobby?.phase == PartyLobbyPhase.Lobby }
    if (privateContentCovered) {
        PartyPrivacyCover()
    } else if (confirmation != null) {
        PartyConfirmationContent(confirmation, actions.waiting, error, { confirm = null }) {
            scope.launch {
                actions.run {
                    if (applyConfirmation(confirmation, owner, session)) {
                        confirm = null
                        if (confirmation == PartyConfirmation.Leave) onExit()
                    }
                }
            }
        }
    } else if (visiblePage == PartyLobbyPage.Rules && selected != null) {
        PartyRulesContent(titles.getValue(selected), onBack = { lobbyPage = null }) {
            val binding = registry.byId(selected.protocol.gameId) as? PartyGameShell
            binding?.PartyRules(lobby?.members.orEmpty().count { it.connected })
        }
    } else if (visiblePage == PartyLobbyPage.Invite && host != null) {
        PartyInvitationContent(host, onBack = { lobbyPage = null })
    } else if (showingMatch && activeMatch != null && protocol != null && matchUi != null) {
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
            commands = partyLobbyCommands(session, scope, actions, viewRules = { lobbyPage = PartyLobbyPage.Rules },
                invite = { lobbyPage = PartyLobbyPage.Invite }),
        )
    }
}

private suspend fun PartyActions.applyConfirmation(
    confirmation: PartyConfirmation,
    owner: ProcessPartySessionOwner,
    session: ProcessPartySession,
): Boolean = when (confirmation) {
    PartyConfirmation.Leave -> showNetwork(owner.leave(session))
    is PartyConfirmation.EndMatch -> (session as? ProcessPartySession.Host)?.let { host ->
        showParty(host.returnToLobby(confirmation.id, confirmation.reason))
    } == true
}

private fun partyLobbyCommands(
    session: ProcessPartySession,
    scope: CoroutineScope,
    actions: PartyActions,
    viewRules: () -> Unit,
    invite: () -> Unit,
): PartyLobbyCommands {
    val host = session as? ProcessPartySession.Host
    return PartyLobbyCommands(
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
        viewRules = viewRules,
        invite = invite,
    )
}

@Composable
private fun partyError(actions: PartyActions): String? = when {
    actions.networkError != null -> roomGameError(checkNotNull(actions.networkError))
    actions.rejection == PartyRejection.IneligibleCount || actions.rejection == PartyRejection.IncompatibleGame ->
        stringResource(Res.string.party_cant_switch)
    actions.rejection != null -> stringResource(Res.string.party_action_error)
    else -> null
}

private data class PartyInvitation(val link: String?)

@Composable
private fun PartyInvitationContent(host: ProcessPartySession.Host, onBack: () -> Unit) {
    val invitation by produceState<PartyInvitation?>(null, host) {
        value = PartyInvitation(host.createInvitation())
    }
    PartyScaffold {
        PartyHeading(stringResource(Res.string.party_invite_players))
        val loaded = invitation
        if (loaded == null) {
            PartyDetail(stringResource(Res.string.party_invite_loading))
        } else {
            InvitationContent(loaded.link)
        }
        PartyButton(Res.string.party_rules_back, variant = ParlorButtonVariant.Secondary, onClick = onBack)
    }
}

@Composable
private fun PartyConfirmationContent(
    request: PartyConfirmation,
    waiting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val leave = request is PartyConfirmation.Leave
    val title = if (leave) Res.string.party_leave_confirm else Res.string.party_end_match_confirm
    val action = if (leave) Res.string.party_leave else Res.string.party_end_match
    PartyScaffold {
        PartyHeading(stringResource(title))
        error?.let { PartyError(it) }
        PartyButton(action, enabled = !waiting, variant = ParlorButtonVariant.Destructive, onClick = onConfirm)
        PartyButton(Res.string.party_cancel, enabled = !waiting, variant = ParlorButtonVariant.Ghost, onClick = onDismiss)
    }
}

private fun matchVisible(
    protocol: SessionProtocol?,
    hiddenMatch: SessionId?,
    lifecycle: RoomLifecycleState,
): Boolean = protocol != null && hiddenMatch != protocol.sessionId &&
    lifecycle != RoomLifecycleState.Closed && lifecycle != RoomLifecycleState.Expired
