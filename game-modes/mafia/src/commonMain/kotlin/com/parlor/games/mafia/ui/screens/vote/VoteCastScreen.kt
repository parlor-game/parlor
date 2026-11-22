package com.parlor.games.mafia.ui.screens.vote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import com.parlor.core.ids.PlayerId
import com.parlor.designsystem.components.CatalogArtworkBadge
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.icons.ParlorArtwork
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.mafia.resources.Res
import com.parlor.games.mafia.resources.vote_abstain
import com.parlor.games.mafia.resources.vote_announcement_continue
import com.parlor.games.mafia.resources.vote_announcement_continue_description
import com.parlor.games.mafia.resources.vote_announcement_eyebrow_format
import com.parlor.games.mafia.resources.vote_announcement_no_elimination
import com.parlor.games.mafia.resources.vote_announcement_revealed_role_format
import com.parlor.games.mafia.resources.vote_announcement_tally
import com.parlor.games.mafia.resources.vote_eyebrow_voter_format
import com.parlor.games.mafia.resources.vote_headline
import com.parlor.games.mafia.resources.vote_instructions
import com.parlor.games.mafia.resources.vote_revote_eyebrow_voter_format
import com.parlor.games.mafia.resources.vote_submit
import com.parlor.games.mafia.ui.components.MafiaTable
import com.parlor.games.mafia.ui.screens.night.PickableTarget
import com.parlor.games.mafia.ui.screens.night.TargetPickerScreen
import org.jetbrains.compose.resources.stringResource

/**
 * Vote-casting screen — used in both Pass-and-Play (cycled per player) and
 * Local/P2P (rendered to whichever peer hasn't voted yet). The vote target
 * is PUBLIC by spec; the per-player handoff in PaP is just for input, not
 * privacy.
 */
@Composable
fun VoteCastScreen(
    voterName: String,
    candidates: List<PickableTarget>,
    revoteRound: Int,
    onCast: (PlayerId) -> Unit,
    onAbstain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val eyebrow = if (revoteRound > 0) {
        stringResource(Res.string.vote_revote_eyebrow_voter_format, revoteRound, voterName)
    } else {
        stringResource(Res.string.vote_eyebrow_voter_format, voterName)
    }
    TargetPickerScreen(
        eyebrow = eyebrow,
        headline = stringResource(Res.string.vote_headline),
        instructions = stringResource(Res.string.vote_instructions),
        targets = candidates,
        submitLabel = stringResource(Res.string.vote_submit),
        onSubmit = { selected ->
            if (selected != null) onCast(selected) else onAbstain()
        },
        allowSkip = true,
        skipLabel = stringResource(Res.string.vote_abstain),
        privateSelection = false,
        modifier = modifier,
    )
}

/**
 * Vote announcement — public reveal of the tally and the eliminated player.
 */
@Composable
fun VoteAnnouncementScreen(
    day: Int,
    tally: Map<PlayerId, Int>,
    nameLookup: (PlayerId) -> String,
    eliminatedName: String?,
    eliminatedRole: String?,
    outcomeLine: String,
    onAcknowledged: (() -> Unit)?,
    waitingLabel: String? = null,
    modifier: Modifier = Modifier,
) {
    val compactTypography = LocalDensity.current.fontScale >= COMPACT_FONT_SCALE_LIMIT
    MafiaTable(
        modifier = modifier,
        actions = if (onAcknowledged != null || waitingLabel != null) {
            {
                if (onAcknowledged != null) {
                    ParlorButton(
                        label = stringResource(Res.string.vote_announcement_continue),
                        contentDescription = stringResource(
                            Res.string.vote_announcement_continue_description,
                        ),
                        onClick = onAcknowledged,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else if (waitingLabel != null) {
                    Text(
                        text = waitingLabel,
                        style = ParlorTheme.typography.bodyMedium,
                        color = ParlorTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            null
        },
    ) {
        ParlorCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = ParlorTheme.spacing.l,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
            ) {
                if (!compactTypography) {
                    CatalogArtworkBadge(
                        artwork = ParlorArtwork.Moon,
                        modifier = Modifier.size(ParlorTheme.spacing.xxl),
                    )
                }
                EyebrowLabel(
                    text = stringResource(Res.string.vote_announcement_eyebrow_format, day),
                )
                Text(
                    text = eliminatedName ?: stringResource(Res.string.vote_announcement_no_elimination),
                    style = ParlorTheme.typography.headingLarge,
                    color = if (eliminatedName != null) {
                        ParlorTheme.colors.semanticDanger
                    } else {
                        ParlorTheme.colors.textPrimary
                    },
                )
                if (eliminatedRole != null) {
                    Text(
                        text = stringResource(
                            Res.string.vote_announcement_revealed_role_format,
                            eliminatedRole,
                        ),
                        style = ParlorTheme.typography.bodyMedium,
                        color = ParlorTheme.colors.textSecondary,
                    )
                }
                Text(
                    text = outcomeLine,
                    style = ParlorTheme.typography.bodyMedium,
                    color = ParlorTheme.colors.textSecondary,
                )
            }
        }

        ParlorCard(modifier = Modifier.fillMaxWidth(), contentPadding = ParlorTheme.spacing.m) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
            ) {
                EyebrowLabel(text = stringResource(Res.string.vote_announcement_tally), accent = false)
                val maxVotes = tally.values.maxOrNull()?.coerceAtLeast(1) ?: 1
                tally.entries
                    .sortedByDescending { it.value }
                    .forEach { (id, count) ->
                        Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xxs)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = nameLookup(id),
                                    modifier = Modifier.weight(1f),
                                    style = ParlorTheme.typography.bodyMedium,
                                    color = ParlorTheme.colors.textPrimary,
                                )
                                Text(
                                    text = count.toString(),
                                    style = ParlorTheme.typography.headingMedium,
                                    color = ParlorTheme.colors.accentEmber,
                                )
                            }
                            VoteBar(count, maxVotes)
                        }
                    }
            }
        }
    }
}

private const val COMPACT_FONT_SCALE_LIMIT = 1.3f

@Composable
private fun VoteBar(votes: Int, maxVotes: Int) {
    val shape = RoundedCornerShape(ParlorTheme.radii.pill)
    Box(
        Modifier.fillMaxWidth()
            .height(ParlorTheme.spacing.xxs)
            .clip(shape)
            .background(ParlorTheme.colors.surfaceHigher)
            .clearAndSetSemantics { },
    ) {
        Box(
            Modifier.fillMaxWidth((votes.toFloat() / maxVotes).coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(shape)
                .background(ParlorTheme.colors.accentEmber),
        )
    }
}
