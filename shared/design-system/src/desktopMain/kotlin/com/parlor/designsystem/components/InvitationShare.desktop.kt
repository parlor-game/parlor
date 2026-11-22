package com.parlor.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
internal actual fun rememberInvitationSharer(): (String) -> Unit = remember {
    { invite -> Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(invite), null) }
}
