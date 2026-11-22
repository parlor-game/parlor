package com.parlor.app.shell.multiplayer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.parlor.app.resources.Res
import com.parlor.app.resources.join_cancel_description
import com.parlor.app.resources.join_code_field
import com.parlor.app.resources.join_code_help
import com.parlor.app.resources.join_confirm
import com.parlor.app.resources.join_confirm_description
import com.parlor.app.resources.join_steps_approval
import com.parlor.app.resources.join_steps_network
import com.parlor.app.resources.join_title
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorTextField
import com.parlor.designsystem.components.ScreenHeader
import com.parlor.designsystem.components.StickyActionLayout
import com.parlor.designsystem.components.parlorImePadding
import com.parlor.designsystem.components.parlorSafeContentPadding
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.networking.room.RoomInputPolicy
import org.jetbrains.compose.resources.stringResource

private val JoinFormMaxWidth = 560.dp

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
) {
    var code by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        StickyActionLayout(
            modifier = Modifier.fillMaxSize().parlorImePadding(),
            content = {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .widthIn(max = JoinFormMaxWidth)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .parlorSafeContentPadding(ParlorTheme.spacing.m),
                    verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
                ) {
                    ScreenHeader(
                        title = stringResource(Res.string.join_title),
                        subtitle = stringResource(Res.string.join_steps_network),
                        onBack = onCancel,
                        backContentDescription = stringResource(Res.string.join_cancel_description),
                    )

                    ParlorTextField(
                        value = code,
                        onValueChange = { input -> code = RoomInputPolicy.normalizeRoomCode(input) },
                        label = stringResource(Res.string.join_code_field),
                        capitalization = KeyboardCapitalization.Characters,
                        // Room codes are opaque ASCII identifiers, not words.
                        // Do not let a language keyboard correct a valid code.
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
                        text = stringResource(Res.string.join_code_help),
                        style = ParlorTheme.typography.bodySmall,
                        color = ParlorTheme.colors.textSecondary,
                    )
                    Text(
                        text = stringResource(Res.string.join_steps_approval),
                        style = ParlorTheme.typography.bodyMedium,
                        color = ParlorTheme.colors.textSecondary,
                    )
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
                        modifier = Modifier.widthIn(max = JoinFormMaxWidth).fillMaxWidth(),
                        enabled = RoomInputPolicy.isValidRoomCode(code),
                    )
                }
            },
        )
    }
}
