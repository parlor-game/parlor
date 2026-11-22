package com.parlor.games.dominoes.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.parlor.core.ids.PlayerId
import com.parlor.designsystem.localization.asBidiArgument
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.dominoes.domain.DominoScoring
import com.parlor.games.dominoes.domain.DominoState
import com.parlor.games.dominoes.resources.Res
import com.parlor.games.dominoes.resources.dom_score
import com.parlor.games.dominoes.resources.dom_seat
import com.parlor.games.dominoes.resources.dom_stock
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DominoOpponents(state: DominoState, self: PlayerId, motion: DominoMotion) {
    val colors = ParlorTheme.colors
    val spacing = ParlorTheme.spacing
    val cardShape = RoundedCornerShape(ParlorTheme.radii.card)
    // Weighted FlowRow items wrap using their intrinsic word width before sharing each line.
    // A fixed three-way Row can split even ordinary names at the user's enlarged font size.
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        state.players.filter { it.id != self }.forEach { player ->
            val count = state.public.handCounts.getValue(player.id)
            val selected = state.public.turn == player.id
            val team = DominoScoring.teamNumber(state, player.id)
            val sideColor = dominoSideColor(team)
            val containerColor by animateColorAsState(
                targetValue = if (selected) colors.surfaceHero else colors.surfaceElevated,
                animationSpec = tween(if (ParlorTheme.reducedMotion) 0 else 250),
            )
            val borderColor = when {
                selected -> colors.accentEmber
                team != null -> sideColor.copy(alpha = 0.55f)
                else -> colors.borderSubtle
            }
            val label = stringResource(
                Res.string.dom_seat,
                player.displayName.asBidiArgument(),
                count,
                DominoScoring.scoreFor(state, player.id),
            ) + if (team == null) "" else " · " + dominoSideName(state, player.id)

            Column(
                Modifier
                    .weight(1f)
                    .clip(cardShape)
                    .background(containerColor)
                    .border(if (selected) ParlorTheme.borders.strong else ParlorTheme.borders.hairline, borderColor, cardShape)
                    .padding(horizontal = 4.dp, vertical = 12.dp)
                    .semantics(mergeDescendants = true) { contentDescription = label }
                    .onGloballyPositioned {
                        motion.seats[player.id] = DominoFlightPose(
                            it.localToRoot(Offset(it.size.width / 2f, it.size.height * .3f)),
                            it.size.height * .3f,
                            90f,
                        )
                    },
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DominoBackFan(count)
                Text(
                    text = player.displayName,
                    color = if (selected) colors.accentEmber else colors.textPrimary,
                    style = ParlorTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.clearAndSetSemantics {},
                )
                if (team != null) {
                    Text(
                        text = dominoSideName(state, player.id),
                        color = sideColor,
                        style = ParlorTheme.typography.bodySmall,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
                Text(
                    text = stringResource(Res.string.dom_score, DominoScoring.scoreFor(state, player.id)),
                    color = colors.textSecondary,
                    style = ParlorTheme.typography.bodySmall,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        }
    }
}

@Composable
private fun DominoBackFan(count: Int) {
    val step = (52f / (count - 1).coerceAtLeast(1)).coerceAtMost(7f)
    Box(
        Modifier
            .width((18 + step * (count - 1).coerceAtLeast(0)).dp)
            .height(46.dp)
            .clearAndSetSemantics {},
        contentAlignment = AbsoluteAlignment.TopLeft,
    ) {
        repeat(count) { index ->
            DominoTileFace(
                left = 0,
                right = 0,
                modifier = Modifier
                    .size(18.dp, 36.dp)
                    .graphicsLayer {
                        translationX = (index * step).dp.toPx()
                        translationY = (kotlin.math.abs(index - (count - 1) / 2f) * .4f).dp.toPx()
                        rotationZ = (index - (count - 1) / 2f) * 1.3f
                    },
                covered = true,
                vertical = true,
            )
        }
    }
}

@Composable
internal fun DominoStock(count: Int, motion: DominoMotion) {
    val colors = ParlorTheme.colors
    val shape = RoundedCornerShape(ParlorTheme.radii.card)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceElevated)
            .border(ParlorTheme.borders.hairline, colors.borderSubtle, shape)
            .padding(horizontal = ParlorTheme.spacing.m, vertical = ParlorTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(62.dp, 40.dp)
                .onGloballyPositioned {
                    motion.stock = DominoFlightPose(
                        it.localToRoot(Offset(it.size.width / 2f, it.size.height / 2f)),
                        it.size.width * .8f,
                        0f,
                    )
                }
                .clearAndSetSemantics {},
            contentAlignment = AbsoluteAlignment.TopLeft,
        ) {
            if (count > 0) {
                repeat(count.coerceAtMost(3)) { index ->
                    DominoTileFace(
                        left = 0,
                        right = 0,
                        modifier = Modifier
                            .size(48.dp, 24.dp)
                            .graphicsLayer {
                                translationX = (index * 3).dp.toPx()
                                translationY = (index * 3).dp.toPx()
                                rotationZ = index * 4f
                            },
                        covered = true,
                    )
                }
            }
        }
        DominoBody(stringResource(Res.string.dom_stock, count))
    }
}
