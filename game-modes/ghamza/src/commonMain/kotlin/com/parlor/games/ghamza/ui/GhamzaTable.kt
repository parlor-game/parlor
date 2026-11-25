package com.parlor.games.ghamza.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.parlor.core.ids.PlayerId
import com.parlor.designsystem.components.CatalogArtworkBadge
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.InPlaceBackHandler
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.components.QuickStartSteps
import com.parlor.designsystem.components.SectionDivider
import com.parlor.designsystem.icons.ParlorArtwork
import com.parlor.designsystem.localization.asBidiArgument
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.ghamza.domain.GhamzaAction
import com.parlor.games.ghamza.domain.GhamzaPhase
import com.parlor.games.ghamza.domain.GhamzaRole
import com.parlor.games.ghamza.domain.GhamzaRules
import com.parlor.games.ghamza.domain.GhamzaState
import com.parlor.games.ghamza.resources.Res
import com.parlor.games.ghamza.resources.gh_active
import com.parlor.games.ghamza.resources.gh_attempts_left
import com.parlor.games.ghamza.resources.gh_cancel
import com.parlor.games.ghamza.resources.gh_circle
import com.parlor.games.ghamza.resources.gh_close_help
import com.parlor.games.ghamza.resources.gh_close_help_compact
import com.parlor.games.ghamza.resources.gh_confirm
import com.parlor.games.ghamza.resources.gh_confirm_body
import com.parlor.games.ghamza.resources.gh_confirm_title
import com.parlor.games.ghamza.resources.gh_eliminated
import com.parlor.games.ghamza.resources.gh_final
import com.parlor.games.ghamza.resources.gh_final_wait
import com.parlor.games.ghamza.resources.gh_final_you
import com.parlor.games.ghamza.resources.gh_full_rules
import com.parlor.games.ghamza.resources.gh_guess_confirm
import com.parlor.games.ghamza.resources.gh_guess_result
import com.parlor.games.ghamza.resources.gh_guess_submit
import com.parlor.games.ghamza.resources.gh_guest
import com.parlor.games.ghamza.resources.gh_guest_hint
import com.parlor.games.ghamza.resources.gh_help
import com.parlor.games.ghamza.resources.gh_help_compact
import com.parlor.games.ghamza.resources.gh_hide
import com.parlor.games.ghamza.resources.gh_hide_details
import com.parlor.games.ghamza.resources.gh_leave
import com.parlor.games.ghamza.resources.gh_leave_compact
import com.parlor.games.ghamza.resources.gh_loser
import com.parlor.games.ghamza.resources.gh_match_complete
import com.parlor.games.ghamza.resources.gh_next
import com.parlor.games.ghamza.resources.gh_next_lives
import com.parlor.games.ghamza.resources.gh_out
import com.parlor.games.ghamza.resources.gh_quick_start
import com.parlor.games.ghamza.resources.gh_ready
import com.parlor.games.ghamza.resources.gh_ready_count
import com.parlor.games.ghamza.resources.gh_rematch
import com.parlor.games.ghamza.resources.gh_result
import com.parlor.games.ghamza.resources.gh_reveal
import com.parlor.games.ghamza.resources.gh_reveal_hint
import com.parlor.games.ghamza.resources.gh_reveal_title
import com.parlor.games.ghamza.resources.gh_revealed
import com.parlor.games.ghamza.resources.gh_round
import com.parlor.games.ghamza.resources.gh_rules
import com.parlor.games.ghamza.resources.gh_social
import com.parlor.games.ghamza.resources.gh_social_hint
import com.parlor.games.ghamza.resources.gh_status
import com.parlor.games.ghamza.resources.gh_step_one
import com.parlor.games.ghamza.resources.gh_step_three
import com.parlor.games.ghamza.resources.gh_step_two
import com.parlor.games.ghamza.resources.gh_title
import com.parlor.games.ghamza.resources.gh_wait_host
import com.parlor.games.ghamza.resources.gh_winked
import com.parlor.games.ghamza.resources.gh_winker
import com.parlor.games.ghamza.resources.gh_winker_hint
import com.parlor.games.ghamza.resources.gh_winner
import org.jetbrains.compose.resources.stringResource

