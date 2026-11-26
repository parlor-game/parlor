package com.parlor.app.shell.party

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.parlor.app.resources.Res
import com.parlor.app.resources.party_playlist_add
import com.parlor.app.resources.party_playlist_complete
import com.parlor.app.resources.party_playlist_completed_game
import com.parlor.app.resources.party_playlist_count
import com.parlor.app.resources.party_playlist_edit
import com.parlor.app.resources.party_playlist_editor
import com.parlor.app.resources.party_playlist_empty
import com.parlor.app.resources.party_playlist_next
import com.parlor.app.resources.party_playlist_remove
import com.parlor.app.resources.party_playlist_save
import com.parlor.app.resources.party_playlist_scoreboard
import com.parlor.app.resources.party_playlist_unknown_game
import com.parlor.app.resources.party_standings
import com.parlor.app.resources.party_standings_policy
import com.parlor.app.resources.party_standings_row
import com.parlor.app.resources.party_standings_scored_count
import com.parlor.app.resources.party_preset_delete
import com.parlor.app.resources.party_preset_full
import com.parlor.app.resources.party_preset_heading
import com.parlor.app.resources.party_preset_incompatible
import com.parlor.app.resources.party_preset_load
import com.parlor.app.resources.party_preset_save
import com.parlor.app.resources.party_preset_write_failed
import com.parlor.app.resources.party_rules_back
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorButtonVariant
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.localization.asBidiArgument
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.networking.protocol.GameProtocolRef
import com.parlor.networking.protocol.MAX_PARTY_PLAYLIST_MATCHES
import com.parlor.networking.protocol.PublicPartyPlaylist
import com.parlor.networking.protocol.PublicPartyScoredSeat
import com.parlor.session.lobby.PartyGame
import com.parlor.storage.settings.PartyPlaylistPreset
import com.parlor.storage.settings.SettingsPersistenceException
import com.parlor.storage.settings.SettingsStore
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PartyPlaylistEditor(
    titles: Map<PartyGame, String>,
    initialGames: List<PartyGame>,
    seatCount: Int,
    enabled: Boolean,
    error: String?,
    settings: SettingsStore,
    onSave: (List<PartyGame>) -> Unit,
    onBack: () -> Unit,
) {
    var games by remember { mutableStateOf(initialGames) }
    var writeInFlight by remember { mutableStateOf(false) }
    var writeFailed by remember { mutableStateOf(false) }
    val presets by settings.partyPlaylistPresets.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val editable = enabled && !writeInFlight
    val write: (suspend () -> Unit) -> Unit = { mutation ->
        if (!writeInFlight) {
            writeInFlight = true
            writeFailed = false
            scope.launch {
                try {
                    mutation()
                } catch (_: SettingsPersistenceException) {
                    writeFailed = true
                } finally {
                    writeInFlight = false
                }
            }
        }
    }
    PartyScaffold {
        val editorLabel = if (initialGames.isEmpty()) Res.string.party_playlist_editor else Res.string.party_playlist_edit
        PartyHeading(stringResource(editorLabel))
        if (games.isEmpty()) PartyDetail(stringResource(Res.string.party_playlist_empty))
        games.forEachIndexed { index, game ->
            val label = stringResource(Res.string.party_playlist_remove, index + 1, titles.getValue(game))
            PlaylistOption(label, editable) { games = games.filterIndexed { position, _ -> position != index } }
        }
        titles.forEach { (game, title) ->
            val label = stringResource(Res.string.party_playlist_add, title)
            PlaylistOption(label, editable && seatCount in game.playerCounts && games.size < MAX_PARTY_PLAYLIST_MATCHES) {
                games = games + game
            }
        }
        val draft = games.takeIf { it.isNotEmpty() }?.let { PartyPlaylistPreset(it.map { game -> game.protocol.gameId }) }
        PartyButton(Res.string.party_preset_save, enabled = editable && draft != null && draft !in presets &&
            presets.size < PartyPlaylistPreset.MAX_SAVED, variant = ParlorButtonVariant.Secondary) {
            draft?.let { preset -> write { settings.savePartyPlaylistPreset(preset) } }
        }
        SavedPlaylistPresets(presets, titles, seatCount, editable, onLoad = { games = it }, onDelete = { preset ->
            write { settings.removePartyPlaylistPreset(preset) }
        })
        if (writeFailed) PartyError(stringResource(Res.string.party_preset_write_failed))
        error?.let { PartyError(it) }
        PartyButton(Res.string.party_playlist_save, enabled = editable && games.isNotEmpty()) { onSave(games) }
        PartyButton(Res.string.party_rules_back, enabled = editable, variant = ParlorButtonVariant.Secondary, onClick = onBack)
    }
}

