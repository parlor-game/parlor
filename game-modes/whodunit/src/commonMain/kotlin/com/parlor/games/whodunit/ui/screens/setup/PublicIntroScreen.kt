package com.parlor.games.whodunit.ui.screens.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.CatalogArtworkBadge
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.components.parlorSafeContentPadding
import com.parlor.designsystem.icons.ParlorArtwork
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.resources.setup_intro_continue
import com.parlor.games.whodunit.resources.setup_intro_continue_description
import com.parlor.games.whodunit.resources.setup_intro_read_aloud_eyebrow
import com.parlor.games.whodunit.resources.setup_intro_what_is_true_eyebrow
import com.parlor.games.whodunit.resources.round_clue_bullet_format
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
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .parlorSafeContentPadding(ParlorTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.l),
        ) {
            if (!compactTypography) {
                CatalogArtworkBadge(
                    artwork = ParlorArtwork.CaseFile,
                    modifier = Modifier.size(ParlorTheme.spacing.xxxl),
                )
            }
            EyebrowLabel(
                text = stringResource(Res.string.setup_intro_read_aloud_eyebrow),
                accent = false,
            )
            Text(
                text = title,
                style = ParlorTheme.typography.displayHero,
                color = ParlorTheme.colors.textPrimary,
            )
            Text(
                text = intro,
                style = ParlorTheme.typography.narration,
                color = ParlorTheme.colors.textNarration,
            )
            ParlorCard(modifier = Modifier.fillMaxWidth()) {
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
            ParlorButton(
                label = stringResource(Res.string.setup_intro_continue),
                contentDescription = stringResource(Res.string.setup_intro_continue_description),
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private const val COMPACT_FONT_SCALE_LIMIT = 1.3f
