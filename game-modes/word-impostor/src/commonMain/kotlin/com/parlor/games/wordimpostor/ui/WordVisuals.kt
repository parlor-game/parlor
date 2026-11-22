package com.parlor.games.wordimpostor.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.components.parlorSafeContentPadding
import com.parlor.designsystem.theme.ParlorTheme

@Composable
internal fun WordFrame(
    modifier: Modifier,
    resetKey: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    // A new phase, round or help surface starts at its next-action header, not the previous ballot's scroll position.
    val scroll = key(resetKey) { rememberScrollState() }
    val compact = LocalDensity.current.fontScale >= 1.5f
    val colors = ParlorTheme.colors
    val spacing = ParlorTheme.spacing
    Box(
        modifier
            .fillMaxSize()
            .background(colors.surfaceCanvas)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        colors.borderGlow.copy(alpha = 0.22f),
                        colors.transparent,
                        colors.transparent,
                    ),
                ),
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
            drawCircle(
                color = colors.borderGlow.copy(alpha = 0.16f),
                radius = size.width * 0.72f,
                center = Offset(size.width * 0.92f, size.height * 0.16f),
            )
            drawCircle(
                color = colors.borderSubtle.copy(alpha = 0.45f),
                radius = size.width * 0.62f,
                center = Offset(0f, size.height * 0.84f),
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }
        Column(
            Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .verticalScroll(scroll)
                .parlorSafeContentPadding(if (compact) spacing.m else spacing.l),
            verticalArrangement = Arrangement.spacedBy(if (compact) spacing.s else spacing.m),
            content = content,
        )
    }
}

@Composable
internal fun WordPanel(
    hero: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val compact = LocalDensity.current.fontScale >= 1.5f
    val spacing = ParlorTheme.spacing
    ParlorCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = ParlorTheme.radii.elevated,
        contentPadding = if (compact) spacing.m else spacing.l,
        hero = hero,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(if (compact) spacing.s else spacing.m),
            content = content,
        )
    }
}

@Composable
internal fun WordStatusPill(
    text: String,
    accent: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = ParlorTheme.colors
    val shape = RoundedCornerShape(ParlorTheme.radii.pill)
    val bg = if (accent) colors.surfaceHero else colors.surfaceHigher
    val stroke = if (accent) colors.borderAccent.copy(alpha = 0.55f) else colors.borderSubtle
    val fg = if (accent) colors.accentEmber else colors.textSecondary
    Box(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .border(ParlorTheme.borders.hairline, stroke, shape)
            .padding(horizontal = ParlorTheme.spacing.m, vertical = ParlorTheme.spacing.xs),
    ) {
        Text(
            text = text,
            style = ParlorTheme.typography.labelSmall,
            color = fg,
        )
    }
}

@Composable
internal fun WordTitle(
    text: String,
    textAlign: TextAlign? = null,
    modifier: Modifier = Modifier,
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Use a semantic heading tier on compact enlarged-text layouts; never cap the user's font scale.
        val style = if (maxWidth / fontScale < 180.dp) ParlorTheme.typography.headingLarge
            else ParlorTheme.typography.displayMedium
        Text(
            text = text,
            style = style,
            color = ParlorTheme.colors.textPrimary,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth().semantics { heading() },
        )
    }
}

@Composable
internal fun WordBody(
    text: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    textAlign: TextAlign? = null,
) {
    val colors = ParlorTheme.colors
    Text(
        text = text,
        style = if (emphasized) ParlorTheme.typography.labelLarge else ParlorTheme.typography.bodyMedium,
        color = if (emphasized) colors.textPrimary else colors.textSecondary,
        textAlign = textAlign,
        modifier = modifier,
    )
}

