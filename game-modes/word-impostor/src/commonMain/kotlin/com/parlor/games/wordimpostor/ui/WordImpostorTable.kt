package com.parlor.games.wordimpostor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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
import com.parlor.games.wordimpostor.domain.WordImpostorAction
import com.parlor.games.wordimpostor.domain.WordImpostorPhase
import com.parlor.games.wordimpostor.domain.WordImpostorState
import com.parlor.games.wordimpostor.domain.WordRole
import com.parlor.games.wordimpostor.resources.Res
import com.parlor.games.wordimpostor.resources.wi_answer_hint
import com.parlor.games.wordimpostor.resources.wi_answered
import com.parlor.games.wordimpostor.resources.wi_close_help
import com.parlor.games.wordimpostor.resources.wi_close_help_compact
import com.parlor.games.wordimpostor.resources.wi_discussion
import com.parlor.games.wordimpostor.resources.wi_discussion_hint
import com.parlor.games.wordimpostor.resources.wi_full_rules
import com.parlor.games.wordimpostor.resources.wi_guessing
import com.parlor.games.wordimpostor.resources.wi_help
import com.parlor.games.wordimpostor.resources.wi_help_compact
import com.parlor.games.wordimpostor.resources.wi_hide
import com.parlor.games.wordimpostor.resources.wi_hide_details
import com.parlor.games.wordimpostor.resources.wi_host_controls
import com.parlor.games.wordimpostor.resources.wi_impostor
import com.parlor.games.wordimpostor.resources.wi_impostor_hint
import com.parlor.games.wordimpostor.resources.wi_leave
import com.parlor.games.wordimpostor.resources.wi_leave_compact
import com.parlor.games.wordimpostor.resources.wi_open_vote
import com.parlor.games.wordimpostor.resources.wi_ordinary
import com.parlor.games.wordimpostor.resources.wi_pair
import com.parlor.games.wordimpostor.resources.wi_phase_progress
import com.parlor.games.wordimpostor.resources.wi_phase_results
import com.parlor.games.wordimpostor.resources.wi_phase_reveal
import com.parlor.games.wordimpostor.resources.wi_question_progress
import com.parlor.games.wordimpostor.resources.wi_question_prompt
import com.parlor.games.wordimpostor.resources.wi_question_wait
import com.parlor.games.wordimpostor.resources.wi_questions
import com.parlor.games.wordimpostor.resources.wi_quick_start
import com.parlor.games.wordimpostor.resources.wi_ready
import com.parlor.games.wordimpostor.resources.wi_ready_count
import com.parlor.games.wordimpostor.resources.wi_reveal
import com.parlor.games.wordimpostor.resources.wi_reveal_hint
import com.parlor.games.wordimpostor.resources.wi_reveal_title
import com.parlor.games.wordimpostor.resources.wi_round
import com.parlor.games.wordimpostor.resources.wi_rules
import com.parlor.games.wordimpostor.resources.wi_solo_impostor
import com.parlor.games.wordimpostor.resources.wi_step_one
import com.parlor.games.wordimpostor.resources.wi_step_three
import com.parlor.games.wordimpostor.resources.wi_step_two
import com.parlor.games.wordimpostor.resources.wi_teammates
import com.parlor.games.wordimpostor.resources.wi_title
import com.parlor.games.wordimpostor.resources.wi_voting
import org.jetbrains.compose.resources.stringResource

