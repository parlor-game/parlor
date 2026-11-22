package com.parlor.games.dominoes.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.components.parlorSafeContentPadding
import com.parlor.designsystem.theme.ParlorTheme

@Composable
internal fun DominoFrame(modifier: Modifier, resetKey: String, content: @Composable ColumnScope.() -> Unit) {
    // A new phase, round or help surface starts at its next-action header, not the previous ballot's scroll position.
    val scroll = key(resetKey) { rememberScrollState() }
    val colors = ParlorTheme.colors
    val spacing = ParlorTheme.spacing
    val backdrop = Brush.verticalGradient(
        listOf(
            colors.surfaceCanvas,
            colors.surfaceInset,
            colors.surfaceCanvas,
        ),
    )
    Box(
        modifier
            .fillMaxSize()
            .background(backdrop),
        contentAlignment = Alignment.TopCenter,
    ) {
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        colors.accentEmber.copy(alpha = 0.09f),
                        colors.surfaceCanvas.copy(alpha = 0f),
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.14f),
                    radius = size.maxDimension * 0.65f,
                ),
            )
            // Bounded deterministic tabletop grain, not an allocated bitmap or an animated noise field.
            repeat(120) { index ->
                val x = ((index * 137) % 997) / 997f * size.width
                val y = ((index * 251) % 991) / 991f * size.height
                drawCircle(colors.textPrimary.copy(alpha = 0.03f), 0.6.dp.toPx(), Offset(x, y))
            }
            drawRoundRect(colors.borderSubtle.copy(alpha = 0.45f), style = Stroke(8.dp.toPx()))
            drawRoundRect(colors.accentEmber.copy(alpha = 0.16f), style = Stroke(1.5.dp.toPx()))
        }
        Column(
            Modifier
                .widthIn(max = 900.dp)
                .fillMaxSize()
                .verticalScroll(scroll)
                .parlorSafeContentPadding(spacing.l),
            verticalArrangement = Arrangement.spacedBy(spacing.m),
            content = content,
        )
    }
}

@Composable
internal fun DominoTitle(text: String) {
    val fontScale = LocalDensity.current.fontScale
    val colors = ParlorTheme.colors
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Use a semantic heading tier on compact enlarged-text layouts; never cap the user's font scale.
        val style = if (maxWidth / fontScale < 180.dp) {
            ParlorTheme.typography.headingLarge
        } else {
            ParlorTheme.typography.displayMedium
        }
        Text(
            text = text,
            style = style,
            color = colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
internal fun DominoBody(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = ParlorTheme.colors.textSecondary,
        style = ParlorTheme.typography.bodyLarge,
        modifier = modifier,
    )
}

@Composable
internal fun DominoButton(
    text: String,
    enabled: Boolean = true,
    secondary: Boolean = false,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val colors = ParlorTheme.colors
    val shape = RoundedCornerShape(ParlorTheme.radii.card)
    val container = when {
        !enabled -> colors.surfaceHigher
        secondary -> colors.surfaceElevated
        else -> colors.accentEmber
    }
    val contentColor = when {
        !enabled -> colors.textTertiary
        secondary -> colors.textPrimary
        else -> colors.textOnAccent
    }
    val borderColor = when {
        !enabled -> colors.borderSubtle
        secondary -> colors.borderElevated
        else -> colors.accentEmber
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(container)
            .border(ParlorTheme.borders.hairline, borderColor, shape)
            .clickable(enabled = enabled, role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = contentColor,
            style = ParlorTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun DominoTopAction(
    text: String,
    accented: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = ParlorTheme.colors
    val shape = RoundedCornerShape(ParlorTheme.radii.pill)
    val container = when {
        !enabled -> colors.surfaceInset
        accented -> colors.accentEmber.copy(alpha = 0.14f)
        else -> colors.surfaceElevated
    }
    val border = when {
        !enabled -> colors.borderSubtle
        accented -> colors.accentEmber.copy(alpha = 0.55f)
        else -> colors.borderSubtle
    }
    val content = when {
        !enabled -> colors.textTertiary
        accented -> colors.accentEmber
        else -> colors.textPrimary
    }
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(container)
            .border(ParlorTheme.borders.hairline, border, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = content,
            style = ParlorTheme.typography.labelLarge,
        )
    }
}
