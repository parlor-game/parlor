package com.parlor.app.shell.party

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.parlor.app.resources.Res
import com.parlor.app.resources.party_approve
import com.parlor.app.resources.party_cant_start_count
import com.parlor.app.resources.party_cant_start_ready
import com.parlor.app.resources.party_change_game
import com.parlor.app.resources.party_connected
import com.parlor.app.resources.party_decline
import com.parlor.app.resources.party_ended
import com.parlor.app.resources.party_game_selected
import com.parlor.app.resources.party_host
import com.parlor.app.resources.party_leave
import com.parlor.app.resources.party_not_ready
import com.parlor.app.resources.party_offline
import com.parlor.app.resources.party_open
import com.parlor.app.resources.party_pending
import com.parlor.app.resources.party_play_again
import com.parlor.app.resources.party_player_line
import com.parlor.app.resources.party_playlist_choose_next
import com.parlor.app.resources.party_playlist_edit
import com.parlor.app.resources.party_playlist_editor
import com.parlor.app.resources.party_ready
import com.parlor.app.resources.party_roster
import com.parlor.app.resources.party_rules_back
import com.parlor.app.resources.party_rules_view
import com.parlor.app.resources.party_start
import com.parlor.app.resources.party_view_match
import com.parlor.app.resources.party_waiting
import com.parlor.app.shell.game.multiplayer.LobbyInvitation
import com.parlor.core.ids.PlayerId
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.AdmissionCountdown
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorButtonVariant
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.components.parlorSafeContentPadding
import com.parlor.designsystem.localization.asBidiArgument
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.networking.protocol.PartyLobbyPhase
import com.parlor.networking.protocol.PublicPartyLobby
import com.parlor.networking.room.RoomLifecycleState
import com.parlor.session.lobby.PartyGame
import com.parlor.session.lobby.ProcessPartySession
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal class PartyLobbyCommands(
    val selectGame: (PartyGame) -> Unit,
    val start: () -> Unit,
    val approve: (PlayerId) -> Unit,
    val reject: (PlayerId) -> Unit,
    val setReady: (Boolean) -> Unit,
    val viewRules: () -> Unit,
    val invite: () -> Unit,
    val editPlaylist: () -> Unit,
)

@Composable
internal fun PartyLobbyContent(
    session: ProcessPartySession,
    lobby: PublicPartyLobby?,
    titles: Map<PartyGame, String>,
    waiting: Boolean,
    error: String?,
    onLeave: () -> Unit,
    onViewMatch: () -> Unit,
    commands: PartyLobbyCommands,
) {
    val info by session.roomInfo.collectAsState()
    val lifecycle by session.lifecycle.collectAsState()
    val match by session.match.collectAsState()
    val foregroundReady by session.foregroundReady.collectAsState()
    val ended = lobby?.phase == PartyLobbyPhase.Ended || lifecycle == RoomLifecycleState.Expired ||
        lifecycle == RoomLifecycleState.Closed
    val connected = lifecycle == RoomLifecycleState.Active && foregroundReady && !ended
    val selected = titles.keys.firstOrNull { it.protocol == lobby?.selectedGame }
    PartyScaffold {
        PartyHeading(lobby?.roomName?.asBidiArgument() ?: stringResource(Res.string.party_open))
        if (ended) {
            PartyDetail(stringResource(Res.string.party_ended))
        } else {
            PartyDetail(stringResource(if (connected) Res.string.party_connected else Res.string.party_offline))
            selected?.let { game -> PartyDetail(stringResource(Res.string.party_game_selected, titles.getValue(game))) }
        }
        if (match != null && !ended) PartyButton(Res.string.party_view_match, onClick = onViewMatch)
        if (lobby != null && !ended) {
            if (lobby.phase == PartyLobbyPhase.Lobby) lobby.playlist?.let { PartyPlaylistScoreboard(it, titles) }
            PartySeats(lobby, info.hostPlayerId)
            if (session is ProcessPartySession.Host && lobby.phase == PartyLobbyPhase.Lobby) {
                HostPartyControls(session, lobby, titles, selected, connected && !waiting, commands)
            } else if (session is ProcessPartySession.Peer && lobby.phase == PartyLobbyPhase.Lobby) {
                PartyReadyButton(lobby, session.selfPlayerId, connected && !waiting, commands.setReady)
            }
        } else if (!ended) {
            PartyDetail(stringResource(Res.string.party_waiting))
        }
        if (!ended && selected != null && lobby?.phase == PartyLobbyPhase.Lobby) {
            PartyButton(Res.string.party_rules_view, variant = ParlorButtonVariant.Secondary, onClick = commands.viewRules)
        }
        error?.let { PartyError(it) }
        PartyButton(Res.string.party_leave, enabled = !waiting, variant = ParlorButtonVariant.Secondary, onClick = onLeave)
    }
}

@Composable
private fun PartyReadyButton(lobby: PublicPartyLobby, self: PlayerId, enabled: Boolean, onReady: (Boolean) -> Unit) {
    val ready = lobby.members.firstOrNull { it.playerId == self }?.ready == true
    val next = lobby.playlist?.nextGame
    PartyButton(
        if (ready) Res.string.party_not_ready else Res.string.party_ready,
        enabled = enabled && (next == null || next == lobby.selectedGame),
    ) { onReady(!ready) }
}

