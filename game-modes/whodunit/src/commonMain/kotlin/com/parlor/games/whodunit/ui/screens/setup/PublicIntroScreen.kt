package com.parlor.games.whodunit.ui.screens.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.parlor.designsystem.components.CatalogArtworkBadge
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.icons.ParlorArtwork
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.resources.round_clue_bullet_format
import com.parlor.games.whodunit.resources.setup_intro_continue
import com.parlor.games.whodunit.resources.setup_intro_continue_description
import com.parlor.games.whodunit.resources.setup_intro_read_aloud_eyebrow
import com.parlor.games.whodunit.resources.setup_intro_what_is_true_eyebrow
import com.parlor.games.whodunit.ui.components.WhodunitTable
import org.jetbrains.compose.resources.stringResource

@Composable
fun PublicIntroScreen(
    title: String,
    intro: String,
    bedrockClues: List<String>,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val compactTypography = LocalDensity.current.fontScale >= COMPACT_FONT_SCALE_LIMIT
    WhodunitTable(
        modifier = modifier,
        actions = {
            ParlorButton(
                label = stringResource(Res.string.setup_intro_continue),
                contentDescription = stringResource(Res.string.setup_intro_continue_description),
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!compactTypography) {
                CatalogArtworkBadge(
                    artwork = ParlorArtwork.CaseFile,
                    modifier = Modifier.size(ParlorTheme.spacing.xxl),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xxs),
            ) {
                EyebrowLabel(text = stringResource(Res.string.setup_intro_read_aloud_eyebrow))
                Text(
                    text = title,
                    style = ParlorTheme.typography.headingLarge,
                    color = ParlorTheme.colors.textPrimary,
                    modifier = Modifier.semantics { heading() },
                )
            }
        }
        Text(
            text = intro,
            style = ParlorTheme.typography.bodyLarge,
            color = ParlorTheme.colors.textSecondary,
        )
        ParlorCard(modifier = Modifier.fillMaxWidth(), contentPadding = ParlorTheme.spacing.l) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
            ) {
                EyebrowLabel(text = stringResource(Res.string.setup_intro_what_is_true_eyebrow))
                bedrockClues.forEachIndexed { index, clue ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = ParlorTheme.colors.borderSubtle,
                            thickness = ParlorTheme.borders.hairline,
                        )
                    }
                    Text(
                        text = stringResource(Res.string.round_clue_bullet_format, clue),
                        style = ParlorTheme.typography.bodyLarge,
                        color = ParlorTheme.colors.textPrimary,
                    )
                }
            }
        }
    }
}

private const val COMPACT_FONT_SCALE_LIMIT = 1.3f
