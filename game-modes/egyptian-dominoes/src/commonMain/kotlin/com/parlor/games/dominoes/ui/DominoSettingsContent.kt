package com.parlor.games.dominoes.ui

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
import com.parlor.games.dominoes.domain.DominoCompetition
import com.parlor.games.dominoes.domain.DominoSettings
import com.parlor.games.dominoes.domain.DominoVariant
import com.parlor.games.dominoes.resources.Res
import com.parlor.games.dominoes.resources.dom_block_variant
import com.parlor.games.dominoes.resources.dom_default_variant
import com.parlor.games.dominoes.resources.dom_draw_variant
import com.parlor.games.dominoes.resources.dom_individual
import com.parlor.games.dominoes.resources.dom_rules
import com.parlor.games.dominoes.resources.dom_settings
import com.parlor.games.dominoes.resources.dom_target
import com.parlor.games.dominoes.resources.dom_target_value
import com.parlor.games.dominoes.resources.dom_teams
import com.parlor.games.dominoes.resources.dom_teams_hint
import org.jetbrains.compose.resources.stringResource

@Composable
fun DominoSettingsContent(
    caseId: CaseId,
    playerCount: Int,
    enabled: Boolean,
    onChange: (CaseId) -> Unit,
) {
    val settings = requireNotNull(DominoSettings.fromCaseId(caseId.raw))
    val colors = ParlorTheme.colors
    val spacing = ParlorTheme.spacing

    Column(verticalArrangement = Arrangement.spacedBy(spacing.m)) {
        Text(
            text = stringResource(Res.string.dom_settings),
            color = colors.textPrimary,
            style = ParlorTheme.typography.headingLarge,
        )

        ParlorCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = spacing.m,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.m)) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    DominoVariant.entries.forEach { variant ->
                        DominoOptionPill(
                            label = stringResource(
                                when (variant) {
                                    DominoVariant.Default -> Res.string.dom_default_variant
                                    DominoVariant.Draw -> Res.string.dom_draw_variant
                                    DominoVariant.Block -> Res.string.dom_block_variant
                                },
                            ),
                            selected = settings.variant == variant,
                            enabled = enabled,
                            onClick = {
                                onChange(
                                    settings.copy(
                                        variant = variant,
                                        competition = DominoCompetition.Individual,
                                    ).caseId,
                                )
                            },
                        )
                    }
                }

                if (settings.variant == DominoVariant.Default &&
                    (playerCount == 4 || settings.competition == DominoCompetition.Teams)
                ) {
                    // Keep the Individual escape hatch visible if the roster shrinks after choosing Teams.
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().selectableGroup(),
                            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                            verticalArrangement = Arrangement.spacedBy(spacing.xs),
                        ) {
                            DominoCompetition.entries.forEach { competition ->
                                DominoOptionPill(
                                    label = stringResource(
                                        if (competition == DominoCompetition.Teams) {
                                            Res.string.dom_teams
                                        } else {
                                            Res.string.dom_individual
                                        },
                                    ),
                                    selected = settings.competition == competition,
                                    enabled = enabled && (competition == DominoCompetition.Individual || playerCount == 4),
                                    onClick = { onChange(settings.copy(competition = competition).caseId) },
                                )
                            }
                        }
                        Text(
                            text = stringResource(Res.string.dom_teams_hint),
                            color = colors.textSecondary,
                            style = ParlorTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        ParlorCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = spacing.m,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                EyebrowLabel(
                    text = stringResource(Res.string.dom_target),
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth().selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    DominoSettings.TARGETS.forEach { target ->
                        DominoOptionPill(
                            label = stringResource(Res.string.dom_target_value, target),
                            selected = settings.target == target,
                            enabled = enabled,
                            onClick = { onChange(settings.copy(target = target).caseId) },
                        )
                    }
                }
            }
        }

        ParlorCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = spacing.m,
        ) {
            Text(
                text = stringResource(Res.string.dom_rules),
                color = colors.textSecondary,
                style = ParlorTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun DominoOptionPill(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = ParlorTheme.colors
    val shape = RoundedCornerShape(ParlorTheme.radii.pill)
    val container = when {
        !enabled -> colors.surfaceInset
        selected -> colors.accentEmber
        else -> colors.surfaceHigher
    }
    val borderColor = when {
        !enabled -> colors.borderSubtle
        selected -> colors.accentEmber
        else -> colors.borderSubtle
    }
    val textColor = when {
        !enabled -> colors.textTertiary
        selected -> colors.textOnAccent
        else -> colors.textPrimary
    }
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(container)
            .border(ParlorTheme.borders.hairline, borderColor, shape)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            style = ParlorTheme.typography.labelLarge,
        )
    }
}
