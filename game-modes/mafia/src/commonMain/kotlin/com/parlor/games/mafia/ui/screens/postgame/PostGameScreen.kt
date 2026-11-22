package com.parlor.games.mafia.ui.screens.postgame

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.CatalogArtworkBadge
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.components.parlorSafeContentPadding
import com.parlor.designsystem.icons.ParlorArtwork
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.mafia.domain.state.Role
import com.parlor.games.mafia.domain.state.Team
import com.parlor.games.mafia.resources.Res
import com.parlor.games.mafia.resources.postgame_eyebrow
import com.parlor.games.mafia.resources.postgame_ended_without_winner
import com.parlor.games.mafia.resources.postgame_exit
import com.parlor.games.mafia.resources.postgame_exit_description
import com.parlor.games.mafia.resources.postgame_mafia_wins
import com.parlor.games.mafia.resources.postgame_roles_card
import com.parlor.games.mafia.resources.postgame_town_wins
import com.parlor.games.mafia.ui.screens.reveal.roleDisplayName
import org.jetbrains.compose.resources.stringResource

@Composable
fun PostGameScreen(
    winner: Team?,
    finalRoles: List<Pair<String, Role>>,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val compactTypography = LocalDensity.current.fontScale >= COMPACT_FONT_SCALE_LIMIT
    val winnerColor = when (winner) {
        Team.Mafia -> ParlorTheme.colors.semanticDanger
        Team.Town -> ParlorTheme.colors.semanticSuccess
        null -> ParlorTheme.colors.textPrimary
    }
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .parlorSafeContentPadding(ParlorTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ParlorCard(modifier = Modifier.fillMaxWidth(), hero = true) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (!compactTypography) {
                        CatalogArtworkBadge(
                            artwork = ParlorArtwork.Moon,
                            modifier = Modifier.size(ParlorTheme.spacing.xxxl),
                        )
                    }
                    EyebrowLabel(
                        text = stringResource(Res.string.postgame_eyebrow),
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = winLine(winner),
                        style = ParlorTheme.typography.displayLarge,
                        color = winnerColor,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            ParlorCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
                ) {
                    EyebrowLabel(text = stringResource(Res.string.postgame_roles_card), accent = false)
                    finalRoles.forEachIndexed { index, (name, role) ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = ParlorTheme.colors.borderSubtle,
                                thickness = ParlorTheme.borders.hairline,
                            )
                        }
                        // Each value owns the available width so legal long
                        // names and large text cannot squeeze out the role.
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xxs),
                        ) {
                            Text(
                                text = name,
                                style = ParlorTheme.typography.bodyLarge,
                                color = ParlorTheme.colors.textPrimary,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = roleDisplayName(role),
                                style = ParlorTheme.typography.bodyLarge,
                                color = roleColor(role),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            ParlorButton(
                label = stringResource(Res.string.postgame_exit),
                contentDescription = stringResource(Res.string.postgame_exit_description),
                onClick = onExit,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun roleColor(role: Role): Color = when (role) {
    Role.Mafia -> ParlorTheme.colors.semanticDanger
    Role.Detective -> ParlorTheme.colors.accentBrass
    Role.Doctor -> ParlorTheme.colors.semanticSuccess
    Role.Civilian -> ParlorTheme.colors.textSecondary
}

@Composable
private fun winLine(team: Team?): String = when (team) {
    Team.Mafia -> stringResource(Res.string.postgame_mafia_wins)
    Team.Town -> stringResource(Res.string.postgame_town_wins)
    null -> stringResource(Res.string.postgame_ended_without_winner)
}

private const val COMPACT_FONT_SCALE_LIMIT = 1.3f
