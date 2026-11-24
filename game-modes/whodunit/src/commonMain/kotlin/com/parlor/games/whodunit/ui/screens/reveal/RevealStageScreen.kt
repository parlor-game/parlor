package com.parlor.games.whodunit.ui.screens.reveal

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.whodunit.domain.event.KillerWinCause
import com.parlor.games.whodunit.domain.event.Verdict
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.resources.reveal_stage_continue
import com.parlor.games.whodunit.resources.reveal_stage_continue_description
import com.parlor.games.whodunit.resources.reveal_stage_eyebrow
import com.parlor.games.whodunit.resources.reveal_stage_game_ended_subhead
import com.parlor.games.whodunit.resources.reveal_stage_killer_finaltwo_subhead
import com.parlor.games.whodunit.resources.reveal_stage_killer_innocent_subhead
import com.parlor.games.whodunit.resources.reveal_stage_killer_tie_subhead
import com.parlor.games.whodunit.resources.reveal_stage_killer_was_label
import com.parlor.games.whodunit.resources.reveal_stage_no
import com.parlor.games.whodunit.resources.reveal_stage_players_win_subhead
import com.parlor.games.whodunit.resources.reveal_stage_yes
import com.parlor.games.whodunit.ui.components.WhodunitTable
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

private fun killerWinCause(verdict: Verdict): KillerWinCause? = when (verdict) {
    is Verdict.PlayersWin -> null
    is Verdict.KillerWins -> verdict.cause
}

/**
 * The final-reveal moment, editorial direction.
 *
 * Staged as three timed beats:
 *
 *  1. **Verdict word** ("Yes"/"No") fades in immediately at full display
 *     size, with no decoration.
 *  2. **Accent line** — a 48dp amber horizontal bar — slides in from
 *     zero width to its full width, drawing the eye to the verdict card.
 *  3. **Verdict card** holding the killer's name fades in.
 *  4. **Narrative** fades in last so the room reads the killer's name
 *     first.
 *
 * Reduced-motion users see the final layout immediately.
 */
@Composable
fun RevealStageScreen(
    verdict: Verdict,
    killerDisplayName: String,
    revealNarrative: String,
    onAcknowledge: (() -> Unit)?,
    waitingLabel: String? = null,
    modifier: Modifier = Modifier,
) {
    val colors = ParlorTheme.colors
    val reduced = ParlorTheme.reducedMotion
    val motion = ParlorTheme.motion
    // Resolve the sealed verdict once outside Compose dispatch. Repeating the
    // type test in this screen let the iOS build render contradictory labels.
    val cause = killerWinCause(verdict)
    val playersWin = cause == null
    val verdictLine = stringResource(if (playersWin) Res.string.reveal_stage_yes else Res.string.reveal_stage_no)
    val accentColor = if (playersWin) colors.semanticSuccess else colors.semanticDanger
    val subhead = stringResource(
        when (cause) {
            null -> Res.string.reveal_stage_players_win_subhead
            KillerWinCause.InnocentAccused -> Res.string.reveal_stage_killer_innocent_subhead
            KillerWinCause.TieUnresolved -> Res.string.reveal_stage_killer_tie_subhead
            KillerWinCause.SurvivedToFinalTwo -> Res.string.reveal_stage_killer_finaltwo_subhead
            KillerWinCause.GameEndedEarly -> Res.string.reveal_stage_game_ended_subhead
        },
    )

    // Stage gating:
    //   0 = verdict word only
    //   1 = accent line draws + card fades in
    //   2 = narrative fades in
    var stage by remember { mutableStateOf(if (reduced) 2 else 0) }
    var cardAccessible by remember { mutableStateOf(reduced) }
    var narrativeAccessible by remember { mutableStateOf(reduced) }
    LaunchedEffect(reduced) {
        if (reduced) {
            stage = 2
            cardAccessible = true
            narrativeAccessible = true
            return@LaunchedEffect
        }
        stage = 0
        cardAccessible = false
        narrativeAccessible = false
        delay(motion.durationFast.toLong())
        stage = 1
        delay(motion.durationSlow.toLong())
        // Do not make alpha-zero content available to assistive technology.
        // The card becomes accessible once its fade-in has completed.
        cardAccessible = true
        stage = 2
        delay(motion.durationSlow.toLong())
        narrativeAccessible = true
    }

    val accentLineProgress by animateFloatAsState(
        targetValue = if (stage >= 1) 1f else 0f,
        animationSpec = tween(
            durationMillis = motion.durationSlow,
            easing = motion.easingTheatrical,
        ),
        label = "reveal-accent-line",
    )
    val cardAlpha by animateFloatAsState(
        targetValue = if (stage >= 1) 1f else 0f,
        animationSpec = tween(
            durationMillis = motion.durationSlow,
            easing = motion.easingStandard,
        ),
        label = "reveal-card-alpha",
    )
    val narrativeAlpha by animateFloatAsState(
        targetValue = if (stage >= 2) 1f else 0f,
        animationSpec = tween(
            durationMillis = motion.durationSlow,
            easing = motion.easingStandard,
        ),
        label = "reveal-narrative-alpha",
    )

    WhodunitTable(
        modifier = modifier,
        actions = if (onAcknowledge != null || waitingLabel != null) {
            {
                if (onAcknowledge != null) {
                    ParlorButton(
                        label = stringResource(Res.string.reveal_stage_continue),
                        contentDescription = stringResource(
                            Res.string.reveal_stage_continue_description,
                        ),
                        onClick = onAcknowledge,
                        enabled = narrativeAccessible,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else if (waitingLabel != null) {
                    EyebrowLabel(
                        text = waitingLabel,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            null
        },
    ) {
        ParlorCard(modifier = Modifier.fillMaxWidth(), contentPadding = ParlorTheme.spacing.l) {
            Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s)) {
                EyebrowLabel(text = stringResource(Res.string.reveal_stage_eyebrow), accent = false)
                Text(
                    text = verdictLine,
                    style = ParlorTheme.typography.headingLarge,
                    color = accentColor,
                )
                // Accent line — amber bar that draws in from zero width.
                Box(
                    modifier = Modifier
                        .width(ParlorTheme.spacing.xxl * accentLineProgress)
                        .height(ParlorTheme.spacing.xxs)
                        .background(colors.accentEmber),
                )
                Text(
                    text = subhead,
                    style = ParlorTheme.typography.bodyLarge,
                    color = colors.textPrimary,
                )
            }
        }

        ParlorCard(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(cardAlpha)
                .then(
                    if (cardAccessible) {
                        Modifier
                    } else {
                        Modifier.clearAndSetSemantics { }
                    }
                ),
            contentPadding = ParlorTheme.spacing.l,
            hero = true,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m)) {
                EyebrowLabel(text = stringResource(Res.string.reveal_stage_killer_was_label))
                Text(
                    text = killerDisplayName,
                    style = ParlorTheme.typography.headingLarge,
                    color = colors.textPrimary,
                    modifier = Modifier.semantics {
                        // This node enters the tree only after the card has
                        // visibly faded in, so it announces the reveal once.
                        liveRegion = LiveRegionMode.Assertive
                    },
                )
                Text(
                    text = revealNarrative,
                    style = ParlorTheme.typography.bodyLarge,
                    color = colors.textSecondary,
                    modifier = Modifier
                        .alpha(narrativeAlpha)
                        .then(
                            if (narrativeAccessible) {
                                Modifier.semantics {
                                    liveRegion = LiveRegionMode.Polite
                                }
                            } else {
                                Modifier.clearAndSetSemantics { }
                            }
                        ),
                )
            }
        }
    }
}
