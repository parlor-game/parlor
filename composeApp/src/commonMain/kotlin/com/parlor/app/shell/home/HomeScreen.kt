package com.parlor.app.shell.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.parlor.designsystem.components.LibraryText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.parlor.app.resources.Res
import com.parlor.app.resources.app_name
import com.parlor.app.resources.catalog_empty_body
import com.parlor.app.resources.catalog_empty_title
import com.parlor.app.resources.catalog_nearby_only_meta
import com.parlor.app.resources.catalog_mode_one_phone_wifi
import com.parlor.app.resources.catalog_mode_wifi_only
import com.parlor.app.resources.catalog_players_and_genre
import com.parlor.app.resources.home_continue_label
import com.parlor.app.resources.home_guest
import com.parlor.app.resources.home_party_subtitle
import com.parlor.app.resources.home_games_count
import com.parlor.app.resources.home_local_lan_meta
import com.parlor.app.resources.home_local_meta
import com.parlor.app.resources.home_players_exact_format
import com.parlor.app.resources.home_players_range_format
import com.parlor.app.resources.home_recovery_checking
import com.parlor.app.resources.home_recovery_retry
import com.parlor.app.resources.home_recovery_retry_description
import com.parlor.app.resources.home_recovery_unavailable_body
import com.parlor.app.resources.home_recovery_unavailable_title
import com.parlor.app.resources.home_resume_multiplayer_description
import com.parlor.app.resources.home_resume_multiplayer_subtitle
import com.parlor.app.resources.home_resume_multiplayer_title
import com.parlor.app.resources.home_resume_tile_description
import com.parlor.app.resources.home_resume_tile_game_description
import com.parlor.app.resources.home_resume_tile_game_title
import com.parlor.app.resources.home_resume_tile_position
import com.parlor.app.resources.home_resume_tile_subtitle
import com.parlor.app.resources.home_resume_tile_title
import com.parlor.app.resources.home_resume_tile_unknown_description
import com.parlor.app.resources.home_saved_on_device
import com.parlor.app.resources.home_title
import com.parlor.app.resources.resume_delete_action
import com.parlor.app.resources.resume_delete_description
import com.parlor.app.shell.game.GameCatalogPresentation
import com.parlor.app.shell.game.GameShellBinding
import com.parlor.app.shell.profile.LocalPreferredDisplayName
import com.parlor.app.shell.profile.OpenProfileButton
import com.parlor.app.shell.profile.ProfileGreeting
import com.parlor.core.ids.GameId
import com.parlor.core.ids.SessionId
import com.parlor.designsystem.components.CatalogCover
import com.parlor.designsystem.components.EmptyState
import com.parlor.designsystem.components.LibrarySurface
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.components.ParlorMark
import com.parlor.designsystem.components.libraryFocusOutline
import com.parlor.designsystem.components.parlorSafeContentPaddingValues
import com.parlor.designsystem.icons.ParlorArtwork
import com.parlor.designsystem.icons.ParlorIcons
import com.parlor.designsystem.theme.ParlorTheme
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The game library leads with recovery actions when a saved game or seat is available. */
@Composable
internal fun HomeScreen(
    games: List<GameShellBinding>,
    onGameSelected: (GameId) -> Unit,
    modifier: Modifier = Modifier,
    unfinishedSessions: List<LocalRecoveryEntry> = emptyList(),
    onResume: (SessionId) -> Unit = {},
    hasResumableMultiplayer: Boolean = false,
    resumableMultiplayerGameId: GameId? = null,
    onResumeMultiplayer: () -> Unit = {},
    recoveryLoading: Boolean = false,
    recoveryUnavailable: Boolean = false,
    onRetryRecovery: () -> Unit = {},
    onDiscardLocal: (SessionId, String) -> Unit = { _, _ -> },
    onDiscardMultiplayer: (String) -> Unit = {},
    recoveryActionsEnabled: Boolean = true,
    catalogScrollState: HomeCatalogScrollState = rememberHomeCatalogScrollState(),
    onOpenProfile: (() -> Unit)? = null,
) {
    var selectedFilter by rememberSaveable { mutableStateOf(HomeCatalogFilter.All) }
    var returnFocusKey by rememberSaveable { mutableStateOf<String?>(null) }
    val filteredGames = games.filter { selectedFilter.accepts(it.capabilities) }
    val itemKeys = homeCatalogItemKeys(
        filteredGames, unfinishedSessions, hasResumableMultiplayer, recoveryLoading, recoveryUnavailable,
    )
    LaunchedEffect(catalogScrollState, recoveryLoading, itemKeys) {
        if (!recoveryLoading) catalogScrollState.restoreAfterRecovery(itemKeys)
    }
    LibrarySurface(modifier.fillMaxSize(), litHeader = false) {
        val padding = parlorSafeContentPaddingValues(
            horizontal = ParlorTheme.spacing.m, top = ParlorTheme.spacing.m, bottom = ParlorTheme.spacing.xxl,
        )
        val columns = libraryColumns(maxWidth, padding)
        val showFeatured = LocalDensity.current.fontScale < HOME_COMPACT_INTRODUCTION_FONT_SCALE
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = catalogScrollState.listState,
            modifier = Modifier.widthIn(max = if (columns == 1) ReadingWidth else LibraryWidth).fillMaxSize(),
            contentPadding = padding,
            horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "home-top-bar") { HomeTopBar(onOpenProfile) }
            if (unfinishedSessions.isNotEmpty() || hasResumableMultiplayer) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "continue-label") {
                    RecoveryHeading()
                }
                itemsIndexed(
                    items = unfinishedSessions,
                    span = { _, _ -> GridItemSpan(maxLineSpan) },
                    key = { _, entry -> "local:${entry.sessionId.raw}" },
                ) { index, entry ->
                    LocalResumeTile(
                        entry = entry,
                        position = index + 1,
                        total = unfinishedSessions.size,
                        games = games,
                        onResume = onResume,
                        onDiscard = onDiscardLocal,
                        enabled = recoveryActionsEnabled,
                    )
                }
                if (hasResumableMultiplayer) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "multiplayer-resume") {
                        ResumeTile(
                            title = stringResource(Res.string.home_resume_multiplayer_title),
                            subtitle = stringResource(Res.string.home_resume_multiplayer_subtitle),
                            contentDescription = stringResource(
                                Res.string.home_resume_multiplayer_description,
                            ),
                            artwork = games.firstOrNull { it.definition.id == resumableMultiplayerGameId }
                                ?.catalogPresentation()?.artwork,
                            onTap = onResumeMultiplayer,
                            onDiscard = onDiscardMultiplayer,
                            enabled = recoveryActionsEnabled,
                        )
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }, key = "home-lead") {
                if (showFeatured && games.size > 1) FeaturedGames(games, onGameSelected)
            }

            when {
                recoveryLoading -> item(span = { GridItemSpan(maxLineSpan) }, key = "recovery-loading") { RecoveryLoadingCard() }
                recoveryUnavailable -> item(span = { GridItemSpan(maxLineSpan) }, key = "recovery-unavailable") {
                    RecoveryUnavailableCard(onRetryRecovery)
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }, key = "games-label") {
                Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s)) {
                    CatalogHeading(catalogGameCount(filteredGames.size))
                    CatalogFilters(selectedFilter, games) {
                        catalogScrollState.cancelPendingRestoration()
                        selectedFilter = it
                    }
                }
            }
            if (filteredGames.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "catalog-empty") {
                    EmptyState(
                        title = stringResource(Res.string.catalog_empty_title),
                        body = stringResource(Res.string.catalog_empty_body),
                    )
                }
            }
            items(
                items = filteredGames,
                key = { game -> "game:${game.definition.id.raw}" },
            ) { game ->
                val presentation = game.catalogPresentation()
                GameHeroCard(
                    presentation = presentation,
                    compact = columns == 1,
                    restoreFocus = returnFocusKey == game.definition.id.raw,
                    onFocusRestored = { returnFocusKey = null },
                    supportedPlayerCounts = game.multiplayerContract
                        ?.supportedPlayerCounts
                        ?: game.definition.supportedPlayerCounts,
                    supportsLan = game.capabilities.supportsNearbyPlay(),
                    supportsLocal = game.capabilities.supportsLocalPlay(),
                    onOpen = { focused ->
                        returnFocusKey = game.definition.id.raw.takeIf { focused }
                        catalogScrollState.cancelPendingRestoration()
                        onGameSelected(game.definition.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun libraryColumns(width: Dp, padding: PaddingValues): Int {
    val direction = LocalLayoutDirection.current
    val fontScale = LocalDensity.current.fontScale
    val available = minOf(width, LibraryWidth) - padding.calculateLeftPadding(direction) - padding.calculateRightPadding(direction)
    return when {
        fontScale >= HOME_COMPACT_INTRODUCTION_FONT_SCALE || available / fontScale < TwoCoverWidth -> 1
        available / fontScale < ThreeCoverWidth -> 2
        else -> WIDE_LIBRARY_COLUMNS
    }
}

@Composable
private fun catalogGameCount(count: Int): String = pluralStringResource(Res.plurals.home_games_count, count, count)

@Composable
private fun HomeTopBar(onOpenProfile: (() -> Unit)?) {
    val name = LocalPreferredDisplayName.current ?: stringResource(Res.string.home_guest)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            ProfileGreeting(name, Modifier.testTag(HOME_BRAND_TEST_TAG))
            LibraryText(
                text = stringResource(Res.string.home_party_subtitle),
                style = ParlorTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 15.sp),
                color = ParlorTheme.colors.textSecondary,
            )
        }
        if (onOpenProfile != null) {
            OpenProfileButton(onOpenProfile)
        } else {
            val brand = stringResource(Res.string.app_name)
            Box(Modifier.semantics { contentDescription = brand }) {
                ParlorMark(modifier = Modifier.size(ParlorTheme.spacing.xxl))
            }
        }
    }
}

@Composable
private fun CatalogHeading(detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f).semantics { heading() }) {
            LibraryText(
                text = stringResource(Res.string.app_name),
                style = ParlorTheme.typography.labelLarge,
                color = ParlorTheme.colors.textPrimary,
            )
            LibraryText(
                text = stringResource(Res.string.home_title),
                style = ParlorTheme.typography.headingMedium,
                color = ParlorTheme.colors.accentEmber,
            )
        }
        CatalogBadge(detail)
    }
}

