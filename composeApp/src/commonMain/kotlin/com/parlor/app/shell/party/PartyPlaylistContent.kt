package com.parlor.app.shell.party

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.parlor.app.resources.Res
import com.parlor.app.resources.party_playlist_add
import com.parlor.app.resources.party_playlist_complete
import com.parlor.app.resources.party_playlist_completed_game
import com.parlor.app.resources.party_playlist_count
import com.parlor.app.resources.party_playlist_editor
import com.parlor.app.resources.party_playlist_empty
import com.parlor.app.resources.party_playlist_next
import com.parlor.app.resources.party_playlist_remove
import com.parlor.app.resources.party_playlist_save
import com.parlor.app.resources.party_playlist_scoreboard
import com.parlor.app.resources.party_playlist_unknown_game
import com.parlor.app.resources.party_rules_back
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorButtonVariant
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.networking.protocol.GameProtocolRef
import com.parlor.networking.protocol.MAX_PARTY_PLAYLIST_MATCHES
import com.parlor.networking.protocol.PublicPartyPlaylist
import com.parlor.session.lobby.PartyGame
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PartyPlaylistEditor(
    titles: Map<PartyGame, String>,
    seatCount: Int,
    enabled: Boolean,
    error: String?,
    onSave: (List<PartyGame>) -> Unit,
    onBack: () -> Unit,
) {
    var games by remember { mutableStateOf(emptyList<PartyGame>()) }
    PartyScaffold {
        PartyHeading(stringResource(Res.string.party_playlist_editor))
        if (games.isEmpty()) PartyDetail(stringResource(Res.string.party_playlist_empty))
        games.forEachIndexed { index, game ->
            val label = stringResource(Res.string.party_playlist_remove, index + 1, titles.getValue(game))
            PlaylistOption(label, enabled) { games = games.filterIndexed { position, _ -> position != index } }
        }
        titles.forEach { (game, title) ->
            val label = stringResource(Res.string.party_playlist_add, title)
            PlaylistOption(label, enabled && seatCount in game.playerCounts && games.size < MAX_PARTY_PLAYLIST_MATCHES) {
                games = games + game
            }
        }
        error?.let { PartyError(it) }
        PartyButton(Res.string.party_playlist_save, enabled = enabled && games.isNotEmpty()) { onSave(games) }
        PartyButton(Res.string.party_rules_back, enabled = enabled, variant = ParlorButtonVariant.Secondary, onClick = onBack)
    }
}

@Composable
internal fun PartyPlaylistScoreboard(playlist: PublicPartyPlaylist, titles: Map<PartyGame, String>) {
    PartyHeading(stringResource(Res.string.party_playlist_scoreboard))
    ParlorCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s)) {
            PartyDetail(stringResource(Res.string.party_playlist_count, playlist.completedMatchIds.size, playlist.games.size))
            val next = playlist.nextGame
            if (next == null) {
                PartyDetail(stringResource(Res.string.party_playlist_complete))
            } else {
                PartyDetail(stringResource(Res.string.party_playlist_next, playlistGameTitle(next, titles)))
            }
            playlist.games.take(playlist.completedMatchIds.size).forEachIndexed { index, game ->
                PartyDetail(stringResource(Res.string.party_playlist_completed_game, index + 1, playlistGameTitle(game, titles)))
            }
        }
    }
}

@Composable
private fun playlistGameTitle(game: GameProtocolRef, titles: Map<PartyGame, String>): String =
    titles.entries.firstOrNull { it.key.protocol == game }?.value ?: stringResource(Res.string.party_playlist_unknown_game)

@Composable
private fun PlaylistOption(label: String, enabled: Boolean, onClick: () -> Unit) {
    ParlorButton(label, onClick, label, Modifier.fillMaxWidth(), enabled, ParlorButtonVariant.Secondary)
}
