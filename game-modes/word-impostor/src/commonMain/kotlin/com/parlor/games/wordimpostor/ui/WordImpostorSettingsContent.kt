package com.parlor.games.wordimpostor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.parlor.core.ids.CaseId
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.wordimpostor.domain.WordImpostorSettings
import com.parlor.games.wordimpostor.domain.WordTopic
import com.parlor.games.wordimpostor.resources.Res
import com.parlor.games.wordimpostor.resources.wi_impostor_option
import com.parlor.games.wordimpostor.resources.wi_impostors
import com.parlor.games.wordimpostor.resources.wi_majority
import com.parlor.games.wordimpostor.resources.wi_need_players
import com.parlor.games.wordimpostor.resources.wi_round_option
import com.parlor.games.wordimpostor.resources.wi_rounds
import com.parlor.games.wordimpostor.resources.wi_rules
import com.parlor.games.wordimpostor.resources.wi_settings
import com.parlor.games.wordimpostor.resources.wi_topic
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun WordImpostorSettingsContent(
    caseId: CaseId,
    playerCount: Int,
    enabled: Boolean,
    onChange: (CaseId) -> Unit,
) {
    val settings = requireNotNull(WordImpostorSettings.fromCaseId(caseId.raw))
    val colors = ParlorTheme.colors
    val spacing = ParlorTheme.spacing

    Column(verticalArrangement = Arrangement.spacedBy(spacing.m)) {
        Text(
            text = stringResource(Res.string.wi_settings),
            color = colors.textPrimary,
            style = ParlorTheme.typography.headingLarge,
        )

        ParlorCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = ParlorTheme.radii.card,
            contentPadding = spacing.m,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                EyebrowLabel(text = stringResource(Res.string.wi_topic), accent = true)
                FlowRow(
                    modifier = Modifier.selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.s),
                    verticalArrangement = Arrangement.spacedBy(spacing.s),
                ) {
                    WordTopic.entries.forEach { topic ->
                        WordOptionPill(
                            label = stringResource(WordContentResources.title(topic)),
                            selected = settings.topic == topic,
                            enabled = enabled,
                            onClick = { onChange(settings.copy(topic = topic).caseId) },
                        )
                    }
                }
            }
        }

        ParlorCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = ParlorTheme.radii.card,
            contentPadding = spacing.m,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                EyebrowLabel(text = stringResource(Res.string.wi_impostors), accent = true)
                FlowRow(
                    modifier = Modifier.selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.s),
                    verticalArrangement = Arrangement.spacedBy(spacing.s),
                ) {
                    for (count in 1..3) {
                        WordOptionPill(
                            label = pluralStringResource(Res.plurals.wi_impostor_option, count, count),
                            selected = settings.impostors == count,
                            enabled = enabled,
                            onClick = { onChange(settings.copy(impostors = count).caseId) },
                        )
                    }
                }
                Text(
                    text = stringResource(Res.string.wi_majority),
                    style = ParlorTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
                if (!settings.supports(playerCount)) {
                    Text(
                        text = stringResource(Res.string.wi_need_players, settings.impostors * 2 + 1),
                        style = ParlorTheme.typography.labelMedium,
                        color = colors.semanticDanger,
                    )
                }
            }
        }

        ParlorCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = ParlorTheme.radii.card,
            contentPadding = spacing.m,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                EyebrowLabel(text = stringResource(Res.string.wi_rounds), accent = true)
                FlowRow(
                    modifier = Modifier.selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.s),
                    verticalArrangement = Arrangement.spacedBy(spacing.s),
                ) {
                    WordImpostorSettings.ROUND_COUNTS.forEach { count ->
                        WordOptionPill(
                            label = pluralStringResource(Res.plurals.wi_round_option, count, count),
                            selected = settings.rounds == count,
                            enabled = enabled,
                            onClick = { onChange(settings.copy(rounds = count).caseId) },
                        )
                    }
                }
            }
        }

        ParlorCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = ParlorTheme.radii.card,
            contentPadding = spacing.m,
        ) {
            Text(
                text = stringResource(Res.string.wi_rules),
                color = colors.textSecondary,
                style = ParlorTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun WordOptionPill(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = ParlorTheme.colors
    val shape = RoundedCornerShape(ParlorTheme.radii.pill)
    val bg = when {
        selected && enabled -> colors.accentEmber
        selected -> colors.semanticMuted
        else -> colors.surfaceHigher
    }
    val fg = when {
        selected && enabled -> colors.textOnAccent
        !enabled -> colors.textTertiary
        else -> colors.textPrimary
    }
    val stroke = if (selected) colors.transparent else colors.borderSubtle

    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(bg)
            .border(ParlorTheme.borders.hairline, stroke, shape)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = ParlorTheme.spacing.m, vertical = ParlorTheme.spacing.s),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = ParlorTheme.typography.labelMedium,
            color = fg,
        )
    }
}
