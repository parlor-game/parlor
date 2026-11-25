package com.parlor.app.shell.game.multiplayer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import com.parlor.designsystem.components.AdmissionWaiting
import com.parlor.networking.transport.ApprovalWait
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.parlor.app.resources.Res
import com.parlor.app.resources.room_game_error_body
import com.parlor.app.resources.room_game_approval_expired
import com.parlor.app.resources.room_game_stale_room
import com.parlor.app.resources.room_game_wrong_game
import com.parlor.app.resources.room_game_incompatible
import com.parlor.app.resources.room_game_leave
import com.parlor.app.resources.room_game_new_room
import com.parlor.app.resources.room_game_retry
import com.parlor.app.resources.room_game_settings
import com.parlor.designsystem.components.LibrarySurface
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorButtonVariant
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.components.libraryFocusOutline
import com.parlor.designsystem.components.parlorSafeContentPadding
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.networking.room.NetError
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun RoomGameStatus(
    title: String,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
    body: String? = null,
    onRetry: (() -> Unit)? = null,
    onNewRoom: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    enabled: Boolean = true,
    approval: ApprovalWait? = null,
) {
    LibrarySurface(modifier.fillMaxSize(), litHeader = false) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                    .parlorSafeContentPadding(ParlorTheme.spacing.m),
                verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
            ) {
                ParlorCard(contentPadding = ParlorTheme.spacing.m) {
                    Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s)) {
                        Text(
                            text = title,
                            color = ParlorTheme.colors.textPrimary,
                            style = ParlorTheme.typography.headingLarge,
                            modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite },
                        )
                        approval?.let { AdmissionWaiting(it.lobby.hostDisplayName, it.deadline) }
                        body?.let {
                            Text(it, color = ParlorTheme.colors.textSecondary, style = ParlorTheme.typography.bodyMedium)
                        }
                    }
                }
                // Keep network recovery/leave controls away from the entry screen's bottom submit target.
                onRetry?.let { RoomButton(stringResource(Res.string.room_game_retry), it, enabled) }
                onNewRoom?.let { RoomButton(stringResource(Res.string.room_game_new_room), it, enabled) }
                onSettings?.let { RoomButton(stringResource(Res.string.room_game_settings), it, enabled) }
                RoomButton(stringResource(Res.string.room_game_leave), onLeave, enabled, secondary = true)
            }
        }
    }
}

@Composable
internal fun RoomButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    secondary: Boolean = false,
) {
    ParlorButton(
        label = label, contentDescription = label, onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().libraryFocusOutline(RoundedCornerShape(ParlorTheme.radii.subtle)),
        variant = if (secondary) ParlorButtonVariant.Secondary else ParlorButtonVariant.Primary,
        illuminated = !secondary,
    )
}

@Composable
internal fun roomGameError(error: NetError): String = stringResource(
    when (error) {
        NetError.ApprovalExpired -> Res.string.room_game_approval_expired
        NetError.StaleRoom -> Res.string.room_game_stale_room
        NetError.WrongGame -> Res.string.room_game_wrong_game
        NetError.IncompatibleProtocol -> Res.string.room_game_incompatible
        else -> Res.string.room_game_error_body
    },
)