@Composable
internal fun WordButton(
    text: String,
    enabled: Boolean = true,
    secondary: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = ParlorTheme.colors
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(ParlorTheme.radii.card)

    val containerColor = when {
        !enabled && secondary -> colors.surfaceInset
        !enabled -> colors.semanticMuted
        secondary -> colors.surfaceHigher
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
        else -> colors.transparent
    }
    val pressedTint = if (pressed && enabled) {
        if (secondary) colors.accentEmber.copy(alpha = 0.12f)
        else colors.accentEmberGlow.copy(alpha = 0.28f)
    } else {
        colors.transparent
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(containerColor)
            .background(pressedTint)
            .border(ParlorTheme.borders.hairline, borderColor, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
            )
            .padding(horizontal = ParlorTheme.spacing.m, vertical = ParlorTheme.spacing.s + ParlorTheme.spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = ParlorTheme.typography.labelLarge,
            color = contentColor,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun WordHeaderAction(
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    accent: Boolean = false,
) {
    val colors = ParlorTheme.colors
    val shape = RoundedCornerShape(ParlorTheme.radii.pill)
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (accent) colors.surfaceHero else colors.surfaceElevated)
            .border(
                width = ParlorTheme.borders.hairline,
                color = if (accent) colors.borderAccent.copy(alpha = 0.6f) else colors.borderSubtle,
                shape = shape,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .padding(horizontal = ParlorTheme.spacing.m, vertical = ParlorTheme.spacing.s),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = ParlorTheme.typography.labelMedium,
            color = if (accent) colors.accentEmber else colors.textPrimary,
        )
    }
}

/** A public phase indicator: never keeps an outgoing secret screen in an animation. */
@Composable
internal fun WordPhaseTrack(step: Int, label: String) {
    val colors = ParlorTheme.colors
    val duration = if (ParlorTheme.reducedMotion) 0 else 240
    val compact = LocalDensity.current.fontScale >= 1.5f
    val progress = ProgressBarRangeInfo((step + 1).toFloat(), 1f..6f)
    Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xs)) {
        if (!compact) {
            Text(
                text = label,
                color = colors.accentEmber,
                style = ParlorTheme.typography.labelMedium,
                modifier = Modifier.semantics { progressBarRangeInfo = progress },
            )
        }
        // At large text, the real phase heading below already supplies the
        // title. Keep the complete progress label on the bar for assistive
        // technology instead of repeating a tall heading above every action.
        Row(
            Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {
                    if (compact) {
                        contentDescription = label
                        progressBarRangeInfo = progress
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xs),
        ) {
            repeat(6) { index ->
                val target = when {
                    index == step -> colors.accentEmber
                    index < step -> colors.accentEmber.copy(alpha = 0.65f)
                    else -> colors.surfaceHigher
                }
                val color by animateColorAsState(target, tween(duration))
                Box(
                    Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(ParlorTheme.radii.pill))
                        .background(color),
                )
            }
        }
    }
}

/** Code-native stacked secret cards, with a small public-only settle on reveal. */
@Composable
internal fun WordEmblem(open: Boolean, modifier: Modifier = Modifier) {
    val colors = ParlorTheme.colors
    val angle by animateFloatAsState(if (open) 9f else -9f, tween(if (ParlorTheme.reducedMotion) 0 else 280))
    val artworkSize = if (LocalDensity.current.fontScale >= 1.5f) 56.dp else 104.dp
    Canvas(modifier.size(artworkSize).clearAndSetSemantics {}) {
        val card = Size(size.width * 0.56f, size.height * 0.74f)
        val origin = center - Offset(card.width / 2f, card.height / 2f)
        rotate(-angle) {
            drawRoundRect(
                color = colors.surfaceHigher,
                topLeft = origin - Offset(8.dp.toPx(), 0f),
                size = card,
                cornerRadius = CornerRadius(12.dp.toPx()),
            )
            drawRoundRect(
                color = colors.borderElevated,
                topLeft = origin - Offset(8.dp.toPx(), 0f),
                size = card,
                cornerRadius = CornerRadius(12.dp.toPx()),
                style = Stroke(1.2.dp.toPx()),
            )
        }
        rotate(angle) {
            drawRoundRect(
                color = if (open) colors.accentEmber else colors.surfaceHero,
                topLeft = origin,
                size = card,
                cornerRadius = CornerRadius(12.dp.toPx()),
            )
            drawRoundRect(
                color = if (open) colors.textOnAccent.copy(alpha = 0.25f) else colors.borderAccent,
                topLeft = origin,
                size = card,
                cornerRadius = CornerRadius(12.dp.toPx()),
                style = Stroke(1.5.dp.toPx()),
            )
            val glyphColor = if (open) colors.textOnAccent else colors.accentEmber
            drawRoundRect(
                color = glyphColor.copy(alpha = 0.28f),
                topLeft = origin + Offset(5.dp.toPx(), 5.dp.toPx()),
                size = Size(card.width - 10.dp.toPx(), card.height - 10.dp.toPx()),
                cornerRadius = CornerRadius(8.dp.toPx()),
                style = Stroke(1.dp.toPx()),
            )
            drawCircle(
                color = glyphColor,
                radius = 8.dp.toPx(),
                center = center - Offset(0f, 6.dp.toPx()),
                style = Stroke(2.5.dp.toPx()),
            )
            drawCircle(
                color = glyphColor,
                radius = 2.dp.toPx(),
                center = center + Offset(0f, 14.dp.toPx()),
            )
        }
    }
}

@Composable
internal fun WordScoreBar(
    label: String,
    score: Int,
    maximum: Int,
    highlight: Boolean = false,
) {
    val colors = ParlorTheme.colors
    val progress by animateFloatAsState(
        score.toFloat() / maximum.coerceAtLeast(1),
        tween(if (ParlorTheme.reducedMotion) 0 else 500),
    )
    val shape = RoundedCornerShape(ParlorTheme.radii.pill)
    val barColor: Color = if (highlight) colors.semanticSuccess else colors.accentEmber
    Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xs)) {
        Text(
            text = label,
            style = ParlorTheme.typography.bodyMedium,
            color = colors.textPrimary,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(shape)
                .background(colors.surfaceInset)
                .border(ParlorTheme.borders.hairline, colors.borderSubtle, shape),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(shape)
                    .background(barColor),
            )
        }
    }
}
