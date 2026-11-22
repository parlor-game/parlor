package com.parlor.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.theme.ParlorTheme
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import parlor.shared.design_system.generated.resources.Res
import parlor.shared.design_system.generated.resources.lobby_invite_close
import parlor.shared.design_system.generated.resources.lobby_invite_help
import parlor.shared.design_system.generated.resources.lobby_invite_players
import parlor.shared.design_system.generated.resources.lobby_invite_qr
import parlor.shared.design_system.generated.resources.lobby_invite_share
import parlor.shared.design_system.generated.resources.lobby_invite_unavailable
import qrcode.raw.QRCodeProcessor
import kotlin.math.floor

@Composable
fun InviteToLobbyButton(createLink: suspend () -> String?) {
    val scope = rememberCoroutineScope()
    val label = stringResource(Res.string.lobby_invite_players)
    val unavailable = stringResource(Res.string.lobby_invite_unavailable)
    val share = rememberInvitationSharer()
    var opening by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf(false) }
    var link by remember { mutableStateOf<String?>(null) }

    ParlorButton(
        label = label,
        contentDescription = label,
        onClick = {
            if (!opening && !open) {
                opening = true
                scope.launch {
                    try {
                        link = createLink()
                        open = true
                    } finally {
                        opening = false
                    }
                }
            }
        },
        loading = opening,
        variant = ParlorButtonVariant.Secondary,
        modifier = Modifier.fillMaxWidth(),
    )
    if (open) {
        val invitation = link
        InviteDialog(
            invite = invitation,
            unavailable = unavailable,
            onClose = { link = null; open = false },
            onShare = { invitation?.let(share) },
        )
    }
}

@Composable
private fun InviteDialog(invite: String?, unavailable: String, onClose: () -> Unit, onShare: () -> Unit) {
    val closeLabel = stringResource(Res.string.lobby_invite_close)
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(Res.string.lobby_invite_players)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
            ) {
                if (invite == null) {
                    Text(unavailable)
                } else {
                    InvitationQr(invite)
                    Text(stringResource(Res.string.lobby_invite_help))
                }
            }
        },
        dismissButton = {
            ParlorButton(closeLabel, onClose, closeLabel, variant = ParlorButtonVariant.Ghost)
        },
        confirmButton = {
            if (invite != null) {
                val shareLabel = stringResource(Res.string.lobby_invite_share)
                ParlorButton(shareLabel, onShare, shareLabel)
            }
        },
    )
}

@Composable
private fun InvitationQr(invite: String) {
    val label = stringResource(Res.string.lobby_invite_qr)
    val data = remember(invite) { QRCodeProcessor(invite).encode() }
    Canvas(
        Modifier.sizeIn(maxWidth = 240.dp).fillMaxWidth().aspectRatio(1f)
            .semantics { contentDescription = label },
    ) {
        drawRect(Color.White)
        val pixelSize = floor(size.minDimension / (data.size + QR_QUIET_ZONE * 2))
        val start = floor((size.minDimension - pixelSize * data.size) / 2)
        val cell = Size(pixelSize, pixelSize)
        data.forEachIndexed { y, row ->
            row.forEachIndexed { x, module ->
                if (module.dark) {
                    drawRect(Color.Black, Offset(start + x * pixelSize, start + y * pixelSize), cell)
                }
            }
        }
    }
}

private const val QR_QUIET_ZONE = 4

@Composable
internal expect fun rememberInvitationSharer(): (String) -> Unit
