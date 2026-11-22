package com.parlor.app.shell.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import com.parlor.designsystem.components.LibraryText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parlor.app.resources.Res
import com.parlor.app.resources.catalog_filter_all
import com.parlor.app.resources.catalog_filter_local
import com.parlor.app.resources.catalog_filter_nearby
import com.parlor.app.shell.game.GameEntryMode
import com.parlor.app.shell.game.GameShellCapabilities
import com.parlor.app.shell.game.GameShellBinding
import com.parlor.designsystem.components.libraryFocusOutline
import com.parlor.designsystem.theme.ParlorTheme
import org.jetbrains.compose.resources.stringResource

internal enum class HomeCatalogFilter { All, Local, Nearby }

internal fun GameShellCapabilities.supportsLocalPlay(): Boolean =
    supports(GameEntryMode.Solo) || supports(GameEntryMode.PassAndPlay)

internal fun GameShellCapabilities.supportsNearbyPlay(): Boolean =
    supports(GameEntryMode.Host) || supports(GameEntryMode.Join)

internal fun HomeCatalogFilter.accepts(capabilities: GameShellCapabilities): Boolean = when (this) {
    HomeCatalogFilter.All -> true
    HomeCatalogFilter.Local -> capabilities.supportsLocalPlay()
    HomeCatalogFilter.Nearby -> capabilities.supportsNearbyPlay()
}

@Composable
internal fun CatalogFilters(selected: HomeCatalogFilter, games: List<GameShellBinding>, onSelected: (HomeCatalogFilter) -> Unit) {
    val colors = ParlorTheme.colors
    val shape = RoundedCornerShape(ParlorTheme.radii.pill)
    FlowRow(
        modifier = Modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HomeCatalogFilter.entries.forEach { filter ->
            val active = filter == selected
            val labelColor = if (active) colors.accentEmber else colors.textSecondary
            Row(
                modifier = Modifier.heightIn(min = 48.dp).libraryFocusOutline(shape)
                    .selectable(selected = active, role = Role.Checkbox) { onSelected(filter) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.clip(shape)
                        .background(if (active) colors.accentEmber.copy(alpha = ACTIVE_CHIP_WASH) else colors.surfaceElevated)
                        .border(1.dp, if (active) colors.borderAccent else colors.borderSubtle, shape)
                        .padding(horizontal = 11.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LibraryText(
                        text = stringResource(when (filter) {
                            HomeCatalogFilter.All -> Res.string.catalog_filter_all
                            HomeCatalogFilter.Local -> Res.string.catalog_filter_local
                            HomeCatalogFilter.Nearby -> Res.string.catalog_filter_nearby
                        }),
                        style = ParlorTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
                        color = labelColor,
                    )
                    LibraryText(
                        text = games.count { filter.accepts(it.capabilities) }.toString(),
                        style = ParlorTheme.typography.labelMedium.copy(fontSize = 10.sp, lineHeight = 14.sp),
                        color = labelColor,
                    )
                }
            }
        }
    }
}

private const val ACTIVE_CHIP_WASH = 0.12f
