package com.parlor.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import com.parlor.designsystem.localization.asBidiArgument
import com.parlor.designsystem.theme.ParlorTheme
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import parlor.shared.design_system.generated.resources.Res
import parlor.shared.design_system.generated.resources.admission_countdown
import parlor.shared.design_system.generated.resources.admission_waiting_for_host
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

/** Shown only after the transport authenticates the host and validates the room scope. */
@Composable
fun AdmissionWaiting(hostName: String, deadline: TimeMark) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s)) {
        Text(
            text = stringResource(Res.string.admission_waiting_for_host, hostName.asBidiArgument()),
            color = ParlorTheme.colors.textPrimary,
            style = ParlorTheme.typography.headingLarge,
            textAlign = TextAlign.Center,
        )
        AdmissionCountdown(deadline)
    }
}
