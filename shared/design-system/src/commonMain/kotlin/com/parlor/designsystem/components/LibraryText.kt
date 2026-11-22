package com.parlor.designsystem.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

internal val LocalLibraryWidth = staticCompositionLocalOf { Dp.Infinity }

/** Fit the preferred number of lines on a small phone. At the type floor the complete text wraps. */
@Composable
fun LibraryText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Unspecified,
    preferredLines: Int = 1,
) {
    val width = LocalLibraryWidth.current
    val size = style.fontSize.value
    val floor = when {
        size >= 22f -> 18f
        size >= 17f -> 15f
        size >= 14f -> 14f
        else -> size
    }
    val fit = if (width < 440.dp && size > floor) {
        val fraction = ((width.value - 320f) / 120f).coerceIn(0f, 1f)
        // A small phone starts halfway down the display ramp, even for a short title.
        val ceiling = (floor + (size - floor) * (0.5f + fraction * 0.5f)).roundToInt()
        LibraryTypeFit(floor.roundToInt(), ceiling, preferredLines)
    } else null
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(
            color = color,
            textAlign = textAlign,
            lineHeight = (style.lineHeight.value / size).em,
        ),
        autoSize = fit,
    )
}

private data class LibraryTypeFit(val minimum: Int, val maximum: Int, val preferredLines: Int) : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        for (size in maximum downTo minimum + 1) {
            val layout = performLayout(constraints, text, size.sp)
            if (!layout.hasVisualOverflow && layout.lineCount <= preferredLines) return size.sp
        }
        return minimum.sp
    }
}
