package com.parlor.app.shell.multiplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.parlor.app.resources.Res
import com.parlor.app.resources.join_cancel_description
import com.parlor.app.resources.join_change_seat
import com.parlor.app.resources.join_seat_label
import com.parlor.app.resources.join_code_field
import com.parlor.app.resources.join_code_help
import com.parlor.app.resources.join_confirm
import com.parlor.app.resources.join_confirm_description
import com.parlor.app.resources.join_steps_approval
import com.parlor.app.resources.join_steps_network
import com.parlor.app.resources.join_title
import com.parlor.app.shell.profile.SeatIdentityCard
import com.parlor.designsystem.components.LibrarySurface
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorTextField
import com.parlor.designsystem.components.ScreenHeader
import com.parlor.designsystem.components.StickyActionLayout
import com.parlor.designsystem.components.parlorImePadding
import com.parlor.designsystem.components.parlorSafeContentPadding
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.networking.room.RoomInputPolicy
import org.jetbrains.compose.resources.stringResource

/**
 * Room-code entry shared by the shipping game-specific peer session flows.
 * [onConfirm] hands the validated code to the app shell, which starts the
 * authoritative join/resume and acknowledged-session-start workflow.
 */
@Composable
fun JoinPromptScreen(
    onConfirm: (code: String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    gameTitle: String? = null,
    seatName: String? = null,
    onChangeName: (() -> Unit)? = null,
) {
    var code by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

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
                        title = stringResource(Res.string.join_title),
                        eyebrow = gameTitle,
                        subtitle = stringResource(Res.string.join_steps_network),
                        onBack = onCancel,
                        compact = true,
                        backContentDescription = stringResource(Res.string.join_cancel_description),
                    )

                    if (seatName != null) {
                        SeatIdentityCard(seatName, stringResource(Res.string.join_seat_label)) {
                            if (onChangeName != null) {
                                TextButton(onClick = onChangeName) {
                                    Text(
                                        stringResource(Res.string.join_change_seat),
                                        color = ParlorTheme.colors.accentEmber,
                                    )
                                }
                            }
                        }
                    }
                    ParlorCard(contentPadding = ParlorTheme.spacing.m) {
                        Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s)) {
                            Text(
                                text = stringResource(Res.string.join_code_help),
                                style = ParlorTheme.typography.bodySmall,
                                color = ParlorTheme.colors.textSecondary,
                            )
                            if (LocalDensity.current.fontScale < CODE_SLOT_FONT_SCALE) RoomCodeSlots(code)
                            ParlorTextField(
                                value = code,
                                onValueChange = { input -> code = RoomInputPolicy.normalizeRoomCode(input) },
                                label = stringResource(Res.string.join_code_field),
                                capitalization = KeyboardCapitalization.Characters,
                                keyboardType = KeyboardType.Ascii,
                                autoCorrectEnabled = false,
                                textStyle = ParlorTheme.typography.timerMedium.copy(
                                    textAlign = TextAlign.Center,
                                    textDirection = TextDirection.Ltr,
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                    },
                                ),
                            )
                            Text(
                                text = stringResource(Res.string.join_steps_approval),
                                style = ParlorTheme.typography.bodySmall,
                                color = ParlorTheme.colors.textSecondary,
                            )
                        }
                    }
                }
            },
            actions = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ParlorButton(
                        label = stringResource(Res.string.join_confirm),
                        contentDescription = stringResource(Res.string.join_confirm_description),
                        onClick = {
                            // An enabled callback can outlive the draft it was rendered for.
                            val currentCode = code
                            if (RoomInputPolicy.isValidRoomCode(currentCode)) onConfirm(currentCode)
                        },
                        modifier = Modifier.widthIn(max = RoomEntryMaxWidth).fillMaxWidth(),
                        enabled = RoomInputPolicy.isValidRoomCode(code),
                        illuminated = true,
                    )
                }
            },
        )
    }
}


/** The editable field owns the entire code and its accessibility semantics. */
@Composable
private fun RoomCodeSlots(code: String) {
    val colors = ParlorTheme.colors
    val shape = RoundedCornerShape(12.dp)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(RoomInputPolicy.ROOM_CODE_LENGTH) { index ->
                if (index == RoomInputPolicy.ROOM_CODE_LENGTH / 2) {
                    Box(Modifier.size(3.dp).background(colors.textTertiary, RoundedCornerShape(50)))
                }
                Box(
                    modifier = Modifier.weight(1f).height(48.dp)
                        .background(colors.surfaceHigher, shape)
                        .border(1.dp, if (index == code.length) colors.accentEmber else colors.borderSubtle, shape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = code.getOrNull(index)?.toString().orEmpty(),
                        color = colors.textPrimary,
                        style = ParlorTheme.typography.timerMedium,
                    )
                }
            }
        }
    }
}

private const val CODE_SLOT_FONT_SCALE = 1.7f
