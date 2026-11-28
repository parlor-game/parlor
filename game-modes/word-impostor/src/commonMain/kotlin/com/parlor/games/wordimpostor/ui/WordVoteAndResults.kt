package com.parlor.games.wordimpostor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.parlor.core.ids.PlayerId
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.InPlaceBackHandler
import com.parlor.designsystem.components.SectionDivider
import com.parlor.designsystem.localization.asBidiArgument
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.wordimpostor.domain.WordImpostorAction
import com.parlor.games.wordimpostor.domain.WordImpostorPhase
import com.parlor.games.wordimpostor.domain.WordImpostorState
import com.parlor.games.wordimpostor.domain.WordRole
import com.parlor.games.wordimpostor.resources.Res
import com.parlor.games.wordimpostor.resources.wi_cancel
import com.parlor.games.wordimpostor.resources.wi_champions
import com.parlor.games.wordimpostor.resources.wi_correct_voters
import com.parlor.games.wordimpostor.resources.wi_guess_confirm
import com.parlor.games.wordimpostor.resources.wi_guess_count
import com.parlor.games.wordimpostor.resources.wi_guess_hint
import com.parlor.games.wordimpostor.resources.wi_guess_recorded
import com.parlor.games.wordimpostor.resources.wi_guess_result
import com.parlor.games.wordimpostor.resources.wi_guess_wait
import com.parlor.games.wordimpostor.resources.wi_guessing
import com.parlor.games.wordimpostor.resources.wi_hide
import com.parlor.games.wordimpostor.resources.wi_impostors_were
import com.parlor.games.wordimpostor.resources.wi_lock_guess
import com.parlor.games.wordimpostor.resources.wi_lock_vote
import com.parlor.games.wordimpostor.resources.wi_next
import com.parlor.games.wordimpostor.resources.wi_no_correct_voters
import com.parlor.games.wordimpostor.resources.wi_rematch
import com.parlor.games.wordimpostor.resources.wi_result
import com.parlor.games.wordimpostor.resources.wi_reveal
import com.parlor.games.wordimpostor.resources.wi_reveal_hint
import com.parlor.games.wordimpostor.resources.wi_score
import com.parlor.games.wordimpostor.resources.wi_scoreboard
import com.parlor.games.wordimpostor.resources.wi_vote_confirm
import com.parlor.games.wordimpostor.resources.wi_vote_count
import com.parlor.games.wordimpostor.resources.wi_vote_counts
import com.parlor.games.wordimpostor.resources.wi_vote_recorded
import com.parlor.games.wordimpostor.resources.wi_voted_count
import com.parlor.games.wordimpostor.resources.wi_voting
import com.parlor.games.wordimpostor.resources.wi_voting_hint
import com.parlor.games.wordimpostor.resources.wi_wait_host
import com.parlor.games.wordimpostor.resources.wi_word_was
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun WordVoting(
    state: WordImpostorState,
    self: PlayerId,
    enabled: Boolean,
    privacyEpoch: Long,
    onAction: (WordImpostorAction) -> Unit,
) {
    var selected by remember(state.public.token, privacyEpoch) { mutableStateOf<PlayerId?>(null) }
    InPlaceBackHandler(selected != null) { selected = null }
    WordTitle(stringResource(Res.string.wi_voting))
    WordBody(
        text = stringResource(Res.string.wi_voted_count, state.public.voted.size, state.players.size),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    WordPanel(hero = selected != null) {
        if (self in state.public.voted) {
            WordEmblem(false, Modifier.align(Alignment.CenterHorizontally))
            WordBody(
                text = stringResource(Res.string.wi_vote_recorded),
                emphasized = true,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            val target = selected
            if (target == null) {
                WordBody(stringResource(Res.string.wi_voting_hint))
                state.players.filter { it.id != self }.forEach { player ->
                    WordButton(player.displayName, enabled, true) { selected = player.id }
                }
            } else {
                WordTitle(stringResource(Res.string.wi_vote_confirm, state.name(target)))
                WordButton(stringResource(Res.string.wi_lock_vote), enabled) {
                    onAction(WordImpostorAction.Vote(self, state.public.token, target))
                }
                WordButton(stringResource(Res.string.wi_cancel), secondary = true) { selected = null }
            }
        }
    }
}

@Composable
internal fun WordGuessing(
    state: WordImpostorState,
    self: PlayerId,
    enabled: Boolean,
    privacyEpoch: Long,
    onAction: (WordImpostorAction) -> Unit,
) {
    val own = state.privatePerPlayer.getValue(self)
    // Guesses/role screens do not survive recovery or an interrupted app surface.
    var revealed by remember(state.public.token, privacyEpoch) { mutableStateOf(false) }
    var selected by remember(state.public.token, privacyEpoch) { mutableStateOf<String?>(null) }
    InPlaceBackHandler(revealed || selected != null) {
        if (selected != null) selected = null else revealed = false
    }
    WordTitle(stringResource(Res.string.wi_guessing))
    WordBody(stringResource(Res.string.wi_guess_count, state.public.guessCount, state.public.settings.impostors))
    WordPanel(hero = revealed) {
        when {
            !revealed -> {
                WordBody(stringResource(Res.string.wi_reveal_hint))
                WordButton(stringResource(Res.string.wi_reveal), enabled) { revealed = true }
            }
            own.role != WordRole.Impostor -> WordBody(stringResource(Res.string.wi_guess_wait))
            own.guess != null -> WordBody(stringResource(Res.string.wi_guess_recorded), emphasized = true)
            else -> {
                val choice = selected
                WordBody(stringResource(Res.string.wi_guess_hint))
                if (choice == null) {
                    own.choices.forEach { id ->
                        WordButton(stringResource(WordContentResources.word(id)), enabled, true) { selected = id }
                    }
                } else {
                    WordTitle(stringResource(Res.string.wi_guess_confirm, stringResource(WordContentResources.word(choice))))
                    WordButton(stringResource(Res.string.wi_lock_guess), enabled) {
                        onAction(WordImpostorAction.Guess(self, state.public.token, choice))
                        revealed = false
                        selected = null
                    }
                    WordButton(stringResource(Res.string.wi_cancel), secondary = true) { selected = null }
                }
            }
        }
        if (revealed) WordButton(stringResource(Res.string.wi_hide), secondary = true) { revealed = false; selected = null }
    }
    // Keep the next action ahead of a potentially twelve-seat poll recap.
    WordVoteCounts(state)
}

@Composable
private fun WordVoteCounts(state: WordImpostorState) {
    val counts = checkNotNull(state.public.voteCounts)
    WordPanel {
        EyebrowLabel(text = stringResource(Res.string.wi_vote_counts), accent = false)
        Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s)) {
            state.players.sortedByDescending { counts.getValue(it.id) }.forEach { player ->
                WordScoreBar(
                    label = stringResource(Res.string.wi_vote_count, player.displayName.asBidiArgument(), counts.getValue(player.id)),
                    score = counts.getValue(player.id),
                    maximum = state.players.size,
                )
            }
        }
    }
}

