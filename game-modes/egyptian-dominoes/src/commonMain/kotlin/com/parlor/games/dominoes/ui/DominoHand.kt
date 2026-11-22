package com.parlor.games.dominoes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.parlor.core.ids.PlayerId
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.InPlaceBackHandler
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.dominoes.domain.DominoAction
import com.parlor.games.dominoes.domain.DominoEnd
import com.parlor.games.dominoes.domain.DominoRules
import com.parlor.games.dominoes.domain.DominoState
import com.parlor.games.dominoes.resources.Res
import com.parlor.games.dominoes.resources.dom_draw
import com.parlor.games.dominoes.resources.dom_end_a
import com.parlor.games.dominoes.resources.dom_end_b
import com.parlor.games.dominoes.resources.dom_hand
import com.parlor.games.dominoes.resources.dom_hide
import com.parlor.games.dominoes.resources.dom_open_tile
import com.parlor.games.dominoes.resources.dom_opener
import com.parlor.games.dominoes.resources.dom_pass
import com.parlor.games.dominoes.resources.dom_pick
import com.parlor.games.dominoes.resources.dom_private
import com.parlor.games.dominoes.resources.dom_reveal
import com.parlor.games.dominoes.resources.dom_tile
import com.parlor.games.dominoes.resources.dom_tile_available
import com.parlor.games.dominoes.resources.dom_tile_blocked
import com.parlor.games.dominoes.resources.dom_tile_selected
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DominoHand(
    state: DominoState,
    self: PlayerId,
    enabled: Boolean,
    visible: Boolean,
    motion: DominoMotion,
    onVisibility: (Boolean) -> Unit,
    onAction: (DominoAction) -> Unit,
) {
    val own = state.privatePerPlayer.getValue(self)
    var selected by remember(state.public.token, state.public.move, visible, motion) { mutableStateOf<Int?>(null) }
    InPlaceBackHandler(selected != null) { selected = null }
    val scroll = rememberScrollState()
    val rackRequester = remember { BringIntoViewRequester() }
    var previousHandSize by remember(state.public.token) { mutableStateOf(own.hand.size) }
    val haptic = LocalHapticFeedback.current
    val reducedMotion = ParlorTheme.reducedMotion
    val colors = ParlorTheme.colors
    val spacing = ParlorTheme.spacing
    val isMyTurn = state.public.turn == self
    val rackShape = RoundedCornerShape(ParlorTheme.radii.card)

    LaunchedEffect(own.hand.size, visible) {
        val drew = own.hand.size > previousHandSize
        previousHandSize = own.hand.size
        if (visible) {
            withFrameNanos { }
            if (drew) {
                if (reducedMotion) scroll.scrollTo(scroll.maxValue) else scroll.animateScrollTo(scroll.maxValue)
            }
            // Revealing a hand must reveal the actual tiles, not just a header below the fold.
            rackRequester.bringIntoView()
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(rackShape)
            .background(if (isMyTurn) colors.surfaceHero else colors.surfaceElevated)
            .border(
                width = ParlorTheme.borders.hairline,
                color = if (isMyTurn) colors.accentEmber.copy(alpha = 0.45f) else colors.borderSubtle,
                shape = rackShape,
            )
            .padding(spacing.m)
            .onGloballyPositioned {
                motion.rack = DominoFlightPose(
                    it.localToRoot(Offset(it.size.width / 2f, it.size.height / 2f)),
                    it.size.height * .55f,
                    90f,
                )
            },
        verticalArrangement = Arrangement.spacedBy(spacing.s),
    ) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            EyebrowLabel(
                text = stringResource(Res.string.dom_hand),
            )
            DominoTopAction(
                text = stringResource(if (visible) Res.string.dom_hide else Res.string.dom_reveal),
                accented = !visible,
                enabled = enabled || visible,
                onClick = { onVisibility(!visible) },
            )
        }
        if (!visible) {
            DominoBody(stringResource(Res.string.dom_private))
        } else {
            val slotShape = RoundedCornerShape(ParlorTheme.radii.subtle)
            Row(
                Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(rackRequester)
                    .horizontalScroll(scroll)
                    .padding(vertical = spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                own.hand.forEach { tile ->
                    val playable = DominoRules.playableEnds(state, self, tile).isNotEmpty()
                    val isSelected = selected == tile.id
                    val label = stringResource(Res.string.dom_tile, tile.low, tile.high)
                    val status = stringResource(
                        when {
                            isSelected -> Res.string.dom_tile_selected
                            playable -> Res.string.dom_tile_available
                            else -> Res.string.dom_tile_blocked
                        },
                    )
                    val slotBackground = when {
                        isSelected -> colors.accentEmber.copy(alpha = 0.18f)
                        playable -> colors.surfaceHigher.copy(alpha = 0.65f)
                        else -> colors.surfaceInset.copy(alpha = 0.55f)
                    }
                    val slotBorder = when {
                        isSelected -> colors.accentEmber
                        playable -> colors.accentEmber.copy(alpha = 0.55f)
                        else -> colors.borderSubtle.copy(alpha = 0.45f)
                    }
                    Box(
                        Modifier
                            .size(52.dp, 100.dp)
                            .clip(slotShape)
                            .background(slotBackground)
                            .border(if (isSelected) 2.dp else 1.dp, slotBorder, slotShape)
                            .selectable(isSelected, enabled = enabled && playable, role = Role.RadioButton) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                selected = tile.id
                            }
                            .semantics {
                                contentDescription = label
                                stateDescription = status
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        DominoTileFace(
                            left = tile.low,
                            right = tile.high,
                            modifier = Modifier
                                .size(42.dp, 84.dp)
                                .onGloballyPositioned {
                                    motion.hand[tile.id] = DominoFlightPose(
                                        it.localToRoot(Offset(it.size.width / 2f, it.size.height / 2f)),
                                        it.size.height.toFloat(),
                                        90f,
                                    )
                                }
                                .graphicsLayer {
                                    translationY = if (isSelected) -4.dp.toPx() else 0f
                                    alpha = when {
                                        motion.hiddenHandTile == tile.id -> 0f
                                        !playable && isMyTurn -> 0.55f
                                        else -> 1f
                                    }
                                },
                            vertical = true,
                        )
                    }
                }
            }
            val chosen = own.hand.firstOrNull { it.id == selected }
            if (chosen != null) {
                DominoRules.playableEnds(state, self, chosen).forEach { end ->
                    val label = when {
                        state.public.chain.isEmpty() -> stringResource(Res.string.dom_open_tile)
                        end == DominoEnd.Left -> stringResource(Res.string.dom_end_a, state.public.chain.first().left)
                        else -> stringResource(Res.string.dom_end_b, state.public.chain.last().right)
                    }
                    DominoButton(label, enabled) {
                        onAction(DominoAction.Place(self, state.public.token, state.public.move, chosen.id, end))
                    }
                }
            } else if (isMyTurn) {
                DominoBody(stringResource(if (own.requiredOpening != null) Res.string.dom_opener else Res.string.dom_pick))
            }
        }
        when {
            DominoRules.canDraw(state, self) -> DominoButton(stringResource(Res.string.dom_draw), enabled) {
                onAction(DominoAction.Draw(self, state.public.token, state.public.move))
            }
            DominoRules.canPass(state, self) -> DominoButton(stringResource(Res.string.dom_pass), enabled) {
                onAction(DominoAction.Pass(self, state.public.token, state.public.move))
            }
        }
    }
}
