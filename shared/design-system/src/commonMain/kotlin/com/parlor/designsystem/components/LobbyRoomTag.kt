package com.parlor.designsystem.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.parlor.designsystem.localization.asBidiArgument
import com.parlor.designsystem.theme.ParlorTheme
import org.jetbrains.compose.resources.stringResource
import parlor.shared.design_system.generated.resources.Res
import parlor.shared.design_system.generated.resources.lobby_room_tag

/** Public invitation label used by the host and by the nearby-room picker. */
@Composable
fun LobbyRoomTag(tag: String?) {
    if (tag != null) {
        Text(
            text = stringResource(Res.string.lobby_room_tag, tag.asBidiArgument()),
            color = ParlorTheme.colors.textSecondary,
            style = ParlorTheme.typography.bodySmall,
        )
    }
}
