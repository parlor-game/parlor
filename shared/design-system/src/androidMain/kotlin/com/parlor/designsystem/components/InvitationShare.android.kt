package com.parlor.designsystem.components

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberInvitationSharer(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { invite ->
            val send = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, invite)
            context.startActivity(Intent.createChooser(send, null))
        }
    }
}
