package com.parlor.app.shell.game.multiplayer

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.parlor.app.resources.Res
import com.parlor.app.resources.lobby_player_host
import com.parlor.app.resources.lobby_player_ready
import com.parlor.app.resources.lobby_welcome_title
import com.parlor.app.resources.room_game_code
import com.parlor.app.resources.room_game_host
import com.parlor.app.resources.room_game_local
import com.parlor.app.resources.room_game_players
import com.parlor.app.resources.room_game_wait
import androidx.compose.ui.unit.dp
import com.parlor.app.shell.game.GameCatalogPresentation
import com.parlor.designsystem.components.CatalogCover
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.RoomCodeWithCopy
import com.parlor.designsystem.localization.asBidiArgument
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.networking.room.RoomMember
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun LobbyInvitation(code: String, hostName: String, game: GameCatalogPresentation? = null) {
    val spacing = ParlorTheme.spacing
    val shape = RoundedCornerShape(ParlorTheme.radii.card)
    ParlorCard(
        modifier = Modifier.fillMaxWidth().border(
            ParlorTheme.borders.hairline, ParlorTheme.colors.borderAccent.copy(alpha = 0.45f), shape,
        ),
        contentPadding = spacing.m,
        bordered = false,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                game?.let { CatalogCover(it.artwork, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))) }
                Column {
                    EyebrowLabel(stringResource(Res.string.lobby_welcome_title))
                    Text(
                        game?.title ?: stringResource(Res.string.room_game_local),
                        color = ParlorTheme.colors.textPrimary,
                        style = ParlorTheme.typography.headingMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (game != null) {
                        Text(
                            stringResource(Res.string.room_game_local),
                            color = ParlorTheme.colors.textSecondary,
                            style = ParlorTheme.typography.bodySmall,
                        )
                    }
                }
            }
            ParlorCard(modifier = Modifier.fillMaxWidth(), contentPadding = spacing.s) {
                Column {
                    EyebrowLabel(stringResource(Res.string.room_game_code))
                    RoomCodeWithCopy(code = code)
                }
            }
            Text(stringResource(Res.string.room_game_host, hostName.asBidiArgument()),
                color = ParlorTheme.colors.textPrimary, style = ParlorTheme.typography.labelLarge)
        }
    }
}

@Composable
internal fun LobbyRoster(hostName: String, connected: List<RoomMember>) {
    ParlorCard(modifier = Modifier.fillMaxWidth(), contentPadding = ParlorTheme.spacing.m) {
        Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m)) {
            Text(
                stringResource(Res.string.room_game_players, connected.size + 1),
                color = ParlorTheme.colors.textPrimary,
                style = ParlorTheme.typography.headingMedium,
                modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite },
            )
            LobbyPlayer(hostName, stringResource(Res.string.lobby_player_host))
            connected.forEach { LobbyPlayer(it.displayName, stringResource(Res.string.lobby_player_ready)) }
            if (connected.isEmpty()) {
                Text(stringResource(Res.string.room_game_wait), color = ParlorTheme.colors.textSecondary,
                    style = ParlorTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun LobbyPlayer(name: String, status: String) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
    ) {
        Text(name, color = ParlorTheme.colors.textPrimary, style = ParlorTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f))
        Text(status, color = ParlorTheme.colors.textSecondary, style = ParlorTheme.typography.bodySmall,
            modifier = Modifier.weight(1f))
    }
}
