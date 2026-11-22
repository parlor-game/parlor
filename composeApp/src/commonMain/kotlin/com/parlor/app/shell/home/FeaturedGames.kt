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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.parlor.app.resources.Res
import com.parlor.app.resources.catalog_players_and_genre
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
import com.parlor.designsystem.tokens.AmberActionBrush
import com.parlor.designsystem.tokens.AmberActionGlow
import com.parlor.designsystem.tokens.AmberActionInk
import com.parlor.designsystem.tokens.CozyNoirPalette
import com.parlor.designsystem.tokens.isLight
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun FeaturedGames(games: List<GameShellBinding>, onPlay: (GameId) -> Unit) {
    var selected by rememberSaveable { mutableIntStateOf(games.lastIndex / 2) }
    val index = selected.coerceIn(0, games.lastIndex)
    val activeGame = games[index]
    val presentation = activeGame.catalogPresentation()
    val colors = ParlorTheme.colors
    val shape = RoundedCornerShape(22.dp)

    Column(
        modifier = Modifier.fillMaxWidth().clip(shape).background(colors.surfaceElevated)
            .border(ParlorTheme.borders.hairline, colors.borderSubtle, shape),
        verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
    ) {
        FeaturedCover(presentation, activeGame.multiplayerContract?.supportedPlayerCounts ?: activeGame.definition.supportedPlayerCounts)
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup().horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
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
                    Box(
                        Modifier.fillMaxSize().border(
                            width = if (active) 2.dp else 1.dp,
                            color = if (active) colors.accentEmberGlow else colors.borderElevated,
                            shape = tile,
                        ),
                    )
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
            modifier = Modifier.fillMaxWidth().drawWithCache {
                val center = Offset(size.width / 2f, size.height * PLAY_HALO_Y)
                val radius = size.width * PLAY_HALO_RADIUS
                val halo = Brush.radialGradient(
                    listOf(AmberActionGlow.copy(alpha = PLAY_GLOW_ALPHA), AmberActionGlow.copy(alpha = 0f)),
                    center = center,
                    radius = radius,
                )
                onDrawBehind { drawRect(halo, size = Size(size.width, size.height)) }
            }.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        ) {
            val arrow = Modifier.border(ParlorTheme.borders.hairline, colors.borderSubtle, RoundedCornerShape(ParlorTheme.radii.pill))
            IconButton(onClick = { selected = (index + games.size - 1) % games.size }, modifier = arrow, enabled = games.size > 1) {
                Icon(
                    ParlorIcons.ChevronNext,
                    stringResource(Res.string.featured_previous),
                    Modifier.size(18.dp).rotate(CHEVRON_REVERSE_DEGREES),
                    colors.textSecondary,
                )
            }
            FeaturedPlayButton(playLabel, Modifier.weight(1f, fill = false)) { onPlay(activeGame.definition.id) }
            IconButton(onClick = { selected = (index + 1) % games.size }, modifier = arrow, enabled = games.size > 1) {
                Icon(ParlorIcons.ChevronNext, stringResource(Res.string.featured_next), Modifier.size(18.dp), colors.textSecondary)
            }
        }
    }
}

@Composable
private fun FeaturedPlayButton(label: String, modifier: Modifier, onPlay: () -> Unit) {
    val pill = RoundedCornerShape(ParlorTheme.radii.pill)
    Row(
        modifier = modifier.widthIn(max = 240.dp).heightIn(min = ParlorTheme.spacing.xxl)
            .clip(pill).background(AmberActionBrush)
            .clickable(role = Role.Button, onClick = onPlay)
            .padding(horizontal = ParlorTheme.spacing.l, vertical = ParlorTheme.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Icon(ParlorIcons.Play, contentDescription = null, tint = AmberActionInk, modifier = Modifier.size(13.dp))
        LibraryText(
            text = label,
            style = ParlorTheme.typography.bodySmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 14.sp),
            color = AmberActionInk,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FeaturedCover(presentation: GameCatalogPresentation, players: IntRange) {
    val colors = ParlorTheme.colors
    val playerLabel = playerRangeLabel(players)
    val badge = presentation.genre?.let { stringResource(Res.string.catalog_players_and_genre, it, playerLabel) } ?: playerLabel
    Box(Modifier.fillMaxWidth().heightIn(min = 194.dp)) {
        Box(Modifier.matchParentSize().clipToBounds()) {
            CatalogArtworkBadge(presentation.artwork, Modifier.matchParentSize().offset(y = (-8).dp))
        }
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to colors.coverScreen.copy(alpha = 0.28f),
                    FEATURED_SCRIM_CLEAR_STOP to colors.transparent,
                    FEATURED_SCRIM_FADE_STOP to colors.coverScreen.copy(alpha = 0.84f),
                    1f to if (colors.isLight) colors.coverScreen else colors.surfaceElevated,
                ),
            ),
        )
        Column(Modifier.align(Alignment.TopStart).padding(ParlorTheme.spacing.m)) {
            val headline = ParlorTheme.typography.headingMedium.copy(fontSize = 15.sp, lineHeight = 17.sp)
            LibraryText(stringResource(Res.string.featured_top), headline, colors.coverScreenTextPrimary)
            LibraryText(stringResource(Res.string.featured_accent), headline, CozyNoirPalette.accentEmberGlow)
        }
        val badgeShape = RoundedCornerShape(ParlorTheme.radii.pill)
        LibraryText(
            text = badge,
            style = ParlorTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold),
            color = colors.coverScreenTextPrimary,
            modifier = Modifier.align(Alignment.TopEnd).padding(14.dp)
                .background(colors.coverScreen.copy(alpha = HERO_BADGE_ALPHA), badgeShape)
                .border(1.dp, colors.coverScreenTextSecondary.copy(alpha = 0.25f), badgeShape)
                .padding(horizontal = 9.dp, vertical = 4.dp),
        )
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(min = 50.dp).clipToBounds(),
        ) {
            CatalogArtworkBadge(presentation.artwork, Modifier.matchParentSize().blur(12.dp))
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        listOf(
                            colors.coverScreen.copy(alpha = HERO_GLASS_TOP_ALPHA),
                            if (colors.isLight) colors.coverScreen else colors.surfaceElevated,
                        ),
                    ),
                ),
            )
        }
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(ParlorTheme.spacing.s),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LibraryText(
                text = presentation.title,
                style = ParlorTheme.typography.headingLarge.copy(fontSize = 21.sp, fontWeight = FontWeight.ExtraBold),
                color = colors.coverScreenTextPrimary,
            )
            LibraryText(
                text = presentation.tagline,
                style = ParlorTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.coverScreenTextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private const val FEATURED_SCRIM_CLEAR_STOP = 0.43f
private const val PLAY_GLOW_ALPHA = 0.44f
private const val HERO_GLASS_TOP_ALPHA = 0.78f
private const val PLAY_HALO_Y = 0.62f
private const val PLAY_HALO_RADIUS = 0.35f
private const val HERO_BADGE_ALPHA = 0.65f
private const val FEATURED_SCRIM_FADE_STOP = 0.8f

private const val CHEVRON_REVERSE_DEGREES = 180f
