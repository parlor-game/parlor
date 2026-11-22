package com.parlor.app.shell.multiplayer

import com.parlor.app.shell.profile.LocalPreferredDisplayName
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.parlor.app.resources.Res
import com.parlor.app.resources.name_back_description
import com.parlor.app.resources.name_confirm_host
import com.parlor.app.resources.name_confirm_host_description
import com.parlor.app.resources.name_confirm_peer
import com.parlor.app.resources.name_confirm_peer_description
import com.parlor.app.resources.name_eyebrow_host
import com.parlor.app.resources.name_eyebrow_peer
import com.parlor.app.resources.name_field
import com.parlor.app.resources.name_character_count
import com.parlor.app.resources.name_seat_preview
import com.parlor.app.resources.name_seat_label
import com.parlor.app.resources.name_help
import com.parlor.app.resources.name_title_host
import com.parlor.app.resources.name_title_peer
import com.parlor.designsystem.components.LibrarySurface
import com.parlor.designsystem.components.LibraryText
import com.parlor.app.shell.profile.SeatIdentityCard
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorTextField
import com.parlor.designsystem.components.ScreenHeader
import com.parlor.designsystem.components.StickyActionLayout
import com.parlor.designsystem.components.parlorImePadding
import com.parlor.designsystem.components.parlorSafeContentPadding
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.networking.room.RoomInputPolicy
import org.jetbrains.compose.resources.stringResource

internal val RoomEntryMaxWidth = 560.dp

/**
 * Asks the player to enter the name shown to the room. Reused by both
 * Host and Join flows — the [isHost] flag chooses the right eyebrow and
 * confirm label. Names must be explicitly provided; values trim whitespace
 * before submission.
 *
 * Mobile-first: scrollable container, full-width input, full-width primary
 * button, and a secondary back row so the layout fits comfortably on a
 * 360 dp wide phone.
 */
internal fun normalizedMultiplayerDisplayName(input: String): String =
    RoomInputPolicy.normalizeDisplayName(input)

@Composable
fun NameInputScreen(
    isHost: Boolean,
    initial: String,
    onConfirm: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    gameTitle: String? = null,
) {
    val preferredName = LocalPreferredDisplayName.current
    var name by remember { mutableStateOf(initial.ifBlank { preferredName.orEmpty() }) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val sanitized = normalizedMultiplayerDisplayName(name)

    LibrarySurface(modifier.fillMaxSize(), litHeader = false) {
        StickyActionLayout(
            modifier = Modifier.fillMaxSize().parlorImePadding(),
            content = {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .widthIn(max = RoomEntryMaxWidth)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .parlorSafeContentPadding(ParlorTheme.spacing.m),
                    verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
                ) {
                    ScreenHeader(
                        title = if (isHost) stringResource(Res.string.name_title_host)
                        else stringResource(Res.string.name_title_peer),
                        eyebrow = gameTitle ?: stringResource(
                            if (isHost) Res.string.name_eyebrow_host else Res.string.name_eyebrow_peer,
                        ),
                        subtitle = stringResource(Res.string.name_help),
                        onBack = onBack,
                        compact = true,
                        backContentDescription = stringResource(Res.string.name_back_description),
                    )

                    ParlorTextField(
                        value = name,
                        onValueChange = { input ->
                            name = RoomInputPolicy.sanitizeDisplayNameInput(input)
                        },
                        label = stringResource(Res.string.name_field),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            },
                        ),
                    )
                    NameSeatPreview(sanitized, name.length)
                }
            },
            actions = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ParlorButton(
                        label = if (isHost) stringResource(Res.string.name_confirm_host)
                        else stringResource(Res.string.name_confirm_peer),
                        contentDescription = if (isHost) {
                            stringResource(Res.string.name_confirm_host_description)
                        } else {
                            stringResource(Res.string.name_confirm_peer_description)
                        },
                        onClick = {
                            // A retained callback must validate the current draft at activation.
                            val currentName = normalizedMultiplayerDisplayName(name)
                            if (RoomInputPolicy.isValidDisplayName(currentName)) onConfirm(currentName)
                        },
                        modifier = Modifier.widthIn(max = RoomEntryMaxWidth).fillMaxWidth(),
                        enabled = RoomInputPolicy.isValidDisplayName(sanitized),
                        illuminated = true,
                    )
                }
            },
        )
    }
}


@Composable
private fun NameSeatPreview(displayName: String, characterCount: Int) {
    val colors = ParlorTheme.colors
    LibraryText(
        text = stringResource(
            Res.string.name_character_count,
            characterCount,
            RoomInputPolicy.MAX_DISPLAY_NAME_LENGTH,
        ),
        style = ParlorTheme.typography.labelSmall,
        color = colors.textSecondary,
    )
    if (displayName.isNotEmpty()) {
        val label = stringResource(Res.string.name_seat_preview, displayName)
        SeatIdentityCard(
            name = displayName,
            caption = stringResource(Res.string.name_seat_label),
            modifier = Modifier.clearAndSetSemantics { contentDescription = label },
        )
    }
}
