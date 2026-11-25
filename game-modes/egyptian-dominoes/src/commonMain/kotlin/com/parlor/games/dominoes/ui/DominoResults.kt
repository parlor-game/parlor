package com.parlor.games.dominoes.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.components.CatalogArtworkBadge
import com.parlor.designsystem.icons.ParlorArtwork
import com.parlor.designsystem.localization.asBidiArgument
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.dominoes.domain.DominoAction
import com.parlor.games.dominoes.domain.DominoPhase
import com.parlor.games.dominoes.domain.DominoScoring
import com.parlor.games.dominoes.domain.DominoState
import com.parlor.games.dominoes.resources.Res
import com.parlor.games.dominoes.resources.dom_award
import com.parlor.games.dominoes.resources.dom_blocked
import com.parlor.games.dominoes.resources.dom_match_result
import com.parlor.games.dominoes.resources.dom_next
import com.parlor.games.dominoes.resources.dom_remaining
import com.parlor.games.dominoes.resources.dom_rematch
import com.parlor.games.dominoes.resources.dom_round_winner
import com.parlor.games.dominoes.resources.dom_score
import com.parlor.games.dominoes.resources.dom_shutout
import com.parlor.games.dominoes.resources.dom_tie
import com.parlor.games.dominoes.resources.dom_wait_host
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DominoResults(
    state: DominoState,
    host: Boolean,
    enabled: Boolean,
    onAction: (DominoAction) -> Unit,
) {
    val result = checkNotNull(state.public.result)
    val match = state.phase == DominoPhase.MatchResult
    val showAwardArt = LocalDensity.current.fontScale < 1.5f
    val colors = ParlorTheme.colors
    val spacing = ParlorTheme.spacing
    val cardShape = RoundedCornerShape(ParlorTheme.radii.card)
    val rowShape = RoundedCornerShape(ParlorTheme.radii.subtle)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(colors.surfaceHero)
            .border(ParlorTheme.borders.hairline, colors.accentEmber.copy(alpha = 0.45f), cardShape)
            .padding(if (showAwardArt) spacing.xl else spacing.m),
        verticalArrangement = Arrangement.spacedBy(spacing.m),
    ) {
        if (showAwardArt && match) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CatalogArtworkBadge(
                    artwork = ParlorArtwork.Tiles,
                    modifier = Modifier.size(64.dp),
                )
            }
        }
        if (match) {
            DominoTitle(stringResource(Res.string.dom_match_result))
            DominoTitle(
                state.players
                    .filter { it.id in state.public.matchWinners }
                    .joinToString(" · ") { it.displayName.asBidiArgument() },
            )
        } else {
            DominoTitle(
                result.winner?.let { stringResource(Res.string.dom_round_winner, dominoSideName(state, it)) }
                    ?: stringResource(Res.string.dom_tie),
            )
        }
        val award = stringResource(Res.string.dom_award, result.points)
        if (showAwardArt) {
            DominoAwardSeal(award, state.public.token)
        } else {
            Text(award, color = colors.accentEmber, style = ParlorTheme.typography.headingMedium)
        }
        if (result.blocked) DominoBody(stringResource(Res.string.dom_blocked))
        if (match && DominoScoring.isShutout(state.public.settings, state.public.scores)) {
            DominoBody(stringResource(Res.string.dom_shutout))
        }
        if (host) {
            DominoButton(stringResource(if (match) Res.string.dom_rematch else Res.string.dom_next), enabled) {
                onAction(if (match) DominoAction.Rematch(state.public.token) else DominoAction.NextRound(state.public.token))
            }
        } else {
            DominoBody(stringResource(Res.string.dom_wait_host))
        }
        DominoScoring.sides(state.players, state.public.settings).entries.sortedByDescending {
            state.public.scores.getValue(it.key)
        }.forEach { (side, members) ->
            val score = state.public.scores.getValue(side)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(rowShape)
                    .background(colors.surfaceElevated)
                    .border(ParlorTheme.borders.hairline, colors.borderSubtle, rowShape)
                    .padding(spacing.s),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                ) {
                    Text(
                        text = dominoSideName(state, side),
                        color = colors.textPrimary,
                        style = ParlorTheme.typography.labelLarge,
                    )
                    Text(
                        text = stringResource(Res.string.dom_score, score),
                        color = colors.accentEmber,
                        style = ParlorTheme.typography.labelLarge,
                    )
                }
                members.forEach { id ->
                    DominoBody(stringResource(Res.string.dom_remaining, state.name(id), result.remainingPips.getValue(id)))
                }
                val barShape = RoundedCornerShape(ParlorTheme.radii.pill)
                Box(Modifier.fillMaxWidth().height(6.dp).clip(barShape).background(colors.surfaceInset)) {
                    Box(
                        Modifier
                            .fillMaxWidth((score.toFloat() / state.public.settings.target).coerceIn(0f, 1f))
                            .height(6.dp)
                            .clip(barShape)
                            .background(colors.accentEmber),
                    )
                }
            }
        }
    }
}

@Composable
private fun DominoAwardSeal(award: String, token: Long) {
    val appear = remember(token) { Animatable(0f) }
    val reducedMotion = ParlorTheme.reducedMotion
    val accent = ParlorTheme.colors.accentEmber
    LaunchedEffect(appear, reducedMotion) { appear.animateTo(1f, tween(if (reducedMotion) 0 else 700)) }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(92.dp).clearAndSetSemantics {}) {
            val radius = size.minDimension * .36f
            drawCircle(accent.copy(alpha = .25f), radius, style = Stroke(1.dp.toPx()))
            repeat(16) { index ->
                rotate(index * 22.5f) {
                    val r = radius * (1f + .2f * appear.value)
                    drawLine(
                        accent.copy(alpha = appear.value),
                        center + Offset(0f, r),
                        center + Offset(0f, r + 5.dp.toPx()),
                        2.dp.toPx(),
                    )
                }
            }
        }
        Text(
            text = award,
            color = accent,
            style = ParlorTheme.typography.headingMedium,
            textAlign = TextAlign.Center,
        )
    }
}
