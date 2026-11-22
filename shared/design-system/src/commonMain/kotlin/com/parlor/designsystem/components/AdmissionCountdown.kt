package com.parlor.designsystem.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.parlor.designsystem.theme.ParlorTheme
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import parlor.shared.design_system.generated.resources.Res
import parlor.shared.design_system.generated.resources.admission_countdown
import kotlin.time.TimeMark

/** Host deadline or a locally measured peer estimate. Only the host can accept. */
@Composable
fun AdmissionCountdown(deadline: TimeMark?) {
    if (deadline == null) return
    val seconds by produceState(deadline.secondsRemaining(), deadline) {
        value = deadline.secondsRemaining()
        while (value > 0) {
            delay(COUNTDOWN_REFRESH_MS)
            value = deadline.secondsRemaining()
        }
    }
    Text(
        text = stringResource(Res.string.admission_countdown, seconds),
        color = ParlorTheme.colors.textSecondary,
        style = ParlorTheme.typography.bodySmall,
    )
}

private fun TimeMark.secondsRemaining(): Int =
    ((-elapsedNow().inWholeMilliseconds).coerceAtLeast(0L) + 999L).div(1_000L).toInt()

private const val COUNTDOWN_REFRESH_MS = 200L
