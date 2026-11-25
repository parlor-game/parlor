package com.parlor.games.lastlight.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.designsystem.tokens.ParlorColors
import com.parlor.games.lastlight.domain.rules.LastLightRules
import com.parlor.games.lastlight.resources.Res
import com.parlor.games.lastlight.resources.game_fuse_burnout
import com.parlor.games.lastlight.resources.game_fuse_explainer_title
import com.parlor.games.lastlight.resources.game_fuse_hidden_point
import com.parlor.games.lastlight.resources.game_fuse_safe_test
import com.parlor.games.lastlight.resources.game_fuse_summary
import com.parlor.games.lastlight.resources.game_fuse_tests
import com.parlor.games.lastlight.resources.game_fuse_untested
import com.parlor.games.lastlight.ui.theme.LastLightColors
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal enum class FuseChamberState { Untested, Safe, Burnout }

private data class FusePalette(
    val body: Color,
    val rimLight: Color,
    val rimMid: Color,
    val rimDark: Color,
    val safe: Color,
    val burnout: Color,
    val untested: Color,
    val untestedBorder: Color,
    val symbolOnChamber: Color,
)

private fun ParlorColors.toFusePalette() = FusePalette(
    body = surfaceInset,
    rimLight = borderElevated,
    rimMid = surfaceHigher,
    rimDark = borderSubtle,
    safe = semanticSuccess,
    burnout = semanticDanger,
    untested = surfaceCanvas,
    untestedBorder = textSecondary,
    symbolOnChamber = textOnAccent,
)

/** A history of public tests, never a prediction of the hidden burnout chamber. */
internal fun fuseChambers(attempts: Int, burnedOut: Boolean): List<FuseChamberState> =
    List(LastLightRules.FUSE_LIGHTS) { index ->
        when {
            burnedOut && index == attempts - 1 -> FuseChamberState.Burnout
            index < attempts -> FuseChamberState.Safe
            else -> FuseChamberState.Untested
        }
    }

/** Front-facing revolver cylinder; untested chambers are deliberately indistinguishable. */
@Composable
internal fun FuseCylinder(attempts: Int, burnedOut: Boolean, modifier: Modifier = Modifier) {
    val tests = stringResource(Res.string.game_fuse_tests, attempts)
    val state = stringResource(
        when {
            burnedOut -> Res.string.game_fuse_burnout
            attempts > 0 -> Res.string.game_fuse_safe_test
            else -> Res.string.game_fuse_untested
        },
    )
    val description = stringResource(Res.string.game_fuse_summary, tests, state)
    val chambers = remember(attempts, burnedOut) { fuseChambers(attempts, burnedOut) }
    val palette = ParlorTheme.colors.toFusePalette()
    Canvas(modifier.clearAndSetSemantics { contentDescription = description }) {
        val radius = size.minDimension / 2f
        val edge = if (burnedOut) palette.burnout else palette.rimLight
        drawCircle(palette.body, radius)
        drawCircle(
            Brush.linearGradient(listOf(palette.rimLight, palette.rimMid, palette.rimDark)),
            radius * 0.9f,
        )
        drawCircle(edge, radius * 0.9f, style = Stroke(radius * 0.045f))
        drawCircle(palette.body, radius * 0.76f, style = Stroke(radius * 0.04f))
        chambers.forEachIndexed { index, chamber ->
            val angle = -PI / 2 + index * PI / 3
            val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
            // Machined edge notches make this a cylinder rather than six floating dots.
            drawLine(
                palette.body,
                center + direction * (radius * 0.85f),
                center + direction * radius,
                strokeWidth = radius * 0.14f,
                cap = StrokeCap.Round,
            )
            drawFuseChamber(chamber, center + direction * (radius * 0.53f), radius * 0.2f, palette)
        }
        drawCircle(palette.body, radius * 0.17f)
        drawCircle(palette.rimLight, radius * 0.17f, style = Stroke(radius * 0.04f))
        drawLine(
            palette.rimLight,
            center - Offset(radius * 0.08f, 0f),
            center + Offset(radius * 0.08f, 0f),
            strokeWidth = radius * 0.045f,
        )
    }
}

private fun DrawScope.drawFuseChamber(
    state: FuseChamberState,
    position: Offset,
    radius: Float,
    palette: FusePalette,
) {
    val color = when (state) {
        FuseChamberState.Untested -> palette.untested
        FuseChamberState.Safe -> palette.safe
        FuseChamberState.Burnout -> palette.burnout
    }
    drawCircle(color, radius, position)
    drawCircle(
        if (state == FuseChamberState.Untested) palette.untestedBorder else color,
        radius,
        position,
        style = Stroke(radius * 0.14f),
    )
    val stroke = radius * 0.23f
    when (state) {
        FuseChamberState.Untested -> Unit
        FuseChamberState.Safe -> {
            drawLine(
                palette.symbolOnChamber,
                position + Offset(-0.5f, 0f) * radius,
                position + Offset(-0.1f, 0.4f) * radius,
                stroke,
                StrokeCap.Round,
            )
            drawLine(
                palette.symbolOnChamber,
                position + Offset(-0.1f, 0.4f) * radius,
                position + Offset(0.5f, -0.4f) * radius,
                stroke,
                StrokeCap.Round,
            )
        }
        FuseChamberState.Burnout -> {
            drawLine(
                palette.symbolOnChamber,
                position + Offset(-0.4f, -0.4f) * radius,
                position + Offset(0.4f, 0.4f) * radius,
                stroke,
                StrokeCap.Round,
            )
            drawLine(
                palette.symbolOnChamber,
                position + Offset(-0.4f, 0.4f) * radius,
                position + Offset(0.4f, -0.4f) * radius,
                stroke,
                StrokeCap.Round,
            )
        }
    }
}

@Composable
internal fun FuseLegend(modifier: Modifier = Modifier) {
    val palette = ParlorTheme.colors.toFusePalette()
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FuseChamberState.entries.forEach { state ->
            val label = stringResource(
                when (state) {
                    FuseChamberState.Untested -> Res.string.game_fuse_untested
                    FuseChamberState.Safe -> Res.string.game_fuse_safe_test
                    FuseChamberState.Burnout -> Res.string.game_fuse_burnout
                },
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Canvas(Modifier.size(14.dp).clearAndSetSemantics { }) {
                    drawFuseChamber(state, center, size.minDimension * 0.44f, palette)
                }
                Text(label, style = MaterialTheme.typography.bodySmall, color = LastLightColors.Muted)
            }
        }
    }
}

@Composable
internal fun FuseExplanation(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(Res.string.game_fuse_explainer_title),
            style = MaterialTheme.typography.titleSmall,
            color = LastLightColors.Paper,
        )
        Text(
            stringResource(Res.string.game_fuse_hidden_point),
            style = MaterialTheme.typography.bodySmall,
            color = LastLightColors.Muted,
        )
    }
}
