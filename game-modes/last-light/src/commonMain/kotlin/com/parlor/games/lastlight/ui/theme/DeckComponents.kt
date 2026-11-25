// Adapted from PartyDeck Standard, commit df649e6c896203bdf93130f6497e229757d5da30.
package com.parlor.games.lastlight.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parlor.designsystem.theme.ParlorTheme

@Composable
fun DeckButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    secondary: Boolean = false,
    accent: Color = LastLightColors.Citron,
) {
    val colors = ParlorTheme.colors
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp),
        enabled = enabled,
        shape = RoundedCornerShape(ParlorTheme.radii.card),
        border = if (secondary) BorderStroke(ParlorTheme.borders.hairline, colors.borderElevated) else null,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (secondary) colors.surfaceElevated else accent,
            contentColor = if (secondary) colors.textPrimary else colors.textOnAccent,
            disabledContainerColor = colors.surfaceHigher,
            disabledContentColor = colors.textTertiary,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LastLightColors.Muted,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = MaterialTheme.typography.labelSmall.copy(
            letterSpacing = if (LocalLayoutDirection.current == LayoutDirection.Rtl) 0.sp else 1.4.sp,
        ),
    )
}
