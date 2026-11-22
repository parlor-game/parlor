package com.parlor.app.shell.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import com.parlor.app.resources.Res
import com.parlor.app.resources.featured_accent
import com.parlor.app.resources.featured_next
import com.parlor.app.resources.featured_play
import com.parlor.app.resources.featured_previous
import com.parlor.app.resources.featured_select
import com.parlor.app.resources.featured_top
import com.parlor.app.shell.game.GameCatalogPresentation
import com.parlor.app.shell.game.GameShellBinding
import com.parlor.core.ids.GameId
import com.parlor.designsystem.components.CatalogArtworkBadge
import com.parlor.designsystem.components.LibraryText
import com.parlor.designsystem.icons.ParlorIcons
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.designsystem.tokens.CozyNoirPalette
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun FeaturedGames(games: List<GameShellBinding>, onPlay: (GameId) -> Unit) {
    var selected by rememberSaveable { mutableIntStateOf(games.lastIndex / 2) }
    val index = selected.coerceIn(0, games.lastIndex)
    val activeGame = games[index]
    val presentation = activeGame.catalogPresentation()
    val colors = ParlorTheme.colors
    val shape = RoundedCornerShape(ParlorTheme.radii.elevated)

    Column(
        modifier = Modifier.fillMaxWidth().clip(shape).background(colors.surfaceElevated)
            .border(ParlorTheme.borders.hairline, colors.borderSubtle, shape),
        verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
    ) {
        FeaturedCover(presentation)
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup().horizontalScroll(rememberScrollState())
                .padding(horizontal = ParlorTheme.spacing.m),
            horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s, Alignment.CenterHorizontally),
        ) {
            games.forEachIndexed { position, game ->
                val option = game.catalogPresentation()
                val label = stringResource(Res.string.featured_select, option.title)
                val active = position == index
                val tile = RoundedCornerShape(ParlorTheme.radii.subtle)
                Box(
                    modifier = Modifier.size(width = 48.dp, height = 60.dp).clip(tile)
                        .selectable(selected = active, role = Role.Tab) { selected = position }
                        .semantics { contentDescription = label },
                ) {
                    CatalogArtworkBadge(option.artwork, Modifier.fillMaxSize())
                    if (active) Box(Modifier.fillMaxSize().border(ParlorTheme.borders.strong, colors.accentEmber, tile))
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xs, Alignment.CenterHorizontally),
        ) {
            games.indices.forEach { position ->
                Box(
                    Modifier.size(if (index == position) 22.dp else 8.dp, 3.dp)
                        .clip(RoundedCornerShape(ParlorTheme.radii.pill))
                        .background(if (index == position) colors.accentEmber else colors.borderElevated),
                )
            }
        }
        val playLabel = stringResource(Res.string.featured_play, presentation.title)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = ParlorTheme.spacing.s).padding(bottom = ParlorTheme.spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s, Alignment.CenterHorizontally),
        ) {
            IconButton(onClick = { selected = (index + games.size - 1) % games.size }, enabled = games.size > 1) {
                Icon(ParlorIcons.Back, stringResource(Res.string.featured_previous), tint = colors.textSecondary)
            }
            FeaturedPlayButton(playLabel, Modifier.weight(1f, fill = false)) { onPlay(activeGame.definition.id) }
            IconButton(onClick = { selected = (index + 1) % games.size }, enabled = games.size > 1) {
                Icon(ParlorIcons.Forward, stringResource(Res.string.featured_next), tint = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun FeaturedPlayButton(label: String, modifier: Modifier, onPlay: () -> Unit) {
    val colors = ParlorTheme.colors
    val pill = RoundedCornerShape(ParlorTheme.radii.pill)
    val amber = CozyNoirPalette.accentEmber
    Box(
        modifier = modifier.widthIn(max = 240.dp).heightIn(min = ParlorTheme.spacing.xxl)
            .clip(pill).background(Brush.horizontalGradient(listOf(amber, FeaturedAmberEdge)))
            .clickable(role = Role.Button, onClick = onPlay)
            .padding(horizontal = ParlorTheme.spacing.l, vertical = ParlorTheme.spacing.s),
        contentAlignment = Alignment.Center,
    ) {
        LibraryText(label, ParlorTheme.typography.labelLarge, colors.coverScreen, textAlign = TextAlign.Center)
    }
}

@Composable
private fun FeaturedCover(presentation: GameCatalogPresentation) {
    val colors = ParlorTheme.colors
    Box(Modifier.fillMaxWidth().heightIn(min = 198.dp)) {
        CatalogArtworkBadge(presentation.artwork, Modifier.matchParentSize())
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to colors.coverScreen.copy(alpha = 0.84f),
                    FEATURED_SCRIM_CLEAR_STOP to colors.transparent,
                    1f to colors.coverScreen.copy(alpha = 0.96f),
                ),
            ),
        )
        Column(Modifier.align(Alignment.TopStart).padding(ParlorTheme.spacing.m)) {
            LibraryText(stringResource(Res.string.featured_top), ParlorTheme.typography.headingMedium, colors.coverScreenTextPrimary)
            LibraryText(stringResource(Res.string.featured_accent), ParlorTheme.typography.headingMedium, CozyNoirPalette.accentEmberGlow)
        }
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(ParlorTheme.spacing.m),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LibraryText(presentation.title, ParlorTheme.typography.headingLarge, colors.coverScreenTextPrimary)
            LibraryText(presentation.tagline, ParlorTheme.typography.bodyMedium, colors.coverScreenTextPrimary)
        }
    }
}

private const val FEATURED_SCRIM_CLEAR_STOP = 0.43f
private val FeaturedAmberEdge = Color(0xFFEE6D16)
