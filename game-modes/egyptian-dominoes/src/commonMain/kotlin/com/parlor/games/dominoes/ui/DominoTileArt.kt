package com.parlor.games.dominoes.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.designsystem.tokens.ParlorColors

private data class DominoTilePalette(
    val shadow: Color,
    val bevel: Color,
    val ivoryTop: Color,
    val ivoryBottom: Color,
    val highlight: Color,
    val divider: Color,
    val brassPin: Color,
    val backFrame: Color,
    val backDiamond: Color,
    val pipRim: Color,
    val pipCore: Color,
    val pipSheen: Color,
)

private fun ParlorColors.toTilePalette() = DominoTilePalette(
    shadow = overlayScrim.copy(alpha = 0.28f),
    bevel = textNarration.copy(alpha = 0.72f),
    ivoryTop = surfacePaper,
    ivoryBottom = textNarration,
    highlight = surfacePaper.copy(alpha = 0.72f),
    divider = textOnPaperSecondary.copy(alpha = 0.45f),
    brassPin = accentEmber,
    backFrame = textOnPaperSecondary.copy(alpha = 0.65f),
    backDiamond = accentEmberDeep,
    pipRim = textOnPaperSecondary.copy(alpha = 0.45f),
    pipCore = textOnPaper,
    pipSheen = textOnPaperSecondary,
)

@Composable
internal fun DominoTileFace(
    left: Int,
    right: Int,
    modifier: Modifier,
    covered: Boolean = false,
    vertical: Boolean = false,
) {
    val palette = ParlorTheme.colors.toTilePalette()
    Canvas(modifier) { drawDominoTile(left, right, covered, palette, vertical) }
}

/** Vector-drawn ivory, recessed pips, a brass pin and a raised edge; all artwork is code-native. */
private fun DrawScope.drawDominoTile(
    left: Int,
    right: Int,
    covered: Boolean,
    palette: DominoTilePalette,
    vertical: Boolean = false,
) {
    val length = if (vertical) size.height else size.width
    val thickness = if (vertical) size.width else size.height
    rotate(if (vertical) 90f else 0f) {
        val start = center - Offset(length / 2, thickness / 2)
        val face = Size(length, thickness * .94f)
        val corner = CornerRadius(thickness * .13f)
        drawRoundRect(palette.shadow, start + Offset(thickness * .05f, thickness * .12f), face, corner)
        drawRoundRect(palette.bevel, start + Offset(0f, thickness * .07f), face, corner)
        drawRoundRect(
            Brush.linearGradient(
                listOf(palette.ivoryTop, palette.ivoryBottom),
                start,
                start + Offset(length, thickness),
            ),
            start,
            face,
            corner,
        )
        drawRoundRect(
            palette.highlight,
            start + Offset(thickness * .025f, thickness * .025f),
            Size(face.width - thickness * .05f, face.height - thickness * .05f),
            corner,
            style = Stroke(thickness * .025f),
        )
        if (covered) {
            drawTileBack(start, length, thickness, palette)
        } else {
            drawLine(
                palette.divider,
                Offset(center.x, start.y + thickness * .15f),
                Offset(center.x, start.y + thickness * .8f),
                thickness * .035f,
            )
            drawCircle(palette.brassPin, thickness * .048f, center - Offset(0f, thickness * .03f))
            drawPips(left, start + Offset(length * .25f, thickness * .47f), thickness, palette)
            drawPips(right, start + Offset(length * .75f, thickness * .47f), thickness, palette)
        }
    }
}

private fun DrawScope.drawTileBack(
    start: Offset,
    length: Float,
    thickness: Float,
    palette: DominoTilePalette,
) {
    val inset = thickness * .16f
    drawRoundRect(
        palette.backFrame,
        start + Offset(inset, inset),
        Size(length - inset * 2, thickness * .94f - inset * 2),
        CornerRadius(inset * .6f),
        style = Stroke(thickness * .025f),
    )
    val diamond = Path().apply {
        moveTo(center.x, center.y - thickness * .23f)
        lineTo(center.x + thickness * .24f, center.y)
        lineTo(center.x, center.y + thickness * .19f)
        lineTo(center.x - thickness * .24f, center.y)
        close()
    }
    drawPath(diamond, palette.backDiamond, style = Stroke(thickness * .035f))
}

private fun DrawScope.drawPips(number: Int, origin: Offset, unit: Float, palette: DominoTilePalette) {
    val points = buildList {
        if (number % 2 == 1) add(Offset.Zero)
        if (number >= 2) {
            add(Offset(-1f, -1f))
            add(Offset(1f, 1f))
        }
        if (number >= 4) {
            add(Offset(1f, -1f))
            add(Offset(-1f, 1f))
        }
        if (number == 6) {
            add(Offset(-1f, 0f))
            add(Offset(1f, 0f))
        }
    }
    points.forEach { point ->
        val position = origin + point * (unit * .24f)
        drawCircle(palette.pipRim, unit * .085f, position + Offset(0f, unit * .018f))
        drawCircle(palette.pipCore, unit * .075f, position)
        drawCircle(palette.pipSheen, unit * .045f, position - Offset(unit * .009f, unit * .008f))
    }
}