@Composable
fun GhamzaTable(
    state: GhamzaState,
    self: PlayerId,
    isHost: Boolean,
    enabled: Boolean,
    privacyEpoch: Long,
    onAction: (GhamzaAction) -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var help by remember(state.public.token, privacyEpoch) { mutableStateOf(false) }
    var fullRules by remember(state.public.token, privacyEpoch, help) { mutableStateOf(false) }
    val compact = LocalDensity.current.fontScale >= 1.5f
    val leaveDescription = stringResource(Res.string.gh_leave)
    val helpDescription = stringResource(if (help) Res.string.gh_close_help else Res.string.gh_help)
    InPlaceBackHandler(help) { help = false }

    GhamzaFrame(modifier, "${state.phase.id}:${state.public.token}:$help") {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xs),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GhamzaHeaderAction(
                    label = if (compact) stringResource(Res.string.gh_leave_compact) else leaveDescription,
                    contentDescription = leaveDescription,
                    onClick = onLeave,
                )
                if (!compact) {
                    CatalogArtworkBadge(
                        artwork = ParlorArtwork.Wink,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(ParlorTheme.radii.subtle))
                            .border(
                                ParlorTheme.borders.hairline,
                                ParlorTheme.colors.borderSubtle,
                                RoundedCornerShape(ParlorTheme.radii.subtle),
                            ),
                    )
                }
            }
            GhamzaHeaderAction(
                label = if (compact) {
                    stringResource(if (help) Res.string.gh_close_help_compact else Res.string.gh_help_compact)
                } else {
                    helpDescription
                },
                contentDescription = helpDescription,
                onClick = { help = !help },
                accent = help,
            )
        }

        if (!compact) {
            GhamzaStatusPill(
                text = stringResource(Res.string.gh_round, state.public.round, state.public.settings.rounds),
            )
        } else {
            EyebrowLabel(
                text = stringResource(Res.string.gh_round, state.public.round, state.public.settings.rounds),
            )
        }

        if (help) {
            GhamzaTitle(stringResource(Res.string.gh_title))
            ParlorCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = ParlorTheme.radii.elevated,
                contentPadding = ParlorTheme.spacing.m,
            ) {
                QuickStartSteps(
                    title = stringResource(Res.string.gh_quick_start),
                    steps = listOf(
                        stringResource(Res.string.gh_step_one),
                        stringResource(Res.string.gh_step_two),
                        stringResource(Res.string.gh_step_three),
                    ),
                )
            }
            GhamzaButton(
                text = stringResource(if (fullRules) Res.string.gh_hide_details else Res.string.gh_full_rules),
                secondary = true,
                onClick = { fullRules = !fullRules },
            )
            if (fullRules) {
                GhamzaPanel {
                    GhamzaBody(stringResource(Res.string.gh_rules))
                }
            }
        } else {
            when (state.phase) {
                GhamzaPhase.Reveal -> GhamzaRoleCard(state, self, enabled, privacyEpoch, true, onAction)
                GhamzaPhase.Social -> GhamzaSocial(state, self, enabled, privacyEpoch, onAction)
                GhamzaPhase.FinalGuess -> GhamzaFinalGuess(state, self, enabled, privacyEpoch, onAction)
                GhamzaPhase.RoundResult, GhamzaPhase.MatchResult -> GhamzaResults(state, isHost, enabled, onAction)
                GhamzaPhase.Aborted -> Unit // The session shell replaces aborted games with a public-only terminal surface.
            }
        }
    }
}