@Composable
private fun RecoveryHeading() {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xs),
    ) {
        LibraryText(
            text = stringResource(Res.string.home_continue_label),
            style = ParlorTheme.typography.headingMedium,
            color = ParlorTheme.colors.accentEmber,
            modifier = Modifier.align(Alignment.CenterVertically).semantics { heading() },
        )
        Box(Modifier.align(Alignment.CenterVertically)) {
            CatalogBadge(stringResource(Res.string.home_saved_on_device))
        }
    }
}

@Composable
private fun RecoveryLoadingCard() {
    ParlorCard(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        contentPadding = ParlorTheme.spacing.m,
    ) {
        LibraryText(
            text = stringResource(Res.string.home_recovery_checking),
            style = ParlorTheme.typography.bodyMedium,
            color = ParlorTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun RecoveryUnavailableCard(onRetry: () -> Unit) {
    ParlorCard(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        contentPadding = ParlorTheme.spacing.m,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m)) {
            LibraryText(
                text = stringResource(Res.string.home_recovery_unavailable_title),
                style = ParlorTheme.typography.headingLarge,
                color = ParlorTheme.colors.textPrimary,
            )
            LibraryText(
                text = stringResource(Res.string.home_recovery_unavailable_body),
                style = ParlorTheme.typography.bodyMedium,
                color = ParlorTheme.colors.textSecondary,
            )
            ParlorButton(
                label = stringResource(Res.string.home_recovery_retry),
                contentDescription = stringResource(Res.string.home_recovery_retry_description),
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LocalResumeTile(
    entry: LocalRecoveryEntry,
    position: Int,
    total: Int,
    games: List<GameShellBinding>,
    onResume: (SessionId) -> Unit,
    onDiscard: (SessionId, String) -> Unit,
    enabled: Boolean,
) {
    val binding = entry.gameId?.let { gameId ->
        games.firstOrNull { game -> game.definition.id == gameId }
    }
    val presentation = binding?.catalogPresentation()
    val gameTitle = presentation?.title
    val title = if (gameTitle == null) {
        stringResource(Res.string.home_resume_tile_title)
    } else {
        stringResource(Res.string.home_resume_tile_game_title, gameTitle)
    }
    val contentDescription = if (gameTitle == null) {
        if (total == 1) {
            stringResource(Res.string.home_resume_tile_description)
        } else {
            stringResource(Res.string.home_resume_tile_unknown_description, position, total)
        }
    } else {
        stringResource(
            Res.string.home_resume_tile_game_description,
            gameTitle,
            position,
            total,
        )
    }
    ResumeTile(
        title = title,
        subtitle = if (total == 1) {
            stringResource(Res.string.home_resume_tile_subtitle)
        } else {
            stringResource(Res.string.home_resume_tile_position, position, total)
        },
        contentDescription = contentDescription,
        artwork = presentation?.artwork,
        onTap = { onResume(entry.sessionId) },
        onDiscard = { onDiscard(entry.sessionId, it) },
        enabled = enabled,
    )
}

@Composable
private fun ResumeTile(
    title: String,
    subtitle: String,
    contentDescription: String,
    artwork: ParlorArtwork?,
    onTap: () -> Unit,
    onDiscard: (String) -> Unit,
    enabled: Boolean,
) {
    val removeDescription = stringResource(Res.string.resume_delete_description, contentDescription)
    ParlorCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = ParlorTheme.spacing.s,
    ) {
        val resume: @Composable () -> Unit = {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .semantics { this.contentDescription = contentDescription }
                    .clickable(enabled = enabled, role = Role.Button, onClick = onTap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                artwork?.let {
                    CatalogCover(it, Modifier.padding(start = ParlorTheme.spacing.xs).size(42.dp)
                        .clip(RoundedCornerShape(10.dp)))
                }
                Column(
                    modifier = Modifier.weight(1f).heightIn(min = ParlorTheme.spacing.xxl)
                        .padding(ParlorTheme.spacing.s),
                    verticalArrangement = Arrangement.Center,
                ) {
                    LibraryText(title, style = ParlorTheme.typography.headingMedium, color = ParlorTheme.colors.textPrimary)
                    LibraryText(subtitle, style = ParlorTheme.typography.bodySmall, color = ParlorTheme.colors.textSecondary)
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val remove: @Composable () -> Unit = {
                val danger = ParlorTheme.colors.semanticDanger
                val shape = RoundedCornerShape(ParlorTheme.radii.subtle)
                TextButton(
                    onClick = { onDiscard("$title — $subtitle") },
                    enabled = enabled,
                    shape = shape,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = danger,
                        containerColor = danger.copy(alpha = 0.10f),
                    ),
                    border = BorderStroke(ParlorTheme.borders.hairline, danger.copy(alpha = 0.35f)),
                    contentPadding = PaddingValues(horizontal = ParlorTheme.spacing.s),
                    modifier = Modifier.heightIn(min = ParlorTheme.spacing.xxl)
                        .semantics { this.contentDescription = removeDescription }.libraryFocusOutline(shape),
                ) {
                    Text(stringResource(Res.string.resume_delete_action), style = ParlorTheme.typography.labelLarge)
                }
            }
            val resumeOutline = Modifier.libraryFocusOutline(RoundedCornerShape(ParlorTheme.radii.subtle))
            if (maxWidth < RecoveryRowWidth || LocalDensity.current.fontScale >= HOME_COMPACT_INTRODUCTION_FONT_SCALE) {
                Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xs)) {
                    Box(resumeOutline) { resume() }
                    remove()
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xs),
                ) {
                    Box(Modifier.weight(1f).then(resumeOutline)) { resume() }
                    remove()
                }
            }
        }
    }
}

@Composable
private fun GameHeroCard(
    presentation: GameCatalogPresentation,
    supportedPlayerCounts: IntRange,
    supportsLan: Boolean,
    supportsLocal: Boolean,
    compact: Boolean,
    restoreFocus: Boolean,
    onFocusRestored: () -> Unit,
    onOpen: (Boolean) -> Unit,
) {
    val players = playerRangeLabel(supportedPlayerCounts)
    val modes = stringResource(when {
        supportsLan && supportsLocal -> Res.string.home_local_lan_meta
        supportsLan -> Res.string.catalog_nearby_only_meta
        else -> Res.string.home_local_meta
    })
    val requester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (restoreFocus && requester.requestFocus()) onFocusRestored()
    }
    val shape = RoundedCornerShape(ParlorTheme.radii.card)
    ParlorCard(
        modifier = Modifier.fillMaxWidth().clip(shape).focusRequester(requester)
            .onFocusChanged { focused = it.isFocused }.libraryFocusOutline(shape)
            .semantics {
                contentDescription = presentation.openContentDescription
                stateDescription = "${presentation.tagline} $players · $modes"
            }
            .clickable(role = Role.Button) { onOpen(focused) },
        contentPadding = 0.dp,
    ) {
        if (compact && LocalDensity.current.fontScale < HOME_COMPACT_INTRODUCTION_FONT_SCALE) {
            CompactGameRow(
                presentation = presentation,
                details = presentation.genre?.let { stringResource(Res.string.catalog_players_and_genre, it, players) } ?: players,
                mode = catalogModeLabel(supportsLocal, supportsLan),
                compatibleLocally = supportsLocal,
            )
        } else Column {
            if (!compact) {
                CatalogCover(
                    presentation.artwork,
                    Modifier.fillMaxWidth().aspectRatio(COVER_ASPECT_RATIO),
                )
            }
            Column(
                modifier = Modifier.padding(ParlorTheme.spacing.m),
                verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
            ) {
                if (compact) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CatalogCover(presentation.artwork, Modifier.size(ParlorTheme.spacing.xxxl))
                        CatalogGameTitle(presentation.title)
                    }
                } else {
                    CatalogGameTitle(presentation.title)
                    CatalogTagline(presentation.tagline)
                }
                CatalogMetadata(players, modes)
                if (compact) Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LibraryText(
                        text = presentation.openLabel,
                        modifier = Modifier.weight(1f),
                        style = ParlorTheme.typography.labelLarge,
                        color = ParlorTheme.colors.accentEmber,
                    )
                    Icon(
                        imageVector = ParlorIcons.Forward,
                        contentDescription = null,
                        tint = ParlorTheme.colors.accentEmber,
                    )
                }
                if (compact) CatalogTagline(presentation.tagline)
            }
        }
    }
}

