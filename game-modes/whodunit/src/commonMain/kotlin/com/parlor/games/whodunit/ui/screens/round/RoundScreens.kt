package com.parlor.games.whodunit.ui.screens.round

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.parlor.designsystem.components.CatalogArtworkBadge
import com.parlor.designsystem.components.ContextRibbon
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.components.ParlorContextTone
import com.parlor.designsystem.icons.ParlorArtwork
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.whodunit.domain.state.PublicTimerState
import com.parlor.games.whodunit.domain.state.RevealedClue
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.resources.round_begin_discussion
import com.parlor.games.whodunit.resources.round_begin_discussion_description
import com.parlor.games.whodunit.resources.round_clue_eyebrow_format
import com.parlor.games.whodunit.resources.round_context_public
import com.parlor.games.whodunit.resources.round_discussion_title
import com.parlor.games.whodunit.resources.round_eyebrow_format
import com.parlor.games.whodunit.resources.round_known_so_far
import com.parlor.games.whodunit.resources.round_move_on
import com.parlor.games.whodunit.resources.round_move_on_description
import com.parlor.games.whodunit.resources.round_reveal_clue_button
import com.parlor.games.whodunit.resources.round_reveal_clue_description
import com.parlor.games.whodunit.ui.components.ClueCard
import com.parlor.games.whodunit.ui.components.TimerRibbon
import com.parlor.games.whodunit.ui.components.WhodunitTable
import org.jetbrains.compose.resources.stringResource

@Composable
fun RoundTitleCardScreen(
    roundIndex: Int,
    title: String,
    tagline: String,
    authorityLabel: String,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val compactTypography = LocalDensity.current.fontScale >= COMPACT_FONT_SCALE_LIMIT
    WhodunitTable(
        modifier = modifier,
        actions = {
            ParlorButton(
                label = stringResource(Res.string.round_reveal_clue_button),
                contentDescription = stringResource(Res.string.round_reveal_clue_description),
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        ContextRibbon(
            label = stringResource(Res.string.round_context_public),
            detail = authorityLabel,
            tone = ParlorContextTone.Public,
        )
        ParlorCard(modifier = Modifier.fillMaxWidth(), hero = true) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
            ) {
                if (!compactTypography) {
                    CatalogArtworkBadge(
                        artwork = ParlorArtwork.CaseFile,
                        modifier = Modifier.size(ParlorTheme.spacing.xxxl),
                    )
                }
                EyebrowLabel(
                    text = stringResource(Res.string.round_eyebrow_format, roundIndex),
                    accent = true,
                )
                Text(
                    text = title,
                    style = ParlorTheme.typography.headingLarge,
                    color = ParlorTheme.colors.textPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = tagline,
                    style = ParlorTheme.typography.bodyMedium,
                    color = ParlorTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun ClueRevealScreen(
    clue: RevealedClue,
    authorityLabel: String,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WhodunitTable(
        modifier = modifier,
        actions = {
            ParlorButton(
                label = stringResource(Res.string.round_begin_discussion),
                contentDescription = stringResource(Res.string.round_begin_discussion_description),
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        ContextRibbon(
            label = stringResource(Res.string.round_context_public),
            detail = authorityLabel,
            tone = ParlorContextTone.Public,
        )
        EyebrowLabel(
            text = stringResource(Res.string.round_clue_eyebrow_format, clue.roundIndex),
            accent = false,
        )
        ClueCard(text = clue.text)
    }
}

@Composable
fun DiscussionScreen(
    timer: PublicTimerState?,
    revealedClues: List<RevealedClue>,
    authorityLabel: String,
    onAdvance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WhodunitTable(
        modifier = modifier,
        actions = {
            ParlorButton(
                label = stringResource(Res.string.round_move_on),
                contentDescription = stringResource(Res.string.round_move_on_description),
                onClick = onAdvance,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        Text(
            text = stringResource(Res.string.round_discussion_title),
            style = ParlorTheme.typography.headingLarge,
            color = ParlorTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        ContextRibbon(
            label = stringResource(Res.string.round_context_public),
            detail = authorityLabel,
            tone = ParlorContextTone.Public,
        )
        if (timer != null) {
            TimerRibbon(
                remainingSeconds = timer.remainingSeconds,
                totalSeconds = timer.totalSeconds,
                paused = timer.paused,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            text = stringResource(Res.string.round_known_so_far),
            style = ParlorTheme.typography.headingMedium,
            color = ParlorTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        revealedClues.asReversed().forEachIndexed { index, clue ->
            ParlorCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = ParlorTheme.spacing.m,
                hero = index == 0,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s)) {
                    EyebrowLabel(
                        text = stringResource(Res.string.round_clue_eyebrow_format, clue.roundIndex),
                        accent = false,
                    )
                    Text(
                        text = clue.text,
                        style = ParlorTheme.typography.bodyMedium,
                        color = ParlorTheme.colors.textPrimary,
                    )
                }
            }
        }
    }
}

private const val COMPACT_FONT_SCALE_LIMIT = 1.3f