@Composable
private fun GhamzaRoleCard(
    state: GhamzaState,
    self: PlayerId,
    enabled: Boolean,
    privacyEpoch: Long,
    readiness: Boolean,
    onAction: (GhamzaAction) -> Unit,
) {
    var revealed by remember(state.public.token, privacyEpoch, state.phase) { mutableStateOf(false) }
    InPlaceBackHandler(revealed) { revealed = false }
    GhamzaPanel(hero = revealed) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (readiness) GhamzaEye(revealed)
            if (!revealed) {
                GhamzaTitle(stringResource(Res.string.gh_reveal_title), textAlign = TextAlign.Center)
                GhamzaBody(stringResource(Res.string.gh_reveal_hint), textAlign = TextAlign.Center)
                GhamzaButton(stringResource(Res.string.gh_reveal), enabled) { revealed = true }
            } else {
                val winker = state.privatePerPlayer[self]?.role == GhamzaRole.Winker
                GhamzaTitle(
                    text = stringResource(if (winker) Res.string.gh_winker else Res.string.gh_guest),
                    textAlign = TextAlign.Center,
                )
                GhamzaBody(
                    text = stringResource(if (winker) Res.string.gh_winker_hint else Res.string.gh_guest_hint),
                    textAlign = TextAlign.Center,
                )
                if (readiness && self !in state.public.ready) {
                    GhamzaButton(stringResource(Res.string.gh_ready), enabled) {
                        revealed = false
                        onAction(GhamzaAction.Ready(self, state.public.token))
                    }
                }
                // Concealing local private content never waits for a host acknowledgement.
                GhamzaButton(stringResource(Res.string.gh_hide), secondary = true) { revealed = false }
            }
            if (readiness) {
                GhamzaStatusPill(
                    text = stringResource(Res.string.gh_ready_count, state.public.ready.size, state.players.size),
                    accent = false,
                )
            }
        }
    }
}

@Composable
private fun GhamzaSocial(
    state: GhamzaState,
    self: PlayerId,
    enabled: Boolean,
    privacyEpoch: Long,
    onAction: (GhamzaAction) -> Unit,
) {
    var confirming by remember(state.public.token, privacyEpoch) { mutableStateOf(false) }
    InPlaceBackHandler(confirming) { confirming = false }
    val used = state.public.reports.getValue(self)
    if (confirming) {
        GhamzaPanel(hero = true) {
            GhamzaTitle(stringResource(Res.string.gh_confirm_title))
            GhamzaBody(stringResource(Res.string.gh_confirm_body))
            GhamzaButton(stringResource(Res.string.gh_confirm), enabled) {
                confirming = false
                onAction(GhamzaAction.Winked(self, state.public.token, used + 1))
            }
            GhamzaButton(stringResource(Res.string.gh_cancel), secondary = true) { confirming = false }
        }
        return
    }

    val colors = ParlorTheme.colors
    val isGuest = state.privatePerPlayer[self]?.role == GhamzaRole.Guest
    val canReport = isGuest && used < state.public.settings.attempts

    GhamzaPanel(hero = canReport) {
        GhamzaTitle(stringResource(Res.string.gh_social))
        GhamzaBody(stringResource(Res.string.gh_social_hint))
        if (isGuest) {
            if (used < state.public.settings.attempts) {
                GhamzaStatusPill(
                    text = stringResource(Res.string.gh_attempts_left, state.public.settings.attempts - used),
                    accent = true,
                )
                GhamzaButton(stringResource(Res.string.gh_winked), enabled) { confirming = true }
            } else {
                GhamzaStatusPill(text = stringResource(Res.string.gh_out), accent = false)
            }
        }
    }

    GhamzaReportFeed(state)

    GhamzaPanel {
        EyebrowLabel(text = stringResource(Res.string.gh_circle), accent = false)
        state.players.forEachIndexed { index, player ->
            val remaining = GhamzaRules.livesRemaining(state, player.id)
            val active = remaining > 0
            if (index > 0) SectionDivider()
            Row(
                Modifier.fillMaxWidth().padding(vertical = ParlorTheme.spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xxs)) {
                    GhamzaBody(
                        text = stringResource(Res.string.gh_status, player.displayName.asBidiArgument(), remaining),
                        emphasized = active,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(ParlorTheme.iconSize.xxs)
                                .clip(RoundedCornerShape(ParlorTheme.radii.pill))
                                .background(if (active) colors.semanticSuccess else colors.semanticDanger),
                        )
                        Text(
                            text = stringResource(if (active) Res.string.gh_active else Res.string.gh_eliminated),
                            color = if (active) colors.semanticSuccess else colors.semanticDanger,
                            style = ParlorTheme.typography.labelMedium,
                        )
                    }
                }
                GhamzaAttemptMarks(remaining, state.public.settings.attempts)
            }
        }
    }

    GhamzaRoleCard(state, self, enabled, privacyEpoch, false, onAction)
}