@Composable
private fun SavedPlaylistPresets(
    presets: List<PartyPlaylistPreset>,
    titles: Map<PartyGame, String>,
    seatCount: Int,
    enabled: Boolean,
    onLoad: (List<PartyGame>) -> Unit,
    onDelete: (PartyPlaylistPreset) -> Unit,
) {
    if (presets.isEmpty()) return
    PartyHeading(stringResource(Res.string.party_preset_heading))
    if (presets.size == PartyPlaylistPreset.MAX_SAVED) PartyDetail(stringResource(Res.string.party_preset_full))
    val gamesById = titles.keys.associateBy { it.protocol.gameId }
    val unavailable = stringResource(Res.string.party_playlist_unknown_game)
    presets.forEachIndexed { index, preset ->
        val games = preset.games.mapNotNull(gamesById::get)
        val compatible = games.size == preset.games.size && games.all { seatCount in it.playerCounts }
        ParlorCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s)) {
                PartyDetail(preset.games.joinToString(" · ") { titles[gamesById[it]] ?: unavailable })
                if (!compatible) PartyDetail(stringResource(Res.string.party_preset_incompatible))
                PlaylistOption(stringResource(Res.string.party_preset_load, index + 1), enabled && compatible) { onLoad(games) }
                PlaylistOption(stringResource(Res.string.party_preset_delete, index + 1), enabled) { onDelete(preset) }
            }
        }
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
    if (playlist.scores.isNotEmpty()) PartyStandings(playlist)
}

@Composable
private fun PartyStandings(playlist: PublicPartyPlaylist) {
    val standings = remember(playlist.scores) {
        playlist.scores.flatMap { it.seats }.groupBy(PublicPartyScoredSeat::playerId)
            .values.map { finishes ->
                PartyStanding(finishes.last().displayName, finishes.count(PublicPartyScoredSeat::won), finishes.size)
            }.sortedWith(compareByDescending<PartyStanding> { it.points }.thenBy { it.name })
    }
    PartyHeading(stringResource(Res.string.party_standings))
    ParlorCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s)) {
            PartyDetail(stringResource(Res.string.party_standings_policy))
            PartyDetail(stringResource(
                Res.string.party_standings_scored_count, playlist.scores.size, playlist.completedMatchIds.size,
            ))
            standings.forEach { standing ->
                PartyDetail(stringResource(
                    Res.string.party_standings_row, standing.name.asBidiArgument(), standing.points, standing.played,
                ))
            }
        }
    }
}

private data class PartyStanding(val name: String, val points: Int, val played: Int)

@Composable
private fun playlistGameTitle(game: GameProtocolRef, titles: Map<PartyGame, String>): String =
    titles.entries.firstOrNull { it.key.protocol == game }?.value ?: stringResource(Res.string.party_playlist_unknown_game)

@Composable
private fun PlaylistOption(label: String, enabled: Boolean, onClick: () -> Unit) {
    ParlorButton(label, onClick, label, Modifier.fillMaxWidth(), enabled, ParlorButtonVariant.Secondary)
}