@Composable
fun WordImpostorTable(
    state: WordImpostorState,
    self: PlayerId,
    isHost: Boolean,
    enabled: Boolean,
    privacyEpoch: Long,
    onAction: (WordImpostorAction) -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var help by remember(state.public.token, privacyEpoch) { mutableStateOf(false) }
    var fullRules by remember(state.public.token, privacyEpoch, help) { mutableStateOf(false) }
    val compact = LocalDensity.current.fontScale >= 1.5f
    val leaveDescription = stringResource(Res.string.wi_leave)
    val helpDescription = stringResource(if (help) Res.string.wi_close_help else Res.string.wi_help)
    InPlaceBackHandler(help) { help = false }

    WordFrame(modifier, "${state.phase.id}:${state.public.token}:$help:${state.public.questionIndex}") {
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
                WordHeaderAction(
                    label = if (compact) stringResource(Res.string.wi_leave_compact) else leaveDescription,
                    contentDescription = leaveDescription,
                    onClick = onLeave,
                )
                if (!compact) {
                    CatalogArtworkBadge(
                        artwork = ParlorArtwork.Conversation,
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
            WordHeaderAction(
                label = if (compact) {
                    stringResource(if (help) Res.string.wi_close_help_compact else Res.string.wi_help_compact)
                } else {
                    helpDescription
                },
                contentDescription = helpDescription,
                onClick = { help = !help },
                accent = help,
            )
        }

        if (!compact) {
            WordStatusPill(
                text = stringResource(Res.string.wi_round, state.public.round, state.public.settings.rounds),
            )
        } else {
            EyebrowLabel(
                text = stringResource(Res.string.wi_round, state.public.round, state.public.settings.rounds),
            )
        }

        val step = state.phase.ordinal.coerceAtMost(5)
        val label = when (state.phase) {
            WordImpostorPhase.Reveal -> Res.string.wi_phase_reveal
            WordImpostorPhase.Questions -> Res.string.wi_questions
            WordImpostorPhase.Discussion -> Res.string.wi_discussion
            WordImpostorPhase.Voting -> Res.string.wi_voting
            WordImpostorPhase.Guessing -> Res.string.wi_guessing
            else -> Res.string.wi_phase_results
        }
        WordPhaseTrack(step, stringResource(Res.string.wi_phase_progress, step + 1, stringResource(label)))

        if (help) {
            WordTitle(stringResource(Res.string.wi_title))
            ParlorCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = ParlorTheme.radii.elevated,
                contentPadding = ParlorTheme.spacing.m,
            ) {
                QuickStartSteps(
                    title = stringResource(Res.string.wi_quick_start),
                    steps = listOf(
                        stringResource(Res.string.wi_step_one),
                        stringResource(Res.string.wi_step_two),
                        stringResource(Res.string.wi_step_three),
                    ),
                )
            }
            WordButton(
                text = stringResource(if (fullRules) Res.string.wi_hide_details else Res.string.wi_full_rules),
                secondary = true,
                onClick = { fullRules = !fullRules },
            )
            if (fullRules) {
                WordPanel {
                    WordBody(stringResource(Res.string.wi_rules))
                }
            }
        } else {
            when (state.phase) {
                WordImpostorPhase.Reveal -> WordSecretCard(state, self, enabled, privacyEpoch, true, onAction)
                WordImpostorPhase.Questions -> WordQuestions(state, self, enabled, onAction)
                WordImpostorPhase.Discussion -> WordDiscussion(state, isHost, enabled, onAction)
                WordImpostorPhase.Voting -> WordVoting(state, self, enabled, privacyEpoch, onAction)
                WordImpostorPhase.Guessing -> WordGuessing(state, self, enabled, privacyEpoch, onAction)
                WordImpostorPhase.RoundResult, WordImpostorPhase.MatchResult -> WordResults(state, isHost, enabled, onAction)
                WordImpostorPhase.Aborted -> Unit // Public-only termination is owned by the shared session shell.
            }
            if (state.phase in listOf(WordImpostorPhase.Questions, WordImpostorPhase.Discussion, WordImpostorPhase.Voting)) {
                WordSecretCard(state, self, enabled, privacyEpoch, false, onAction)
            }
        }
    }
}

