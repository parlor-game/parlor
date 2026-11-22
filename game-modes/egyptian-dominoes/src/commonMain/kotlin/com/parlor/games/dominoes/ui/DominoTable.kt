package com.parlor.games.dominoes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.parlor.core.ids.PlayerId
import com.parlor.designsystem.components.CatalogArtworkBadge
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.InPlaceBackHandler
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.components.QuickStartSteps
import com.parlor.designsystem.icons.ParlorArtwork
import com.parlor.designsystem.localization.asBidiArgument
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.dominoes.domain.DominoAction
import com.parlor.games.dominoes.domain.DominoPhase
import com.parlor.games.dominoes.domain.DominoRules
import com.parlor.games.dominoes.domain.DominoState
import com.parlor.games.dominoes.resources.Res
import com.parlor.games.dominoes.resources.dom_close_help
import com.parlor.games.dominoes.resources.dom_full_rules
import com.parlor.games.dominoes.resources.dom_help
import com.parlor.games.dominoes.resources.dom_hide_details
import com.parlor.games.dominoes.resources.dom_leave
import com.parlor.games.dominoes.resources.dom_quick_start
import com.parlor.games.dominoes.resources.dom_round
import com.parlor.games.dominoes.resources.dom_rules
import com.parlor.games.dominoes.resources.dom_step_one
import com.parlor.games.dominoes.resources.dom_step_three
import com.parlor.games.dominoes.resources.dom_step_two
import com.parlor.games.dominoes.resources.dom_title
import com.parlor.games.dominoes.resources.dom_turn_other
import com.parlor.games.dominoes.resources.dom_turn_you
import org.jetbrains.compose.resources.stringResource

@Composable
fun DominoTable(
    state: DominoState,
    self: PlayerId,
    isHost: Boolean,
    enabled: Boolean,
    privacyEpoch: Long,
    onAction: (DominoAction) -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var help by remember(state.public.token, privacyEpoch) { mutableStateOf(false) }
    var visible by remember(state.public.token, privacyEpoch) { mutableStateOf(false) }
    var fullRules by remember(state.public.token, privacyEpoch, help) { mutableStateOf(false) }
    InPlaceBackHandler(help || visible) { if (help) help = false else visible = false }
    val motion = rememberDominoMotion(state, self, privacyEpoch, visible && !help)
    val canAct = enabled && motion.flight == null
    val colors = ParlorTheme.colors
    val spacing = ParlorTheme.spacing
    val compactText = LocalDensity.current.fontScale >= 1.5f

    Box(modifier.fillMaxSize().onGloballyPositioned { motion.rootOrigin = it.positionInRoot() }) {
        DominoFrame(Modifier.fillMaxSize(), "${state.phase.id}:${state.public.token}:$help") {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                DominoTopAction(
                    text = stringResource(Res.string.dom_leave),
                    onClick = onLeave,
                )
                DominoTopAction(
                    text = stringResource(if (help) Res.string.dom_close_help else Res.string.dom_help),
                    accented = help,
                    onClick = { help = !help },
                )
            }
            if (help) {
                if (!compactText) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.m),
                    ) {
                        CatalogArtworkBadge(
                            artwork = ParlorArtwork.Tiles,
                            modifier = Modifier.size(56.dp),
                        )
                        DominoTitle(stringResource(Res.string.dom_title))
                    }
                } else {
                    DominoTitle(stringResource(Res.string.dom_title))
                }
                ParlorCard(Modifier.fillMaxWidth(), contentPadding = spacing.m) {
                    QuickStartSteps(
                        stringResource(Res.string.dom_quick_start),
                        listOf(
                            stringResource(Res.string.dom_step_one),
                            stringResource(Res.string.dom_step_two),
                            stringResource(Res.string.dom_step_three),
                        ),
                    )
                }
                DominoTopAction(
                    text = stringResource(if (fullRules) Res.string.dom_hide_details else Res.string.dom_full_rules),
                    accented = true,
                    onClick = { fullRules = !fullRules },
                )
                if (fullRules) {
                    ParlorCard(Modifier.fillMaxWidth(), contentPadding = spacing.m) {
                        DominoBody(stringResource(Res.string.dom_rules))
                    }
                }
            } else {
                DominoStatusHeader(state, self, compactText)
                if (state.phase == DominoPhase.RoundResult || state.phase == DominoPhase.MatchResult) {
                    DominoResults(state, isHost, enabled, onAction)
                }
                DominoTeams(state, self)
                DominoBoard(state, motion)
                if (state.phase == DominoPhase.Playing) {
                    if (DominoRules.usesDraw(state.public.settings, state.players.size)) {
                        DominoStock(state.public.stockCount, motion)
                    }
                    DominoHand(state, self, canAct, visible, motion, { visible = it }, onAction)
                }
                // Expanded accessible seat cards must not push the board and current action away.
                DominoOpponents(state, self, motion)
            }
        }
        if (!help) DominoFlightOverlay(motion, Modifier.align(AbsoluteAlignment.TopLeft))
    }
}

@Composable
private fun DominoStatusHeader(state: DominoState, self: PlayerId, compactText: Boolean) {
    val colors = ParlorTheme.colors
    val spacing = ParlorTheme.spacing
    val isMyTurn = state.phase == DominoPhase.Playing && state.public.turn == self
    val shape = RoundedCornerShape(ParlorTheme.radii.card)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isMyTurn) colors.surfaceHero else colors.surfaceElevated)
            .border(
                ParlorTheme.borders.hairline,
                if (isMyTurn) colors.accentEmber.copy(alpha = 0.45f) else colors.borderSubtle,
                shape,
            )
            .padding(horizontal = spacing.m, vertical = spacing.s),
        horizontalArrangement = Arrangement.spacedBy(spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!compactText) {
            CatalogArtworkBadge(
                artwork = ParlorArtwork.Tiles,
                modifier = Modifier.size(44.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            EyebrowLabel(
                text = stringResource(Res.string.dom_round, state.public.round),
            )
            if (state.phase == DominoPhase.Playing) {
                Text(
                    text = if (isMyTurn) {
                        stringResource(Res.string.dom_turn_you)
                    } else {
                        stringResource(Res.string.dom_turn_other, state.name(checkNotNull(state.public.turn)))
                    },
                    color = if (isMyTurn) colors.textPrimary else colors.textSecondary,
                    style = ParlorTheme.typography.labelLarge,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

internal fun DominoState.name(id: PlayerId): String = players.first { it.id == id }.displayName.asBidiArgument()
