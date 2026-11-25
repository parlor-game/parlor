package com.parlor.games.whodunit.ui.screens.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.resources.setup_briefing_begin
import com.parlor.games.whodunit.resources.setup_briefing_begin_description
import com.parlor.games.whodunit.resources.setup_briefing_card_1
import com.parlor.games.whodunit.resources.setup_briefing_card_2
import com.parlor.games.whodunit.resources.setup_briefing_card_3
import com.parlor.games.whodunit.resources.setup_briefing_card_4
import com.parlor.games.whodunit.resources.setup_briefing_continue
import com.parlor.games.whodunit.resources.setup_briefing_continue_description
import com.parlor.games.whodunit.resources.setup_briefing_eyebrow
import com.parlor.games.whodunit.ui.components.WhodunitTable
import org.jetbrains.compose.resources.stringResource

@Composable
fun RulesBriefingScreen(
    cardIndex: Int,
    onAdvance: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cards = listOf(
        stringResource(Res.string.setup_briefing_card_1),
        stringResource(Res.string.setup_briefing_card_2),
        stringResource(Res.string.setup_briefing_card_3),
        stringResource(Res.string.setup_briefing_card_4),
    )
    val safeIndex = cardIndex.coerceIn(0, cards.size - 1)
    val isLast = safeIndex == cards.size - 1
    val buttonLabel = if (isLast) {
        stringResource(Res.string.setup_briefing_begin)
    } else {
        stringResource(Res.string.setup_briefing_continue)
    }
    val buttonDescription = if (isLast) {
        stringResource(Res.string.setup_briefing_begin_description)
    } else {
        stringResource(Res.string.setup_briefing_continue_description)
    }
    WhodunitTable(
        modifier = modifier,
        actions = {
            ParlorButton(
                label = buttonLabel,
                contentDescription = buttonDescription,
                onClick = { onAdvance(safeIndex + 1) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        EyebrowLabel(text = stringResource(Res.string.setup_briefing_eyebrow))
        Row(
            modifier = Modifier.fillMaxWidth().semantics {
                progressBarRangeInfo = ProgressBarRangeInfo((safeIndex + 1).toFloat(), 1f..cards.size.toFloat())
            },
            horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xs),
        ) {
            repeat(cards.size) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(ParlorTheme.spacing.xxs)
                        .clip(RoundedCornerShape(ParlorTheme.radii.pill))
                        .background(
                            if (index <= safeIndex) ParlorTheme.colors.accentEmber
                            else ParlorTheme.colors.borderElevated,
                        ),
                )
            }
        }
        ParlorCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = ParlorTheme.spacing.l,
        ) {
            Text(
                text = cards[safeIndex],
                style = ParlorTheme.typography.bodyLarge,
                color = ParlorTheme.colors.textPrimary,
            )
        }
    }
}
