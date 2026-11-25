package com.parlor.app.shell.game.multiplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.parlor.app.resources.Res
import com.parlor.app.resources.room_admitted_progress
import com.parlor.app.resources.room_admitted_title
import com.parlor.app.resources.room_approval_progress
import com.parlor.app.resources.room_approval_title
import com.parlor.app.resources.room_discovering_progress
import com.parlor.app.resources.room_discovering_title
import com.parlor.app.resources.room_game_host
import com.parlor.app.resources.room_game_leave
import com.parlor.app.resources.room_game_opening
import com.parlor.app.resources.room_opening_progress
import com.parlor.app.shell.game.GameCatalogPresentation
import com.parlor.designsystem.components.AdmissionWaiting
import com.parlor.designsystem.components.CatalogCover
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.LibrarySurface
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.components.StickyActionLayout
import com.parlor.designsystem.components.parlorSafeContentPadding
import com.parlor.designsystem.localization.asBidiArgument
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.networking.transport.ApprovalWait
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal enum class RoomOpeningPhase(val title: StringResource, val progress: StringResource) {
    Opening(Res.string.room_game_opening, Res.string.room_opening_progress),
    Discovering(Res.string.room_discovering_title, Res.string.room_discovering_progress),
    Approval(Res.string.room_approval_title, Res.string.room_approval_progress),
    Admitted(Res.string.room_admitted_title, Res.string.room_admitted_progress),
}

@Composable
internal fun RoomOpeningStatus(
    game: GameCatalogPresentation,
    phase: RoomOpeningPhase,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
    hostName: String? = null,
    enabled: Boolean = true,
    approval: ApprovalWait? = null,
) {
    val spacing = ParlorTheme.spacing
    LibrarySurface(modifier.fillMaxSize(), litHeader = false) {
        StickyActionLayout(
            modifier = Modifier.widthIn(max = 560.dp),
            content = {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .parlorSafeContentPadding(spacing.m),
                    verticalArrangement = Arrangement.spacedBy(spacing.m),
                ) {
                    ParlorCard(modifier = Modifier.fillMaxWidth(), contentPadding = spacing.m) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(spacing.m),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CatalogCover(game.artwork, Modifier.size(60.dp).clip(RoundedCornerShape(12.dp)))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                                EyebrowLabel(game.title)
                                Text(
                                    stringResource(phase.title), color = ParlorTheme.colors.textPrimary,
                                    style = ParlorTheme.typography.headingLarge,
                                    modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite },
                                )
                                hostName?.let {
                                    Text(
                                        stringResource(Res.string.room_game_host, it.asBidiArgument()),
                                        color = ParlorTheme.colors.textSecondary, style = ParlorTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                    }
                    approval?.let {
                        ParlorCard(modifier = Modifier.fillMaxWidth(), contentPadding = spacing.m) {
                            AdmissionWaiting(it.lobby.hostDisplayName, it.deadline)
                        }
                    }
                    ParlorCard(modifier = Modifier.fillMaxWidth(), contentPadding = spacing.m) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(spacing.s),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(8.dp).background(ParlorTheme.colors.accentEmber, CircleShape))
                            Text(
                                stringResource(phase.progress), color = ParlorTheme.colors.textPrimary,
                                style = ParlorTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            },
            actions = { RoomButton(stringResource(Res.string.room_game_leave), onLeave, enabled, secondary = true) },
        )
    }
}
