package com.parlor.games.ghamza.ui

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.components.parlorSafeContentPadding
import com.parlor.designsystem.theme.ParlorTheme

@Composable
internal fun GhamzaFrame(
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
                        colors.borderGlow.copy(alpha = 0.20f),
                        colors.transparent,
                        colors.transparent,
                    ),
                ),
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
            drawCircle(
                color = colors.borderGlow.copy(alpha = 0.14f),
                radius = size.width * 0.68f,
                center = Offset(size.width, size.height * 0.22f),
            )
            drawCircle(
                color = colors.borderSubtle.copy(alpha = 0.45f),
                radius = size.width * 0.68f,
                center = Offset(size.width, size.height * 0.22f),
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }
        Column(
            Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .verticalScroll(scroll)
                .parlorSafeContentPadding(if (compact) spacing.m else spacing.l),
            verticalArrangement = Arrangement.spacedBy(if (compact) spacing.s else spacing.m),
            content = content,
        )
    }
}

@Composable
internal fun GhamzaPanel(
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
internal fun GhamzaStatusPill(
    text: String,
    accent: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = ParlorTheme.colors
    val shape = RoundedCornerShape(ParlorTheme.radii.pill)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (accent) colors.surfaceHero else colors.surfaceHigher)
            .border(
                ParlorTheme.borders.hairline,
                if (accent) colors.borderAccent.copy(alpha = 0.55f) else colors.borderSubtle,
                shape,
            )
            .padding(horizontal = ParlorTheme.spacing.m, vertical = ParlorTheme.spacing.xs),
    ) {
        Text(
            text = text,
            style = ParlorTheme.typography.labelSmall,
            color = if (accent) colors.accentEmber else colors.textSecondary,
        )
    }
}

@Composable
internal fun GhamzaTitle(
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
internal fun GhamzaBody(
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
internal fun GhamzaButton(
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
internal fun GhamzaHeaderAction(
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

/** Code-native eye artwork; no secret role is embedded in its accessibility semantics. */
@Composable
internal fun GhamzaEye(open: Boolean, modifier: Modifier = Modifier) {
    val colors = ParlorTheme.colors
    val openness by animateFloatAsState(if (open) 1f else 0f, tween(if (ParlorTheme.reducedMotion) 0 else 280))
    val artworkSize = if (LocalDensity.current.fontScale >= 1.5f) 60.dp else 120.dp
    Canvas(modifier.size(artworkSize).clearAndSetSemantics {}) {
        val path = Path().apply {
            moveTo(size.width * 0.1f, center.y)
            quadraticTo(center.x, size.height * 0.12f, size.width * 0.9f, center.y)
            quadraticTo(center.x, size.height * (0.12f + 0.76f * openness), size.width * 0.1f, center.y)
        }
        drawPath(path, colors.accentEmber, style = Stroke(3.5.dp.toPx()))
        if (openness > 0f) {
            drawCircle(colors.accentEmber.copy(alpha = 0.18f * openness), size.width * 0.20f * openness)
            drawCircle(colors.accentEmber.copy(alpha = openness), size.width * 0.11f * openness)
            drawCircle(
                colors.textOnAccent.copy(alpha = openness),
                size.width * 0.036f * openness,
                center + Offset(size.width * 0.025f, -size.height * 0.025f),
            )
        }
    }
}
