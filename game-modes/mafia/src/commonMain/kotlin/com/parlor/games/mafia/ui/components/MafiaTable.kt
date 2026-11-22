package com.parlor.games.mafia.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.StickyActionLayout
import com.parlor.designsystem.components.parlorSafeContentPadding
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.designsystem.tokens.CozyNoirPalette
import com.parlor.designsystem.tokens.LightCozyNoirPalette
import com.parlor.designsystem.tokens.isLight

/**
 * The catalog can still mark Mafia in red. At the table, amber identifies
 * routine actions and semantic danger identifies losses and Mafia roles.
 */
@Composable
internal fun MafiaTableTheme(content: @Composable () -> Unit) {
    val colors = ParlorTheme.colors
    val amber = if (colors.isLight) LightCozyNoirPalette else CozyNoirPalette
    val tableColors = colors.copy(
        accentEmber = amber.accentEmber,
        accentEmberGlow = amber.accentEmberGlow,
        accentEmberDeep = amber.accentEmberDeep,
        borderGlow = amber.borderGlow,
        borderAccent = amber.borderAccent,
    )
    ParlorTheme(
        themeMode = ParlorTheme.themeMode,
        darkPalette = tableColors,
        lightPalette = tableColors,
        typography = ParlorTheme.typography,
        spacing = ParlorTheme.spacing,
        elevation = ParlorTheme.elevation,
        radii = ParlorTheme.radii,
        blur = ParlorTheme.blur,
        borders = ParlorTheme.borders,
        iconSize = ParlorTheme.iconSize,
        motion = ParlorTheme.motion,
        reducedMotion = ParlorTheme.reducedMotion,
        content = content,
    )
}

@Composable
internal fun MafiaTable(
    modifier: Modifier = Modifier,
    actions: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    MafiaTableTheme {
        HeroBackdrop(modifier = modifier) {
            // Let large-text actions share the scroll viewport instead of squeezing the body.
            val pinActions = LocalDensity.current.fontScale < LARGE_TABLE_TEXT_SCALE
            val body: @Composable () -> Unit = {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .parlorSafeContentPadding(ParlorTheme.spacing.l),
                    verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
                ) {
                    content()
                    if (!pinActions) actions?.invoke()
                }
            }
            if (pinActions && actions != null) {
                StickyActionLayout(content = { body() }, actions = actions)
            } else {
                body()
            }
        }
    }
}

private const val LARGE_TABLE_TEXT_SCALE = 1.3f