@Composable
private fun CompactGameRow(presentation: GameCatalogPresentation, details: String, mode: String, compatibleLocally: Boolean) {
    val colors = ParlorTheme.colors
    val coverShape = RoundedCornerShape(13.dp)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CatalogCover(
            presentation.artwork,
            Modifier.size(56.dp).clip(coverShape).border(1.dp, colors.borderSubtle, coverShape),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            LibraryText(
                presentation.title,
                ParlorTheme.typography.headingMedium.copy(fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold),
                colors.textPrimary,
            )
            LibraryText(details, ParlorTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 15.sp), colors.textSecondary)
            CatalogBadge(mode, if (compatibleLocally) colors.semanticSuccess else colors.accentEmber)
        }
        Icon(ParlorIcons.ChevronNext, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(16.dp))
    }
}

@Composable
internal fun catalogModeLabel(local: Boolean, nearby: Boolean): String = stringResource(when {
    local && nearby -> Res.string.catalog_mode_one_phone_wifi
    nearby -> Res.string.catalog_mode_wifi_only
    else -> Res.string.home_local_meta
})

@Composable
internal fun CatalogBadge(text: String, dot: androidx.compose.ui.graphics.Color? = null) {
    val colors = ParlorTheme.colors
    Row(
        modifier = Modifier.background(colors.surfaceHigher, CircleShape)
            .border(1.dp, colors.borderSubtle, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) Box(Modifier.size(6.dp).background(dot, CircleShape))
        LibraryText(
            text, ParlorTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold),
            colors.textSecondary,
        )
    }
}

