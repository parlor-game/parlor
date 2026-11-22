package com.parlor.app.shell.multiplayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.parlor.app.AppNavigator
import com.parlor.app.AppRoute
import com.parlor.app.resources.Res
import com.parlor.app.resources.invite_busy
import com.parlor.app.resources.invite_invalid
import com.parlor.app.resources.invite_update_required
import com.parlor.app.shell.game.GameShellRouter
import com.parlor.designsystem.components.LocalParlorToastState
import com.parlor.designsystem.components.ParlorToastSeverity
import com.parlor.networking.invitation.JoinLinkCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.compose.resources.stringResource

/** At most one process-local link. It is parsed on receipt and never saved to navigation state. */
internal object IncomingJoinLinks {
    class Delivery(val decoded: JoinLinkCodec.Decoded)

    private val latest = MutableStateFlow<Delivery?>(null)
    val pending = latest.asStateFlow()

    fun accept(raw: String) {
        latest.value = Delivery(JoinLinkCodec.decode(raw))
    }

    fun take(delivery: Delivery): Boolean = latest.compareAndSet(delivery, null)
}

@Composable
internal fun OpenIncomingJoinLink(
    active: Boolean,
    ownerIdle: Boolean,
    openingRecovery: Boolean,
    deletingRecovery: Boolean,
    navigator: AppNavigator,
    router: GameShellRouter,
) {
    val pending by IncomingJoinLinks.pending.collectAsState()
    val toast = LocalParlorToastState.current
    val invalid = stringResource(Res.string.invite_invalid)
    val incompatible = stringResource(Res.string.invite_update_required)
    val busy = stringResource(Res.string.invite_busy)
    val canOpen = ownerIdle && !openingRecovery && !deletingRecovery
    LaunchedEffect(pending, active, canOpen, navigator, router) {
        val delivery = pending ?: return@LaunchedEffect
        if (!active || !IncomingJoinLinks.take(delivery)) return@LaunchedEffect
        val decoded = delivery.decoded
        if (!canOpen || navigator.currentRoute != AppRoute.Home) {
            toast.show(busy, ParlorToastSeverity.Warning)
        } else {
            when (decoded) {
                JoinLinkCodec.Decoded.Invalid -> toast.show(invalid, ParlorToastSeverity.Warning)
                JoinLinkCodec.Decoded.IncompatibleVersion -> toast.show(incompatible, ParlorToastSeverity.Warning)
                is JoinLinkCodec.Decoded.Valid -> {
                    val launch = router.joinByInvitation(decoded.invitation)
                    if (launch == null) toast.show(incompatible, ParlorToastSeverity.Warning) else navigator.openGame(launch)
                }
            }
        }
    }
}
