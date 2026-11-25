package com.parlor.games.whodunit.ui.screens.postgame

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.parlor.designsystem.components.CatalogArtworkBadge
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorButtonVariant
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.icons.ParlorArtwork
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.resources.postgame_back_to_library
import com.parlor.games.whodunit.resources.postgame_back_to_library_description
import com.parlor.games.whodunit.resources.postgame_eyebrow
import com.parlor.games.whodunit.resources.postgame_party_exit
import com.parlor.games.whodunit.resources.postgame_question
import com.parlor.games.whodunit.resources.postgame_replay
import com.parlor.games.whodunit.resources.postgame_replay_description
import com.parlor.games.whodunit.resources.postgame_try_other_mode
import com.parlor.games.whodunit.resources.postgame_try_other_mode_description
import com.parlor.games.whodunit.ui.components.WhodunitTable
import org.jetbrains.compose.resources.stringResource

@Composable
fun PostGameScreen(
    onReplaySameCase: () -> Unit,
    onTryOtherMode: () -> Unit,
    onBackToLibrary: () -> Unit,
    modifier: Modifier = Modifier,
    returnToParty: Boolean = false,
) {
    val compactTypography = LocalDensity.current.fontScale >= COMPACT_FONT_SCALE_LIMIT
    WhodunitTable(
        modifier = modifier,
        actions = {
            if (!returnToParty) {
                ParlorButton(
                    label = stringResource(Res.string.postgame_replay),
                    contentDescription = stringResource(Res.string.postgame_replay_description),
                    onClick = onReplaySameCase,
                    modifier = Modifier.fillMaxWidth(),
                )
                ParlorButton(
                    label = stringResource(Res.string.postgame_try_other_mode),
                    contentDescription = stringResource(Res.string.postgame_try_other_mode_description),
                    onClick = onTryOtherMode,
                    modifier = Modifier.fillMaxWidth(),
                    variant = ParlorButtonVariant.Secondary,
                )
            }
            ParlorButton(
                label = stringResource(if (returnToParty) Res.string.postgame_party_exit else Res.string.postgame_back_to_library),
                contentDescription = stringResource(
                    if (returnToParty) Res.string.postgame_party_exit else Res.string.postgame_back_to_library_description,
                ),
                onClick = onBackToLibrary,
                modifier = Modifier.fillMaxWidth(),
                variant = if (returnToParty) ParlorButtonVariant.Primary else ParlorButtonVariant.Ghost,
            )
        },
    ) {
        ParlorCard(modifier = Modifier.fillMaxWidth(), contentPadding = ParlorTheme.spacing.l) {
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
                    verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xs),
                ) {
                    EyebrowLabel(text = stringResource(Res.string.postgame_eyebrow))
                    Text(
                        text = stringResource(Res.string.postgame_question),
                        style = ParlorTheme.typography.headingLarge,
                        color = ParlorTheme.colors.textPrimary,
                    )
                }
            }
        }
    }
}

private const val COMPACT_FONT_SCALE_LIMIT = 1.3f