@Composable
internal fun WordResults(
    state: WordImpostorState,
    host: Boolean,
    enabled: Boolean,
    onAction: (WordImpostorAction) -> Unit,
) {
    val result = checkNotNull(state.public.result)
    val matchFinished = state.phase == WordImpostorPhase.MatchResult
    val winners = state.matchWinners()
    if (winners != null) {
        WordTitle(stringResource(Res.string.wi_champions, state.names(winners)))
    } else {
        WordTitle(stringResource(Res.string.wi_result))
    }

    WordPanel(hero = true) {
        EyebrowLabel(text = stringResource(Res.string.wi_word_was), accent = true)
        Text(
            text = stringResource(WordContentResources.word(result.wordId)),
            style = ParlorTheme.typography.displayLarge,
            color = ParlorTheme.colors.textPrimary,
        )
        SectionDivider()
        WordBody(
            text = stringResource(Res.string.wi_impostors_were, state.names(result.impostors)),
            emphasized = true,
        )
    }

    if (host) {
        WordButton(stringResource(if (matchFinished) Res.string.wi_rematch else Res.string.wi_next), enabled) {
            onAction(
                if (matchFinished) WordImpostorAction.Rematch(state.public.token)
                else WordImpostorAction.NextRound(state.public.token),
            )
        }
    } else {
        WordStatusPill(text = stringResource(Res.string.wi_wait_host), accent = false)
    }

    WordPanel {
        WordBody(
            text = if (result.correctVoters.isEmpty()) stringResource(Res.string.wi_no_correct_voters)
                else stringResource(Res.string.wi_correct_voters, state.names(result.correctVoters)),
            emphasized = result.correctVoters.isNotEmpty(),
        )
        state.players.filter { it.id in result.impostors }.forEach { player ->
            WordBody(
                stringResource(
                    Res.string.wi_guess_result,
                    player.displayName.asBidiArgument(),
                    stringResource(WordContentResources.word(result.guesses.getValue(player.id))),
                    result.awarded.getValue(player.id),
                ),
            )
        }
    }

    WordVoteCounts(state)

    WordPanel {
        EyebrowLabel(text = stringResource(Res.string.wi_scoreboard), accent = true)
        Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s)) {
            state.players.sortedByDescending { state.public.scores.getValue(it.id) }.forEach { player ->
                val playerScore = state.public.scores.getValue(player.id)
                WordScoreBar(
                    label = stringResource(
                        Res.string.wi_score,
                        player.displayName.asBidiArgument(),
                        playerScore,
                        result.awarded.getValue(player.id),
                    ),
                    score = playerScore,
                    maximum = state.public.settings.rounds,
                    highlight = playerScore > 0 && winners?.contains(player.id) == true,
                )
            }
        }
    }
}
