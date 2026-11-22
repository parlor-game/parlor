package com.parlor.app.shell.multiplayer

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.parlor.app.lifecycle.AppLifecycleCoordinator
import com.parlor.app.resources.Res
import com.parlor.app.resources.nearby_clear
import com.parlor.app.resources.nearby_empty
import com.parlor.app.resources.nearby_error
import com.parlor.app.resources.nearby_room
import com.parlor.app.resources.nearby_search
import com.parlor.app.resources.nearby_searching
import com.parlor.app.resources.nearby_selected
import com.parlor.app.resources.nearby_stop
import com.parlor.core.result.Result
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorButtonVariant
import com.parlor.designsystem.localization.asBidiArgument
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.networking.room.DiscoveredRoom
import com.parlor.networking.room.DiscoveredRoomSelection
import com.parlor.networking.room.NetError
import com.parlor.networking.transport.HostedGameProtocol
import com.parlor.networking.transport.RoomTransport
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
internal fun NearbyRoomsPanel(
    transport: RoomTransport,
    game: HostedGameProtocol,
    selection: DiscoveredRoomSelection?,
    onSelect: (DiscoveredRoomSelection?) -> Unit,
) {
    val lifecycle: AppLifecycleCoordinator = koinInject()
    val visibility by lifecycle.visibility.collectAsState()
    var searching by remember(game) { mutableStateOf(false) }
    var found by remember(game) { mutableStateOf<Result<List<DiscoveredRoom>, NetError>?>(null) }
    var selectedTag by remember(game) { mutableStateOf<String?>(null) }
    val canSearch = searching && !visibility.privateContentCovered
    LaunchedEffect(canSearch, transport, game) {
        if (canSearch) {
            found = null
            withTimeoutOrNull(SEARCH_LIMIT_MS) { transport.discoverRooms(game).collect { found = it } }
            searching = false
        } else {
            searching = false
        }
    }

    val toggle = stringResource(if (searching) Res.string.nearby_stop else Res.string.nearby_search)
    ParlorButton(
        label = toggle,
        contentDescription = toggle,
        onClick = { searching = !searching },
        variant = ParlorButtonVariant.Secondary,
        modifier = Modifier.fillMaxWidth(),
    )
    selectedTag?.let { tag ->
        val label = stringResource(Res.string.nearby_selected, tag.asBidiArgument())
        NearbyStatus(label)
        val clear = stringResource(Res.string.nearby_clear)
        ParlorButton(clear, { selectedTag = null; onSelect(null) }, clear, variant = ParlorButtonVariant.Ghost)
    }
    if (searching || found is Result.Failure) {
        NearbyRoomResults(found, selection) { tag, room ->
            selectedTag = tag
            onSelect(room)
            searching = false
        }
    }
}

@Composable
private fun NearbyRoomResults(
    found: Result<List<DiscoveredRoom>, NetError>?,
    selection: DiscoveredRoomSelection?,
    onSelect: (String, DiscoveredRoomSelection) -> Unit,
) {
    when (found) {
        null -> NearbyStatus(stringResource(Res.string.nearby_searching))
        is Result.Failure -> NearbyStatus(stringResource(Res.string.nearby_error))
        is Result.Success -> {
            if (found.data.isEmpty()) NearbyStatus(stringResource(Res.string.nearby_empty))
            found.data.forEach { room ->
                val tag = room.roomTag
                val target = room.selection
                if (tag != null && target != null) {
                    val label = stringResource(Res.string.nearby_room, tag.asBidiArgument())
                    ParlorButton(
                        label = label,
                        contentDescription = label,
                        onClick = { onSelect(tag, target) },
                        enabled = target != selection,
                        variant = ParlorButtonVariant.Secondary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun NearbyStatus(message: String) {
    Text(message, color = ParlorTheme.colors.textSecondary, style = ParlorTheme.typography.bodySmall)
}

private const val SEARCH_LIMIT_MS = 120_000L
