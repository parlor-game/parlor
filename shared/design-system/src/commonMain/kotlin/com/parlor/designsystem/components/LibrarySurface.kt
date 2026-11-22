package com.parlor.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.designsystem.theme.LocalParlorColors
import com.parlor.designsystem.tokens.neonShell

/** Shared catalog surface with a softly lit header and the normal shell palette. */
@Composable
fun LibrarySurface(modifier: Modifier, litHeader: Boolean = true, content: @Composable BoxWithConstraintsScope.() -> Unit) {
    val colors = ParlorTheme.colors.neonShell
    val background = Brush.verticalGradient(
        0f to if (litHeader) colors.surfaceHero else colors.surfaceCanvas,
        0.38f to colors.surfaceCanvas,
        1f to colors.surfaceCanvas,
    )
    BoxWithConstraints(modifier.background(background), contentAlignment = Alignment.TopCenter) {
        CompositionLocalProvider(
            LocalLibraryWidth provides maxWidth,
            LocalParlorColors provides colors,
        ) { content() }
    }
}

@Composable
fun Modifier.libraryFocusOutline(shape: Shape): Modifier {
    var focused by remember { mutableStateOf(false) }
    return onFocusChanged { focused = it.isFocused }.then(
        if (focused) {
            Modifier.border(ParlorTheme.borders.strong, ParlorTheme.colors.accentEmber, shape)
                .border(ParlorTheme.spacing.xs, ParlorTheme.colors.surfaceCanvas, shape)
        } else {
            Modifier
        },
    )
}