@Composable
private fun WordSecretCard(
    state: WordImpostorState,
    self: PlayerId,
    enabled: Boolean,
    privacyEpoch: Long,
    readiness: Boolean,
    onAction: (WordImpostorAction) -> Unit,
) {
    // Deliberately not saveable: recovery, backgrounding and phase changes re-cover the secret.
    var revealed by remember(state.public.token, privacyEpoch, state.phase) { mutableStateOf(false) }
    InPlaceBackHandler(revealed) { revealed = false }
    val own = state.privatePerPlayer.getValue(self)
    WordPanel(hero = revealed) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
        ) {
            if (readiness) WordEmblem(revealed)
            if (!revealed) {
                WordTitle(stringResource(Res.string.wi_reveal_title), textAlign = TextAlign.Center)
                WordBody(stringResource(Res.string.wi_reveal_hint), textAlign = TextAlign.Center)
                WordButton(stringResource(Res.string.wi_reveal), enabled) { revealed = true }
            } else {
                if (own.role == WordRole.Impostor) {
                    EyebrowLabel(text = stringResource(Res.string.wi_phase_reveal), accent = true)
                    WordTitle(stringResource(Res.string.wi_impostor), textAlign = TextAlign.Center)
                    WordBody(stringResource(Res.string.wi_impostor_hint), textAlign = TextAlign.Center)
                    WordBody(
                        text = if (own.teammates.isEmpty()) stringResource(Res.string.wi_solo_impostor)
                            else stringResource(Res.string.wi_teammates, state.names(own.teammates)),
                        emphasized = true,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    EyebrowLabel(text = stringResource(Res.string.wi_ordinary), accent = true)
                    Text(
                        text = stringResource(WordContentResources.word(checkNotNull(own.wordId))),
                        style = ParlorTheme.typography.displayLarge,
                        color = ParlorTheme.colors.textPrimary,
                        textAlign = TextAlign.Center,
                    )
                }
                if (readiness && self !in state.public.ready) {
                    WordButton(stringResource(Res.string.wi_ready), enabled) {
                        revealed = false
                        onAction(WordImpostorAction.Ready(self, state.public.token))
                    }
                }
                // Concealment is local: pending/paused network actions must not disable it.
                WordButton(stringResource(Res.string.wi_hide), secondary = true) { revealed = false }
            }
            if (readiness) {
                WordStatusPill(
                    text = stringResource(Res.string.wi_ready_count, state.public.ready.size, state.players.size),
                    accent = false,
                )
            }
        }
    }
}

@Composable
private fun WordQuestions(
    state: WordImpostorState,
    self: PlayerId,
    enabled: Boolean,
    onAction: (WordImpostorAction) -> Unit,
) {
    val pair = state.public.interactions[state.public.questionIndex]
    val isAsker = self == pair.asker
    EyebrowLabel(text = stringResource(Res.string.wi_questions), accent = true)
    WordTitle(stringResource(Res.string.wi_pair, state.name(pair.asker), state.name(pair.answerer)))
    WordBody(
        text = stringResource(Res.string.wi_question_progress, state.public.questionIndex + 1, state.players.size),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    WordPanel(hero = isAsker) {
        if (isAsker) {
            EyebrowLabel(text = stringResource(Res.string.wi_question_prompt), accent = true)
            Text(
                text = stringResource(WordContentResources.question(checkNotNull(state.privatePerPlayer[self]?.questionId))),
                style = ParlorTheme.typography.headingLarge,
                color = ParlorTheme.colors.textPrimary,
            )
            WordButton(stringResource(Res.string.wi_answered), enabled) {
                onAction(WordImpostorAction.Answered(self, state.public.token, state.public.questionIndex))
            }
        } else {
            WordEmblem(false, Modifier.align(Alignment.CenterHorizontally))
            WordBody(
                text = stringResource(if (self == pair.answerer) Res.string.wi_answer_hint else Res.string.wi_question_wait),
                emphasized = self == pair.answerer,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun WordDiscussion(
    state: WordImpostorState,
    host: Boolean,
    enabled: Boolean,
    onAction: (WordImpostorAction) -> Unit,
) {
    WordTitle(stringResource(Res.string.wi_discussion))
    WordPanel(hero = host) {
        WordBody(stringResource(Res.string.wi_discussion_hint))
        if (host) {
            WordButton(stringResource(Res.string.wi_open_vote), enabled) {
                onAction(WordImpostorAction.OpenVoting(state.public.token))
            }
        } else {
            WordStatusPill(text = stringResource(Res.string.wi_host_controls), accent = false)
        }
    }
}

internal fun WordImpostorState.name(id: PlayerId): String = players.first { it.id == id }.displayName.asBidiArgument()
internal fun WordImpostorState.names(ids: Set<PlayerId>): String =
    players.filter { it.id in ids }.joinToString(" · ") { it.displayName.asBidiArgument() }
