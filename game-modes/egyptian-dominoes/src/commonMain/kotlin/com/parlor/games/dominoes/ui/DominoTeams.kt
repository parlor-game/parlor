package com.parlor.games.dominoes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.parlor.core.ids.PlayerId
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.dominoes.domain.DominoCompetition
import com.parlor.games.dominoes.domain.DominoScoring
import com.parlor.games.dominoes.domain.DominoState
import com.parlor.games.dominoes.resources.Res
import com.parlor.games.dominoes.resources.dom_team_name
import com.parlor.games.dominoes.resources.dom_team_summary
import com.parlor.games.dominoes.resources.dom_your_team
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun dominoSideColor(team: Int?): Color =
    if (team == 2) ParlorTheme.colors.semanticSuccess else ParlorTheme.colors.accentEmber

@Composable
internal fun dominoSideName(state: DominoState, player: PlayerId): String =
    DominoScoring.teamNumber(state, player)?.let { stringResource(Res.string.dom_team_name, it) } ?: state.name(player)

@Composable
internal fun DominoTeams(state: DominoState, self: PlayerId) {
    if (state.public.settings.competition != DominoCompetition.Teams) return
    val colors = ParlorTheme.colors
    val spacing = ParlorTheme.spacing
    val shape = RoundedCornerShape(ParlorTheme.radii.card)
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        DominoScoring.sides(state.players, state.public.settings).forEach { (side, members) ->
            val teamColor = dominoSideColor(DominoScoring.teamNumber(state, side))
            val isMyTeam = self in members
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(if (isMyTeam) colors.surfaceHero else colors.surfaceElevated)
                    .border(ParlorTheme.borders.hairline, teamColor.copy(alpha = if (isMyTeam) 0.65f else 0.35f), shape)
                    .padding(spacing.m),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                val title = stringResource(
                    Res.string.dom_team_summary,
                    dominoSideName(state, side),
                    state.public.scores.getValue(side),
                )
                Text(
                    text = if (isMyTeam) stringResource(Res.string.dom_your_team, title) else title,
                    color = teamColor,
                    style = ParlorTheme.typography.labelLarge,
                )
                DominoBody(members.joinToString(" · ") { state.name(it) })
            }
        }
    }
}
