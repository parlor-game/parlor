package com.parlor.app.permissions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.parlor.app.resources.Res
import com.parlor.app.resources.permission_back
import com.parlor.app.resources.permission_back_description
import com.parlor.app.resources.permission_body
import com.parlor.app.resources.permission_continue
import com.parlor.app.resources.permission_continue_description
import com.parlor.app.resources.permission_denied_body
import com.parlor.app.resources.permission_denied_eyebrow
import com.parlor.app.resources.permission_eyebrow
import com.parlor.app.resources.permission_open_settings
import com.parlor.app.resources.permission_open_settings_description
import com.parlor.app.resources.permission_permanently_denied_body
import com.parlor.app.resources.permission_permanently_denied_eyebrow
import com.parlor.app.resources.permission_title
import com.parlor.app.resources.profile_saved_seat
import com.parlor.app.shell.profile.LocalPreferredDisplayName
import com.parlor.app.shell.profile.SeatIdentityCard
import com.parlor.designsystem.components.LibrarySurface
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorButtonVariant
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.components.ScreenHeader
import com.parlor.designsystem.components.StickyActionLayout
import com.parlor.designsystem.components.parlorSafeContentPadding
import com.parlor.designsystem.theme.ParlorTheme
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Sits in front of HostName / JoinName until the platform either needs no
 * runtime gate, has proven LAN operation, or the player elects to make the
 * first real iOS network attempt. A generic timeout is never presented as a
 * proven Local Network denial.
 */
@Composable
fun P2pPermissionRationaleScreen(
    gate: P2pPermissionGate,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    gameTitle: String? = null,
    roomAction: String? = null,
) {
    val status by gate.status.collectAsState()
    val scope = rememberCoroutineScope()

    // If the OS state flips to Granted (e.g. user came back from Settings),
    // hand off to the caller automatically. Keep the callback in an effect:
    // invoking it directly during composition can mutate the parent
    // navigation state while Compose is still applying the current frame.
    if (status.entersMultiplayerWithoutRationale) {
        LaunchedEffect(Unit) { onContinue() }
        return
    }

    val blocked = status == PermissionStatus.DeniedActionable || status == PermissionStatus.FailureUnclassified
    LibrarySurface(modifier.fillMaxSize(), litHeader = false) {
        StickyActionLayout(
            content = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Column(
                        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .parlorSafeContentPadding(ParlorTheme.spacing.m),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (roomAction != null) {
                            ScreenHeader(
                                title = roomAction,
                                eyebrow = gameTitle,
                                onBack = onBack,
                                backContentDescription = stringResource(Res.string.permission_back_description),
                                compact = true,
                            )
                            LocalPreferredDisplayName.current?.let { name ->
                                SeatIdentityCard(name, stringResource(Res.string.profile_saved_seat))
                            }
                        }
                        PermissionExplanation(status)
                    }
                }
            },
            actions = {
                Column(
                    modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
                ) {
                    if (blocked && gate.canOpenNetworkSettings) {
                        ParlorButton(
                            label = stringResource(Res.string.permission_open_settings),
                            contentDescription = stringResource(Res.string.permission_open_settings_description),
                            onClick = gate::openAppSettings,
                            modifier = Modifier.fillMaxWidth(),
                            illuminated = true,
                        )
                    }
                    val primaryContinue = !blocked || !gate.canOpenNetworkSettings
                    ParlorButton(
                        label = stringResource(Res.string.permission_continue),
                        contentDescription = stringResource(Res.string.permission_continue_description),
                        onClick = {
                            // On iOS, retry the real network operation to learn whether access works.
                            scope.launch {
                                val next = gate.request()
                                if (next.mayAttemptNetwork) onContinue()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        variant = if (primaryContinue) ParlorButtonVariant.Primary else ParlorButtonVariant.Secondary,
                        illuminated = primaryContinue,
                    )
                    ParlorButton(
                        label = stringResource(Res.string.permission_back),
                        contentDescription = stringResource(Res.string.permission_back_description),
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth(),
                        variant = ParlorButtonVariant.Secondary,
                    )
                }
            },
        )
    }
}

@Composable
private fun PermissionExplanation(status: PermissionStatus) {
    val eyebrow = when (status) {
        PermissionStatus.DeniedActionable -> Res.string.permission_permanently_denied_eyebrow
        PermissionStatus.FailureUnclassified -> Res.string.permission_denied_eyebrow
        else -> Res.string.permission_eyebrow
    }
    val explanation = when (status) {
        PermissionStatus.DeniedActionable -> Res.string.permission_permanently_denied_body
        PermissionStatus.FailureUnclassified -> Res.string.permission_denied_body
        else -> Res.string.permission_body
    }
    ParlorCard(modifier = Modifier.fillMaxWidth(), contentPadding = ParlorTheme.spacing.m) {
        Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s)) {
            EyebrowLabel(stringResource(eyebrow))
            Text(
                text = stringResource(Res.string.permission_title),
                style = ParlorTheme.typography.headingLarge,
                color = ParlorTheme.colors.textPrimary,
            )
            Text(
                text = stringResource(explanation),
                style = ParlorTheme.typography.bodySmall,
                color = ParlorTheme.colors.textSecondary,
            )
        }
    }
}
