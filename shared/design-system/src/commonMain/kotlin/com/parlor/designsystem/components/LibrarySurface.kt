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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.parlor.designsystem.theme.LocalParlorColors
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.designsystem.tokens.isLight

/** Paper, ink and a single action color for browsing the boxed games. */
@Composable
fun LibrarySurface(modifier: Modifier, content: @Composable BoxWithConstraintsScope.() -> Unit) {
    val base = ParlorTheme.colors
    val paper = base.isLight
    val green = if (paper) Color(0xFF174D43) else Color(0xFFB9DECA)
    val colors = base.copy(
        surfaceCanvas = if (paper) Color(0xFFF6F1E8) else Color(0xFF172321),
        surfaceElevated = if (paper) Color(0xFFFFFCF5) else Color(0xFF22322F),
        surfaceHigher = if (paper) Color(0xFFE8E8DC) else Color(0xFF32433F),
        textPrimary = if (paper) Color(0xFF202B29) else Color(0xFFF6F1E8),
        textSecondary = if (paper) Color(0xFF4F605B) else Color(0xFFC0CFC8),
        textTertiary = if (paper) Color(0xFF56645F) else Color(0xFFB0BCB6),
        accentEmber = green,
        borderAccent = green,
        borderSubtle = if (paper) Color(0xFFD4D3C5) else Color(0xFF4A5B56),
    )
    CompositionLocalProvider(LocalParlorColors provides colors) {
        BoxWithConstraints(modifier.background(colors.surfaceCanvas), contentAlignment = Alignment.TopCenter) {
            CompositionLocalProvider(LocalLibraryWidth provides maxWidth) { content() }
        }
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
