package com.parlor.app.shell.party

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDirection
import com.parlor.app.resources.Res
import com.parlor.app.resources.party_cancel
import com.parlor.app.resources.party_code
import com.parlor.app.resources.party_create
import com.parlor.app.resources.party_entry_detail
import com.parlor.app.resources.party_invitation
import com.parlor.app.resources.party_join
import com.parlor.app.resources.party_joining
import com.parlor.app.resources.party_approval_pending
import com.parlor.app.resources.party_name
import com.parlor.app.resources.party_open
import com.parlor.app.resources.party_resume
import com.parlor.app.resources.party_restore_host
import com.parlor.app.resources.party_room_name
import com.parlor.app.resources.party_settings_game
import com.parlor.app.resources.party_network_settings
import com.parlor.app.permissions.rememberP2pPermissionGate
import com.parlor.app.shell.game.multiplayer.roomGameError
import com.parlor.app.shell.profile.LocalPreferredDisplayName
import com.parlor.core.result.Result
import com.parlor.core.ids.PartyId
import com.parlor.designsystem.components.AdmissionCountdown
import com.parlor.designsystem.components.ParlorButtonVariant
import com.parlor.designsystem.components.ParlorTextField
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.designsystem.localization.asBidiArgument
import com.parlor.networking.invitation.PartyJoinInvitation
import com.parlor.networking.room.NetError
import com.parlor.networking.room.RoomInputPolicy
import com.parlor.networking.transport.ApprovalWait
import com.parlor.networking.transport.ResumablePartySessionInfo
import com.parlor.networking.transport.RoomTransport
import com.parlor.networking.transport.needsRecoveryGuidance
import com.parlor.session.lobby.PartyGame
import com.parlor.session.lobby.ProcessPartySession
import com.parlor.session.lobby.ProcessPartySessionOwner
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PartyEntry(
    owner: ProcessPartySessionOwner,
    transport: RoomTransport,
    titles: Map<PartyGame, String>,
    invitation: PartyJoinInvitation?,
    backRequest: Long,
    onExit: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val gate = rememberP2pPermissionGate(transport.localNetworkAccess)
    var job by remember { mutableStateOf<Job?>(null) }
    var waiting by remember { mutableStateOf(false) }
    var showJoinFeedback by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<NetError?>(null) }
    var approval by remember { mutableStateOf<ApprovalWait?>(null) }
    var saved by remember { mutableStateOf<ResumablePartySessionInfo?>(null) }
    var hosted by remember { mutableStateOf<PartyId?>(null) }
    val preferredName = LocalPreferredDisplayName.current
    var name by remember { mutableStateOf(preferredName.orEmpty()) }
    var roomName by remember { mutableStateOf("") }
    var code by remember(invitation) { mutableStateOf(invitation?.code.orEmpty()) }
    val first = titles.keys.firstOrNull()
    var selected by remember(first) { mutableStateOf(first) }
    val catalog = titles.keys.toSet()
    val displayName = RoomInputPolicy.normalizeDisplayName(name)
    val validName = RoomInputPolicy.isValidDisplayName(displayName)
    val leave = { job?.cancel(); onExit() }
    val open: (Boolean, suspend () -> Result<ProcessPartySession, NetError>) -> Unit = { joining, operation ->
        if (!waiting) {
            showJoinFeedback = joining
            waiting = true
            problem = null
            job = scope.launch {
                try {
                    val result = operation()
                    if (result is Result.Failure) problem = result.error
                } finally {
                    waiting = false
                    approval = null
                }
            }
        }
    }
    LaunchedEffect(transport) {
        saved = (transport.resumablePartySession() as? Result.Success)?.data
        hosted = (transport.resumableHostedPartyId() as? Result.Success)?.data
    }
    LaunchedEffect(backRequest) { if (backRequest > 0) leave() }

    val feedback: @Composable () -> Unit = {
        if (waiting) PartyJoinProgress(approval)
        problem?.let { PartyError(roomGameError(it)) }
        if (problem != null && gate.canOpenNetworkSettings && transport.localNetworkAccess.value.needsRecoveryGuidance) {
            PartyButton(Res.string.party_network_settings, variant = ParlorButtonVariant.Secondary, onClick = gate::openAppSettings)
        }
    }
    PartyScaffold {
        PartyHeading(stringResource(if (invitation == null) Res.string.party_open else Res.string.party_invitation))
        PartyDetail(stringResource(Res.string.party_entry_detail))
        hosted?.takeIf { invitation == null }?.let { pending ->
            PartyButton(Res.string.party_restore_host, enabled = !waiting) { open(false) { owner.restoreHostedParty(pending, catalog) } }
        }
        saved?.takeIf { invitation == null }?.let { pending ->
            PartyButton(Res.string.party_resume, enabled = !waiting) { open(false) { owner.resume(pending, catalog) } }
        }
        ParlorTextField(name, { if (!waiting) name = RoomInputPolicy.sanitizeDisplayNameInput(it) },
            stringResource(Res.string.party_name))
        ParlorTextField(code, { if (!waiting && invitation == null) code = RoomInputPolicy.normalizeRoomCode(it) },
            stringResource(Res.string.party_code), capitalization = KeyboardCapitalization.Characters,
            textStyle = ParlorTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
            autoCorrectEnabled = false)
        PartyButton(Res.string.party_join, enabled = !waiting && validName && RoomInputPolicy.isValidRoomCode(code)) {
            open(true) { owner.join(displayName, code, catalog, invitation, onApprovalPending = { approval = it }) }
        }
        if (showJoinFeedback) feedback()
        if (invitation == null) {
            ParlorTextField(roomName, { if (!waiting) roomName = RoomInputPolicy.sanitizeDisplayNameInput(it) },
                stringResource(Res.string.party_room_name))
            PartyHeading(stringResource(Res.string.party_settings_game))
            PartyGameOptions(titles, selected, !waiting) { selected = it }
            PartyButton(Res.string.party_create, enabled = !waiting && validName && selected != null) {
                val initial = checkNotNull(selected)
                val label = RoomInputPolicy.normalizeDisplayName(roomName).ifBlank { null }
                open(false) { owner.host(displayName, catalog, initial, label) }
            }
        }
        if (!showJoinFeedback) feedback()
        PartyButton(Res.string.party_cancel, variant = ParlorButtonVariant.Secondary, modifier = Modifier.fillMaxWidth(), onClick = leave)
    }
}


@Composable
private fun PartyJoinProgress(approval: ApprovalWait?) {
    if (approval == null) {
        PartyDetail(stringResource(Res.string.party_joining))
    } else {
        PartyDetail(stringResource(Res.string.party_approval_pending, approval.lobby.hostDisplayName.asBidiArgument()))
        AdmissionCountdown(approval.deadline)
    }
}
