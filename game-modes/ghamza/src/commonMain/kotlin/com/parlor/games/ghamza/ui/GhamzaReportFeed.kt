package com.parlor.games.ghamza.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.localization.asBidiArgument
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.ghamza.domain.GhamzaState
import com.parlor.games.ghamza.resources.Res
import com.parlor.games.ghamza.resources.gh_report
import org.jetbrains.compose.resources.stringResource

/** Only the public report ledger animates; outgoing private roles never remain in composition. */
@Composable
internal fun GhamzaReportFeed(state: GhamzaState) {
    val colors = ParlorTheme.colors
    val duration = if (ParlorTheme.reducedMotion) 0 else 200
    AnimatedContent(
        targetState = state.public.recentReports.takeLast(3),
        transitionSpec = { fadeIn(tween(duration)) togetherWith fadeOut(tween(duration)) },
    ) { reports ->
        if (reports.isNotEmpty()) {
            val shape = RoundedCornerShape(ParlorTheme.radii.card)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(colors.surfaceHero)
                    .border(ParlorTheme.borders.hairline, colors.borderAccent.copy(alpha = 0.5f), shape)
                    .padding(ParlorTheme.spacing.m)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
            ) {
                reports.asReversed().forEach { report ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(ParlorTheme.iconSize.xxs)
                                .clip(RoundedCornerShape(ParlorTheme.radii.pill))
                                .background(colors.accentEmber),
                        )
                        GhamzaBody(
                            text = stringResource(
                                Res.string.gh_report,
                                state.players.first { it.id == report.player }.displayName.asBidiArgument(),
                                report.attempt,
                            ),
                            emphasized = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun GhamzaAttemptMarks(remaining: Int, total: Int) {
    val colors = ParlorTheme.colors
    Canvas(Modifier.size((total * 18).dp, 28.dp).clearAndSetSemantics {}) {
        repeat(total) { index ->
            val location = Offset((index * 18 + 8).dp.toPx(), center.y)
            if (index < remaining) {
                drawCircle(colors.accentEmber, 5.dp.toPx(), location)
            } else {
                drawCircle(colors.borderElevated, 5.dp.toPx(), location, style = Stroke(1.5.dp.toPx()))
            }
        }
    }
}
