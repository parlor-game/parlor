package com.parlor.app.shell.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.parlor.app.permissions.P2pPermissionGate
import com.parlor.app.permissions.P2pPermissionRationaleScreen
import com.parlor.app.permissions.entersMultiplayerWithoutRationale
import com.parlor.app.resources.Res
import com.parlor.app.resources.setup_host_title
import com.parlor.app.resources.setup_join_title
import org.jetbrains.compose.resources.stringResource

/** Shared permission step; game bindings decide where success routes next. */
@Composable
internal fun P2pPermissionRoute(
    gate: P2pPermissionGate,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    gameTitle: String? = null,
    isHost: Boolean? = null,
) {
    val status by gate.status.collectAsState()
    LaunchedEffect(status) {
        if (status.entersMultiplayerWithoutRationale) onContinue()
    }
    if (status.entersMultiplayerWithoutRationale) {
        // Navigation is applied from the effect, outside composition.
        Box(modifier = Modifier.fillMaxSize())
    } else {
        P2pPermissionRationaleScreen(
            gate = gate,
            onContinue = onContinue,
            onBack = onBack,
            modifier = Modifier.fillMaxSize(),
            gameTitle = gameTitle,
            roomAction = when (isHost) {
                true -> stringResource(Res.string.setup_host_title)
                false -> stringResource(Res.string.setup_join_title)
                null -> null
            },
        )
    }
}

/** Applies a corrupted or incomplete game-owned route reset after composition. */
@Composable
internal fun InvalidGameRouteFallback(onExit: () -> Unit) {
    LaunchedEffect(Unit) { onExit() }
    Box(modifier = Modifier.fillMaxSize())
}
