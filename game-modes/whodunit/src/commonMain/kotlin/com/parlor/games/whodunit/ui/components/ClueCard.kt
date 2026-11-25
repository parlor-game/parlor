package com.parlor.games.whodunit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.whodunit.resources.Res
import com.parlor.games.whodunit.resources.round_clue_eyebrow_label
import org.jetbrains.compose.resources.stringResource

@Composable
fun ClueCard(
    text: String,
    eyebrow: String = stringResource(Res.string.round_clue_eyebrow_label),
    modifier: Modifier = Modifier,
) {
    ParlorCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = ParlorTheme.spacing.l,
        hero = true,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s)) {
            EyebrowLabel(text = eyebrow)
            Text(
                text = text,
                style = ParlorTheme.typography.headingLarge,
                color = ParlorTheme.colors.textPrimary,
            )
        }
    }
}
