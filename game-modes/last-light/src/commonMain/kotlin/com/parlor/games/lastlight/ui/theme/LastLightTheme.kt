// Adapted from PartyDeck Standard, commit df649e6c896203bdf93130f6497e229757d5da30.
package com.parlor.games.lastlight.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.designsystem.tokens.isLight

/** Semantic card-table color roles resolved directly from ParlorTheme (Dark and Light). */
object LastLightColors {
    val Ink: Color
        @Composable @ReadOnlyComposable get() = ParlorTheme.colors.surfaceCanvas
    val Surface: Color
        @Composable @ReadOnlyComposable get() = ParlorTheme.colors.surfaceElevated
    val SurfaceHero: Color
        @Composable @ReadOnlyComposable get() = ParlorTheme.colors.surfaceHero
    val SurfaceInset: Color
        @Composable @ReadOnlyComposable get() = ParlorTheme.colors.surfaceInset
    val Paper: Color
        @Composable @ReadOnlyComposable get() = ParlorTheme.colors.textPrimary
    val CardPaper: Color
        @Composable @ReadOnlyComposable get() = ParlorTheme.colors.surfacePaper
    val CardInk: Color
        @Composable @ReadOnlyComposable get() = ParlorTheme.colors.textOnPaper
    val OnAccent: Color
        @Composable @ReadOnlyComposable get() = ParlorTheme.colors.textOnAccent
    val Muted: Color
        @Composable @ReadOnlyComposable get() = ParlorTheme.colors.textSecondary
    val Citron: Color
        @Composable @ReadOnlyComposable get() = ParlorTheme.colors.accentEmber
    val Safe: Color
        @Composable @ReadOnlyComposable get() = ParlorTheme.colors.semanticSuccess
    val Copper: Color
        @Composable @ReadOnlyComposable get() = ParlorTheme.colors.semanticDanger
    val Divider: Color
        @Composable @ReadOnlyComposable get() = ParlorTheme.colors.borderSubtle
    val Outline: Color
        @Composable @ReadOnlyComposable get() = ParlorTheme.colors.borderElevated
    val Error: Color
        @Composable @ReadOnlyComposable get() = ParlorTheme.colors.semanticDanger
    val Transparent: Color
        @Composable @ReadOnlyComposable get() = ParlorTheme.colors.transparent
}

val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
fun LastLightTheme(
    reduceMotion: Boolean = ParlorTheme.reducedMotion,
    content: @Composable () -> Unit,
) {
    val colors = ParlorTheme.colors
    val parlorType = ParlorTheme.typography
    val colorScheme = remember(colors) {
        if (colors.isLight) {
            lightColorScheme(
                primary = colors.accentEmber,
                onPrimary = colors.textOnAccent,
                primaryContainer = colors.surfaceHero,
                onPrimaryContainer = colors.accentEmber,
                secondary = colors.accentBrass,
                onSecondary = colors.textOnAccent,
                secondaryContainer = colors.surfaceElevated,
                onSecondaryContainer = colors.textPrimary,
                tertiary = colors.semanticDanger,
                onTertiary = colors.textOnAccent,
                tertiaryContainer = colors.surfaceHigher,
                onTertiaryContainer = colors.textPrimary,
                background = colors.surfaceCanvas,
                onBackground = colors.textPrimary,
                surface = colors.surfaceElevated,
                onSurface = colors.textPrimary,
                surfaceVariant = colors.surfaceHigher,
                onSurfaceVariant = colors.textSecondary,
                outline = colors.borderElevated,
                outlineVariant = colors.borderSubtle,
                error = colors.semanticDanger,
                onError = colors.textOnAccent,
                errorContainer = colors.surfaceHigher,
                onErrorContainer = colors.semanticDanger,
                scrim = colors.overlayScrim,
            )
        } else {
            darkColorScheme(
                primary = colors.accentEmber,
                onPrimary = colors.textOnAccent,
                primaryContainer = colors.surfaceHero,
                onPrimaryContainer = colors.accentEmber,
                secondary = colors.accentBrass,
                onSecondary = colors.textOnAccent,
                secondaryContainer = colors.surfaceElevated,
                onSecondaryContainer = colors.textPrimary,
                tertiary = colors.semanticDanger,
                onTertiary = colors.textOnAccent,
                tertiaryContainer = colors.surfaceHigher,
                onTertiaryContainer = colors.textPrimary,
                background = colors.surfaceCanvas,
                onBackground = colors.textPrimary,
                surface = colors.surfaceElevated,
                onSurface = colors.textPrimary,
                surfaceVariant = colors.surfaceHigher,
                onSurfaceVariant = colors.textSecondary,
                outline = colors.borderElevated,
                outlineVariant = colors.borderSubtle,
                error = colors.semanticDanger,
                onError = colors.textOnAccent,
                errorContainer = colors.surfaceHigher,
                onErrorContainer = colors.semanticDanger,
                scrim = colors.overlayScrim,
            )
        }
    }
    val typography = remember(parlorType) {
        Typography(
            displayLarge = parlorType.displayHero,
            displayMedium = parlorType.displayLarge,
            displaySmall = parlorType.displayMedium,
            headlineLarge = parlorType.displayLarge,
            headlineMedium = parlorType.displayMedium,
            headlineSmall = parlorType.headingLarge,
            titleLarge = parlorType.headingLarge,
            titleMedium = parlorType.headingMedium,
            titleSmall = parlorType.labelLarge,
            bodyLarge = parlorType.bodyLarge,
            bodyMedium = parlorType.bodyMedium,
            bodySmall = parlorType.bodySmall,
            labelLarge = parlorType.labelLarge,
            labelMedium = parlorType.labelMedium,
            labelSmall = parlorType.labelSmall,
        )
    }
    CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(6.dp),
                small = RoundedCornerShape(10.dp),
                medium = RoundedCornerShape(16.dp),
                large = RoundedCornerShape(24.dp),
                extraLarge = RoundedCornerShape(28.dp),
            ),
            content = content,
        )
    }
}
