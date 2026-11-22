package com.parlor.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.theme.ParlorTheme
import org.jetbrains.compose.resources.stringResource
import parlor.shared.design_system.generated.resources.Res
import parlor.shared.design_system.generated.resources.room_code_copy
import parlor.shared.design_system.generated.resources.room_code_copied

/** A Latin room identifier, readable as one code in either UI direction at full text scaling. */
@Composable
fun RoomCodeText(code: String, modifier: Modifier = Modifier) {
    Text(
        text = code,
        modifier = modifier.fillMaxWidth(),
        style = ParlorTheme.typography.timerMedium.copy(textDirection = TextDirection.Ltr),
        color = ParlorTheme.colors.accentEmber,
        textAlign = TextAlign.Center,
    )
}

/** Copies the host's displayed invitation code to the platform text clipboard. */
@Composable
fun RoomCodeWithCopy(code: String, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth < 270.dp || LocalDensity.current.fontScale >= 1.5f) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RoomCodeText(code)
                CopyRoomCodeButton(code)
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
            ) {
                RoomCodeText(code, Modifier.weight(1f))
                CopyRoomCodeButton(code)
            }
        }
    }
}

@Suppress("DEPRECATION") // Compose 1.10 uses platform-specific ClipEntry constructors.
@Composable
private fun CopyRoomCodeButton(code: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(code) { mutableStateOf(false) }
    TextButton(onClick = {
        clipboard.setText(AnnotatedString(code))
        copied = true
    }) {
        Text(
            text = stringResource(if (copied) Res.string.room_code_copied else Res.string.room_code_copy),
            style = ParlorTheme.typography.labelMedium,
            color = ParlorTheme.colors.accentEmber,
        )
    }
}
