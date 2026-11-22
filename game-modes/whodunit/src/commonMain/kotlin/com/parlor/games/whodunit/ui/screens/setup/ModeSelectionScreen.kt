package com.parlor.games.whodunit.ui.screens.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.parlor.core.ids.ModeId
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorButtonVariant
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.components.ScreenHeader
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.resources.setup_back
import com.parlor.games.whodunit.resources.setup_back_description
import com.parlor.games.whodunit.resources.setup_choose_mode_eyebrow
import com.parlor.games.whodunit.resources.setup_choose_mode_headline
import com.parlor.games.whodunit.resources.setup_mode_choose_description_format
import com.parlor.games.whodunit.resources.setup_mode_choose_format
import com.parlor.games.whodunit.resources.setup_mode_classic_body
import com.parlor.games.whodunit.resources.setup_mode_classic_meta
import com.parlor.games.whodunit.resources.setup_mode_classic_title
import com.parlor.games.whodunit.resources.setup_mode_elimination_body
import com.parlor.games.whodunit.resources.setup_mode_elimination_meta
import com.parlor.games.whodunit.resources.setup_mode_elimination_title
import com.parlor.games.whodunit.ui.components.WhodunitTable
import org.jetbrains.compose.resources.stringResource

@Composable
fun ModeSelectionScreen(
    onModeSelected: (ModeId) -> Unit,
    onBack: () -> Unit,
    caseSupportedModes: Collection<String>,
    caseSupportedPlayerCounts: IntRange,
    modifier: Modifier = Modifier,
) {
    val availableModes = whodunitModeChoices(
        caseSupportedModes = caseSupportedModes,
        caseSupportedPlayerCounts = caseSupportedPlayerCounts,
    )
    WhodunitTable(
        modifier = modifier,
        actions = {
            ParlorButton(
                label = stringResource(Res.string.setup_back),
                contentDescription = stringResource(Res.string.setup_back_description),
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                variant = ParlorButtonVariant.Ghost,
            )
        },
    ) {
        ScreenHeader(
            title = stringResource(Res.string.setup_choose_mode_headline),
            eyebrow = stringResource(Res.string.setup_choose_mode_eyebrow),
        )
        availableModes.forEach { choice ->
            val classic = choice.modeId == WhodunitIds.ClassicVoteModeId
            ModeCard(
                title = stringResource(
                    if (classic) {
                        Res.string.setup_mode_classic_title
                    } else {
                        Res.string.setup_mode_elimination_title
                    },
                ),
                body = stringResource(
                    if (classic) {
                        Res.string.setup_mode_classic_body
                    } else {
                        Res.string.setup_mode_elimination_body
                    },
                ),
                meta = stringResource(
                    if (classic) {
                        Res.string.setup_mode_classic_meta
                    } else {
                        Res.string.setup_mode_elimination_meta
                    },
                    choice.supportedPlayerCounts.first,
                    choice.supportedPlayerCounts.last,
                ),
                onClick = { onModeSelected(choice.modeId) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

internal data class WhodunitModeChoice(
    val modeId: ModeId,
    val supportedPlayerCounts: IntRange,
)

/** Returns only modes the selected, validated case can actually start. */
internal fun whodunitModeChoices(
    caseSupportedModes: Collection<String>,
    caseSupportedPlayerCounts: IntRange,
): List<WhodunitModeChoice> = listOf(
    WhodunitIds.ClassicVoteModeId to
        com.parlor.games.whodunit.domain.modes.ClassicVoteMode.supportedPlayerCounts,
    WhodunitIds.EliminationModeId to
        com.parlor.games.whodunit.domain.modes.EliminationMode.supportedPlayerCounts,
).mapNotNull { (modeId, moduleCounts) ->
    if (modeId.raw !in caseSupportedModes) return@mapNotNull null
    val first = maxOf(moduleCounts.first, caseSupportedPlayerCounts.first)
    val last = minOf(moduleCounts.last, caseSupportedPlayerCounts.last)
    if (first > last) null else WhodunitModeChoice(modeId, first..last)
}

@Composable
private fun ModeCard(
    title: String,
    body: String,
    meta: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val buttonLabel = stringResource(Res.string.setup_mode_choose_format, title)
    val buttonDescription = stringResource(Res.string.setup_mode_choose_description_format, title)
    ParlorCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = ParlorTheme.spacing.l,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s)) {
            Text(
                text = title,
                style = ParlorTheme.typography.headingMedium,
                color = ParlorTheme.colors.textPrimary,
            )
            Text(
                text = body,
                style = ParlorTheme.typography.bodyMedium,
                color = ParlorTheme.colors.textSecondary,
            )
            Text(
                text = meta,
                style = ParlorTheme.typography.labelMedium,
                color = ParlorTheme.colors.accentEmber,
            )
            ParlorButton(
                label = buttonLabel,
                contentDescription = buttonDescription,
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
