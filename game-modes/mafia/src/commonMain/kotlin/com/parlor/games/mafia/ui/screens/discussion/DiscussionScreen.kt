package com.parlor.games.mafia.ui.screens.discussion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.parlor.designsystem.components.ContextRibbon
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.components.ParlorContextTone
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.mafia.resources.Res
import com.parlor.games.mafia.resources.discussion_alive_card
import com.parlor.games.mafia.resources.discussion_dead_card
import com.parlor.games.mafia.resources.discussion_eyebrow_format
import com.parlor.games.mafia.resources.discussion_headline
import com.parlor.games.mafia.resources.discussion_host_next
import com.parlor.games.mafia.resources.discussion_how_to
import com.parlor.games.mafia.resources.discussion_open_vote
import com.parlor.games.mafia.resources.discussion_open_vote_description
import com.parlor.games.mafia.resources.discussion_peer_next
import com.parlor.games.mafia.resources.discussion_public_table
import com.parlor.games.mafia.ui.components.MafiaTable
import org.jetbrains.compose.resources.stringResource

/**
 * Public discussion screen. Per the no-chat constraint, this only shows
 * a timer (or "untimed") and the list of living players. Conversation is
 * verbal/in-person.
 */
@Composable
fun DiscussionScreen(
    day: Int,
    aliveNames: List<String>,
    deadNames: List<String>,
    timerLabel: String?,
    isHost: Boolean,
    onOpenVote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MafiaTable(
        modifier = modifier,
        actions = if (isHost) {
            {
                ParlorButton(
                    label = stringResource(Res.string.discussion_open_vote),
                    contentDescription = stringResource(Res.string.discussion_open_vote_description),
                    onClick = onOpenVote,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            null
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xs)) {
            EyebrowLabel(text = stringResource(Res.string.discussion_eyebrow_format, day))
            Text(
                text = stringResource(Res.string.discussion_headline),
                style = ParlorTheme.typography.headingLarge,
                color = ParlorTheme.colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
        }
        ContextRibbon(
            label = stringResource(Res.string.discussion_public_table),
            detail = stringResource(
                if (isHost) Res.string.discussion_host_next else Res.string.discussion_peer_next,
            ),
            tone = ParlorContextTone.Public,
        )
        ParlorCard(modifier = Modifier.fillMaxWidth(), contentPadding = ParlorTheme.spacing.m) {
            Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xs)) {
                if (timerLabel != null) {
                    Text(
                        text = timerLabel,
                        style = ParlorTheme.typography.timerMedium,
                        color = ParlorTheme.colors.accentEmber,
                    )
                }
                Text(
                    text = stringResource(Res.string.discussion_how_to),
                    style = ParlorTheme.typography.bodyMedium,
                    color = ParlorTheme.colors.textSecondary,
                )
            }
        }

        PlayerListCard(title = stringResource(Res.string.discussion_alive_card), names = aliveNames)
        if (deadNames.isNotEmpty()) {
            PlayerListCard(
                title = stringResource(Res.string.discussion_dead_card),
                names = deadNames,
                dim = true,
            )
        }
    }
}

@Composable
private fun PlayerListCard(title: String, names: List<String>, dim: Boolean = false) {
    val dotColor = if (dim) ParlorTheme.colors.semanticMuted else ParlorTheme.colors.semanticSuccess
    ParlorCard(modifier = Modifier.fillMaxWidth(), contentPadding = ParlorTheme.spacing.m) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xs),
        ) {
            EyebrowLabel(text = title, accent = false)
            names.forEachIndexed { index, name ->
                if (index > 0) HorizontalDivider(color = ParlorTheme.colors.borderSubtle)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = ParlorTheme.spacing.xxs),
                    horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(ParlorTheme.spacing.s)
                            .clip(CircleShape)
                            .background(dotColor)
                            .clearAndSetSemantics { },
                    )
                    Text(
                        text = name,
                        style = ParlorTheme.typography.bodyMedium,
                        color = if (dim) ParlorTheme.colors.textSecondary else ParlorTheme.colors.textPrimary,
                    )
                }
            }
        }
    }
}