@Composable
private fun CatalogGameTitle(title: String) {
    LibraryText(
        text = title,
        style = ParlorTheme.typography.headingLarge,
        color = ParlorTheme.colors.textPrimary,
        modifier = Modifier.fillMaxWidth().semantics { heading() },
    )
}

@Composable
private fun CatalogTagline(text: String) {
    LibraryText(text = text, style = ParlorTheme.typography.bodyMedium, color = ParlorTheme.colors.textSecondary, preferredLines = 2)
}

@Composable
private fun CatalogMetadata(players: String, modes: String) {
    Column {
        LibraryText(players, style = ParlorTheme.typography.labelMedium, color = ParlorTheme.colors.textPrimary)
        LibraryText(modes, style = ParlorTheme.typography.bodySmall, color = ParlorTheme.colors.textSecondary)
    }
}

@Composable
internal fun playerRangeLabel(range: IntRange): String =
    if (range.first == range.last) {
        stringResource(Res.string.home_players_exact_format, range.first)
    } else {
        stringResource(Res.string.home_players_range_format, range.first, range.last)
    }

private const val HOME_COMPACT_INTRODUCTION_FONT_SCALE = 1.5f
private val TwoCoverWidth = 440.dp
private val ThreeCoverWidth = 688.dp
private val ReadingWidth = 560.dp
private val LibraryWidth = 960.dp
private const val WIDE_LIBRARY_COLUMNS = 3
private const val COVER_ASPECT_RATIO = 1.35f
private val RecoveryRowWidth = 304.dp
private const val HOME_BRAND_TEST_TAG = "parlor-home-brand"