@Composable
private fun GhamzaFinalGuess(
    state: GhamzaState,
    self: PlayerId,
    enabled: Boolean,
    privacyEpoch: Long,
    onAction: (GhamzaAction) -> Unit,
) {
    var selected by remember(state.public.token, privacyEpoch) { mutableStateOf<PlayerId?>(null) }
    InPlaceBackHandler(selected != null) { selected = null }
    GhamzaTitle(stringResource(Res.string.gh_final))
    // Keep the report that triggered the final stage visible to every seat.
    GhamzaReportFeed(state)
    GhamzaPanel(hero = state.public.finalGuesser == self) {
        if (state.public.finalGuesser != self) {
            GhamzaBody(
                text = stringResource(Res.string.gh_final_wait, state.name(checkNotNull(state.public.finalGuesser))),
                emphasized = true,
            )
        } else {
            val target = selected
            if (target == null) {
                GhamzaBody(stringResource(Res.string.gh_final_you), emphasized = true)
                state.players.filter { it.id != self }.forEach { player ->
                    GhamzaButton(player.displayName, enabled, true) { selected = player.id }
                }
            } else {
                GhamzaTitle(stringResource(Res.string.gh_guess_confirm, state.name(target)))
                GhamzaButton(stringResource(Res.string.gh_guess_submit), enabled) {
                    onAction(GhamzaAction.Guess(self, state.public.token, target))
                }
                GhamzaButton(stringResource(Res.string.gh_cancel), secondary = true) { selected = null }
            }
        }
    }
}

@Composable
private fun GhamzaResults(
    state: GhamzaState,
    host: Boolean,
    enabled: Boolean,
    onAction: (GhamzaAction) -> Unit,
) {
    val result = checkNotNull(state.public.result)
    val matchFinished = state.phase == GhamzaPhase.MatchResult

    GhamzaPanel(hero = true) {
        GhamzaStatusPill(
            text = stringResource(if (matchFinished) Res.string.gh_match_complete else Res.string.gh_result),
            accent = true,
        )
        GhamzaTitle(stringResource(Res.string.gh_winner, state.name(result.winner)))
    }

    if (host) {
        GhamzaButton(stringResource(if (matchFinished) Res.string.gh_rematch else Res.string.gh_next), enabled) {
            onAction(
                if (matchFinished) GhamzaAction.Rematch(state.public.token)
                else GhamzaAction.NextRound(state.public.token),
            )
        }
    } else {
        GhamzaStatusPill(text = stringResource(Res.string.gh_wait_host), accent = false)
    }

    GhamzaPanel {
        GhamzaBody(stringResource(Res.string.gh_guess_result, state.name(result.guesser), state.name(result.guessed)), emphasized = true)
        SectionDivider()
        GhamzaBody(stringResource(Res.string.gh_revealed, state.name(result.winker)))
        GhamzaBody(stringResource(Res.string.gh_loser, state.name(result.loser)))
        if (!matchFinished) GhamzaBody(stringResource(Res.string.gh_next_lives))
    }
}

private fun GhamzaState.name(id: PlayerId): String = players.first { it.id == id }.displayName.asBidiArgument()