@Composable
internal fun PartyRulesContent(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    PartyScaffold {
        PartyHeading(title)
        content()
        PartyButton(Res.string.party_rules_back, variant = ParlorButtonVariant.Secondary, onClick = onBack)
    }
}

@Composable
private fun PartySeats(lobby: PublicPartyLobby, host: PlayerId) {
    PartyHeading(stringResource(Res.string.party_roster))
    ParlorCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s)) {
            lobby.members.forEach { player ->
                val status = when {
                    !player.connected -> Res.string.party_offline
                    player.playerId == host -> Res.string.party_host
                    player.ready -> Res.string.party_ready
                    else -> Res.string.party_not_ready
                }
                PartyDetail(stringResource(Res.string.party_player_line, player.displayName.asBidiArgument(), stringResource(status)))
            }
        }
    }
}

@Composable
private fun HostPartyControls(
    host: ProcessPartySession.Host,
    lobby: PublicPartyLobby,
    titles: Map<PartyGame, String>,
    selected: PartyGame?,
    enabled: Boolean,
    commands: PartyLobbyCommands,
) {
    val info by host.roomInfo.collectAsState()
    val pending by host.approvals.collectAsState()
    LobbyInvitation(info.code, info.hostDisplayName, info.roomTag, onInvite = commands.invite)
    if (pending.isNotEmpty()) {
        PartyHeading(stringResource(Res.string.party_pending))
        pending.forEach { request ->
            ParlorCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s)) {
                    PartyDetail(request.displayName.asBidiArgument())
                    AdmissionCountdown(request.approvalDeadline)
                    PartyButton(Res.string.party_approve, enabled = enabled) { commands.approve(request.playerId) }
                    PartyButton(Res.string.party_decline, enabled = enabled, variant = ParlorButtonVariant.Secondary) {
                        commands.reject(request.playerId)
                    }
                }
            }
        }
    }
    val playerCount = lobby.members.count { it.connected }
    val needsReady = lobby.members.any { it.playerId != info.hostPlayerId && it.connected && !it.ready }
    val supportsCount = selected?.playerCounts?.contains(playerCount) == true
    val completedGame by host.lastCompletedGame.collectAsState()
    val playlist = lobby.playlist
    val next = playlist?.nextGame
    val canStartSelected = next == null || next == lobby.selectedGame
    val start = if (next == null && lobby.selectedGame.gameId == completedGame) Res.string.party_play_again
    else Res.string.party_start
    val canChange = enabled && pending.isEmpty()
    if (canStartSelected) {
        PartyButton(start, enabled = canChange && supportsCount && !needsReady, onClick = commands.start)
    } else {
        titles.keys.firstOrNull { it.protocol == next }?.let { game ->
            PartyButton(Res.string.party_playlist_choose_next, enabled = canChange) { commands.selectGame(game) }
        }
    }
    if (canStartSelected && selected != null && !supportsCount) {
        PartyDetail(stringResource(
            Res.string.party_cant_start_count, selected.playerCounts.first.toString(), selected.playerCounts.last.toString(),
        ))
    } else if (canStartSelected && needsReady) {
        PartyDetail(stringResource(Res.string.party_cant_start_ready))
    }
    if (next == null || playlist?.completedMatchIds?.isEmpty() == true) {
        val editorLabel = if (next == null) Res.string.party_playlist_editor else Res.string.party_playlist_edit
        PartyButton(editorLabel, enabled = canChange,
            variant = ParlorButtonVariant.Secondary, onClick = commands.editPlaylist)
    }
    if (next == null) {
        PartyHeading(stringResource(Res.string.party_change_game))
        PartyGameOptions(titles, selected, canChange, commands.selectGame)
    }
}

@Composable
internal fun PartyGameOptions(
    titles: Map<PartyGame, String>,
    selected: PartyGame?,
    enabled: Boolean,
    onSelect: (PartyGame) -> Unit,
) {
    titles.forEach { (game, title) ->
        val label = if (selected == game) stringResource(Res.string.party_game_selected, title) else title
        ParlorButton(label, { onSelect(game) }, label, modifier = Modifier.fillMaxWidth(),
            enabled = enabled && game != selected, variant = ParlorButtonVariant.Secondary)
    }
}

@Composable
internal fun PartyButton(
    text: StringResource,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: ParlorButtonVariant = ParlorButtonVariant.Primary,
    onClick: () -> Unit,
) {
    val label = stringResource(text)
    ParlorButton(label, onClick, label, modifier.fillMaxWidth(), enabled, variant)
}

@Composable
internal fun PartyHeading(text: String) {
    Text(text, color = ParlorTheme.colors.textPrimary, style = ParlorTheme.typography.headingLarge,
        modifier = Modifier.semantics { heading() })
}

@Composable
internal fun PartyDetail(text: String) {
    Text(text, color = ParlorTheme.colors.textSecondary, style = ParlorTheme.typography.bodyLarge)
}

@Composable
internal fun PartyError(text: String) {
    Text(text, color = ParlorTheme.colors.textPrimary, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

@Composable
internal fun PartyScaffold(content: @Composable ColumnScope.() -> Unit) {
    HeroBackdrop(Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).parlorSafeContentPadding(ParlorTheme.spacing.l),
                verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
                content = content,
            )
        }
    }
}
