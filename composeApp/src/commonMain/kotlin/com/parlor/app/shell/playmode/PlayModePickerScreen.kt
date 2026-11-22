package com.parlor.app.shell.playmode

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.parlor.designsystem.components.LibraryText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.parlor.app.resources.Res
import com.parlor.app.resources.playmode_passandplay_body
import com.parlor.app.resources.playmode_passandplay_choose_description
import com.parlor.app.resources.playmode_passandplay_title
import com.parlor.app.resources.playmode_solo_body
import com.parlor.app.resources.playmode_solo_choose_description
import com.parlor.app.resources.playmode_solo_title
import com.parlor.app.resources.setup_back_description
import com.parlor.app.resources.setup_each_device_label
import com.parlor.app.resources.setup_eyebrow
import com.parlor.app.resources.setup_host_body
import com.parlor.app.resources.setup_host_choose_description
import com.parlor.app.resources.setup_host_title
import com.parlor.app.resources.setup_join_body
import com.parlor.app.resources.setup_join_choose_description
import com.parlor.app.resources.setup_join_title
import com.parlor.app.resources.setup_lan_note
import com.parlor.app.resources.setup_one_device_label
import com.parlor.app.resources.setup_solo_requires_exact
import com.parlor.app.resources.setup_solo_requires_range
import com.parlor.app.resources.setup_solo_unavailable_description_format
import com.parlor.app.resources.setup_solo_unavailable_title
import com.parlor.app.shell.game.GameCatalogPresentation
import com.parlor.app.shell.game.GameEntryMode
import com.parlor.app.shell.game.GameShellCapabilities
import com.parlor.app.shell.home.playerRangeLabel
import com.parlor.designsystem.components.CatalogArtworkBadge
import com.parlor.designsystem.components.LibrarySurface
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.components.ParlorIconButton
import com.parlor.designsystem.components.libraryFocusOutline
import com.parlor.designsystem.components.parlorSafeContentPadding
import com.parlor.designsystem.icons.ParlorIcons
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.designsystem.tokens.AmberActionBrush
import com.parlor.designsystem.tokens.AmberActionInk
import com.parlor.session.PlayMode
import org.jetbrains.compose.resources.stringResource

internal data class PlayModePickerAvailability(
    val solo: Boolean,
    val passAndPlay: Boolean,
    val host: Boolean,
    val join: Boolean,
)

internal data class PlayModePickerModel(
    val availability: PlayModePickerAvailability,
    val supportedPlayerCounts: IntRange,
) {
    init {
        require(!supportedPlayerCounts.isEmpty()) {
            "Play-mode player bounds must not be empty"
        }
    }
}

internal fun GameShellCapabilities.toPlayModePickerAvailability() = PlayModePickerAvailability(
    solo = supports(GameEntryMode.Solo),
    passAndPlay = supports(GameEntryMode.PassAndPlay),
    host = supports(GameEntryMode.Host),
    join = supports(GameEntryMode.Join),
)

internal fun GameShellCapabilities.toPlayModePickerModel(
    supportedPlayerCounts: IntRange,
) = PlayModePickerModel(
    availability = toPlayModePickerAvailability(),
    supportedPlayerCounts = supportedPlayerCounts,
)

/** Registry-driven topology picker; presentation never invents a new play mode. */
@Composable
internal fun PlayModePickerScreen(
    onModeSelected: (PlayMode) -> Unit,
    onHost: () -> Unit,
    onJoin: () -> Unit,
    onBack: () -> Unit,
    presentation: GameCatalogPresentation,
    modifier: Modifier = Modifier,
    capabilities: GameShellCapabilities,
    supportedPlayerCounts: IntRange,
) {
    val model = capabilities.toPlayModePickerModel(supportedPlayerCounts)
    val availability = model.availability
    LibrarySurface(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.widthIn(max = PickerWidth).fillMaxSize()
                .verticalScroll(rememberScrollState())
                .parlorSafeContentPadding(ParlorTheme.spacing.m),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
        ) {
            SelectedGameHeader(presentation, model.supportedPlayerCounts, onBack)
            if (availability.solo || availability.passAndPlay) {
                LocalPlayOptions(model, onModeSelected)
            }
            if (availability.host || availability.join) {
                NearbyPlayOptions(availability, onHost, onJoin)
            }
        }
    }
}

@Composable
private fun SelectedGameHeader(presentation: GameCatalogPresentation, players: IntRange, onBack: () -> Unit) {
    val coverShape = RoundedCornerShape(ParlorTheme.radii.subtle)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m)) {
        ParlorIconButton(
            icon = ParlorIcons.Back,
            contentDescription = stringResource(Res.string.setup_back_description),
            onClick = onBack,
            modifier = Modifier.libraryFocusOutline(RoundedCornerShape(ParlorTheme.radii.pill)),
        )
        LibraryText(
            text = stringResource(Res.string.setup_eyebrow),
            style = ParlorTheme.typography.labelSmall,
            color = ParlorTheme.colors.textSecondary,
        )
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val largeText = LocalDensity.current.fontScale >= PICKER_LARGE_TYPE
        val caption: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xs)) {
                LibraryText(
                    text = presentation.title,
                    modifier = Modifier.semantics { heading() },
                    style = ParlorTheme.typography.displayMedium,
                    color = ParlorTheme.colors.textPrimary,
                )
                LibraryText(playerRangeLabel(players), style = ParlorTheme.typography.bodyMedium, color = ParlorTheme.colors.textSecondary)
            }
        }
        if (largeText && maxWidth < NarrowPickerWidth) {
            Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s)) {
                CatalogArtworkBadge(presentation.artwork, Modifier.size(ParlorTheme.spacing.xxl).clip(coverShape))
                caption()
            }
        } else {
            ParlorCard(modifier = Modifier.fillMaxWidth(), contentPadding = ParlorTheme.spacing.m) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
                ) {
                    CatalogArtworkBadge(presentation.artwork, Modifier.size(HeaderPrintSize).clip(coverShape))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xs),
                    ) {
                        caption()
                        if (!largeText) {
                            LibraryText(
                                text = presentation.tagline,
                                style = ParlorTheme.typography.bodySmall,
                                color = ParlorTheme.colors.textSecondary,
                                preferredLines = 2,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NearbyPlayOptions(availability: PlayModePickerAvailability, onHost: () -> Unit, onJoin: () -> Unit) {
    TopologyHeading(stringResource(Res.string.setup_each_device_label))
    ParlorCard(modifier = Modifier.fillMaxWidth(), contentPadding = ParlorTheme.spacing.s) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
        ) {
            ModeGlyph(SetupIcon.Host, Modifier.size(ParlorTheme.spacing.l))
            LibraryText(
                text = stringResource(Res.string.setup_lan_note),
                style = ParlorTheme.typography.bodySmall,
                color = ParlorTheme.colors.textSecondary,
            )
        }
    }
    if (availability.host) {
        SetupOptionCard(
            icon = SetupIcon.Host,
            title = stringResource(Res.string.setup_host_title),
            body = stringResource(Res.string.setup_host_body),
            contentDescription = stringResource(Res.string.setup_host_choose_description),
            onClick = onHost,
        )
    }
    if (availability.join) {
        SetupOptionCard(
            icon = SetupIcon.Join,
            title = stringResource(Res.string.setup_join_title),
            body = stringResource(Res.string.setup_join_body),
            contentDescription = stringResource(Res.string.setup_join_choose_description),
            onClick = onJoin,
        )
    }
}

@Composable
private fun LocalPlayOptions(model: PlayModePickerModel, onModeSelected: (PlayMode) -> Unit) {
    val availability = model.availability
    val inlineHint = !availability.solo && LocalDensity.current.fontScale < PICKER_LARGE_TYPE
    TopologyHeading(stringResource(Res.string.setup_one_device_label)) {
        if (inlineHint) SoloUnavailable(model.supportedPlayerCounts)
    }
    if (availability.solo) {
        SetupOptionCard(
            icon = SetupIcon.Solo,
            title = stringResource(Res.string.playmode_solo_title),
            body = stringResource(Res.string.playmode_solo_body),
            contentDescription = stringResource(Res.string.playmode_solo_choose_description),
            onClick = { onModeSelected(PlayMode.Solo) },
        )
    }
    if (availability.passAndPlay) {
        SetupOptionCard(
            icon = SetupIcon.PassAndPlay,
            title = stringResource(Res.string.playmode_passandplay_title),
            body = stringResource(Res.string.playmode_passandplay_body),
            contentDescription = stringResource(Res.string.playmode_passandplay_choose_description),
            onClick = { onModeSelected(PlayMode.PassAndPlay) },
        )
    }
    if (!availability.solo && !inlineHint) SoloUnavailable(model.supportedPlayerCounts)
}

@Composable
private fun SoloUnavailable(players: IntRange) {
    val explanation = stringResource(Res.string.setup_solo_unavailable_description_format, soloRequirement(players))
    LibraryText(
        text = stringResource(Res.string.setup_solo_unavailable_title),
        modifier = Modifier.semantics {
            disabled()
            contentDescription = explanation
        },
        style = ParlorTheme.typography.labelSmall,
        color = ParlorTheme.colors.textSecondary,
    )
}

@Composable
private fun TopologyHeading(text: String, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LibraryText(
            text = text,
            modifier = Modifier.weight(1f).semantics { heading() },
            style = ParlorTheme.typography.labelLarge,
            color = ParlorTheme.colors.textSecondary,
        )
        trailing()
    }
}

@Composable
private fun SetupOptionCard(
    icon: SetupIcon,
    title: String,
    body: String,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val colors = ParlorTheme.colors
    val emphasized = icon == SetupIcon.PassAndPlay
    val shape = RoundedCornerShape(ParlorTheme.radii.card)
    ParlorCard(
        modifier = Modifier.fillMaxWidth().clip(shape).libraryFocusOutline(shape)
            .then(if (emphasized) Modifier.border(ParlorTheme.borders.hairline, colors.accentEmber, shape) else Modifier)
            .semantics { this.contentDescription = contentDescription }
            .clickable(role = Role.Button, onClick = onClick),
        contentPadding = ParlorTheme.spacing.s,
    ) {
        val label: @Composable () -> Unit = {
            LibraryText(title, style = ParlorTheme.typography.headingMedium, color = ParlorTheme.colors.textPrimary)
        }
        val caption: @Composable () -> Unit = {
            LibraryText(body, style = ParlorTheme.typography.bodySmall, color = ParlorTheme.colors.textSecondary, preferredLines = 2)
        }
        if (LocalDensity.current.fontScale >= PICKER_LARGE_TYPE) {
            Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s)) {
                Row(horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s), verticalAlignment = Alignment.CenterVertically) {
                    ModeGlyph(icon, Modifier.size(ParlorTheme.spacing.xl))
                    label()
                }
                caption()
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m), verticalAlignment = Alignment.CenterVertically) {
                ModeGlyph(icon, Modifier.size(44.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xs)) {
                    label()
                    caption()
                }
                Icon(ParlorIcons.Forward, contentDescription = null, tint = colors.textTertiary)
            }
        }
    }
}

@Composable
private fun ModeGlyph(icon: SetupIcon, modifier: Modifier) {
    val emphasized = icon == SetupIcon.PassAndPlay
    val iconColor = if (emphasized) AmberActionInk else ParlorTheme.colors.accentEmber
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(ParlorTheme.radii.card))
            .then(if (emphasized) Modifier.background(AmberActionBrush) else Modifier.background(iconColor.copy(alpha = MODE_GLYPH_ALPHA)))
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(ParlorTheme.iconSize.l)) {
            val stroke = Stroke(width = size.minDimension * ICON_STROKE_RATIO)
            when (icon) {
                SetupIcon.Solo -> drawSoloGlyph(iconColor, stroke)
                SetupIcon.PassAndPlay -> drawPassAndPlayGlyph(iconColor, stroke)
                SetupIcon.Host -> drawHostGlyph(iconColor, stroke)
                SetupIcon.Join -> drawJoinGlyph(iconColor, stroke)
            }
        }
    }
}

private fun DrawScope.drawSoloGlyph(color: Color, stroke: Stroke) {
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width * SOLO_PHONE_LEFT, size.height * PHONE_TOP),
        size = Size(size.width * SOLO_PHONE_WIDTH, size.height * PHONE_HEIGHT),
        cornerRadius = CornerRadius(size.width * PHONE_CORNER_RADIUS),
        style = stroke,
    )
    drawCircle(color, radius = size.width * PHONE_HOME_RADIUS)
}

private fun DrawScope.drawPassAndPlayGlyph(color: Color, stroke: Stroke) {
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width * PASS_PHONE_LEFT, size.height * PHONE_TOP),
        size = Size(size.width * PASS_PHONE_WIDTH, size.height * PHONE_HEIGHT),
        cornerRadius = CornerRadius(size.width * PHONE_CORNER_RADIUS),
        style = stroke,
    )
    drawCircle(
        color = color,
        radius = size.width * PRIMARY_PERSON_RADIUS,
        center = Offset(size.width * PRIMARY_PERSON_X, size.height * PRIMARY_PERSON_Y),
    )
    drawCircle(
        color = color,
        radius = size.width * SECONDARY_PERSON_RADIUS,
        center = Offset(size.width * SECONDARY_PERSON_X, size.height * SECONDARY_PERSON_Y),
    )
}

private fun DrawScope.drawHostGlyph(color: Color, stroke: Stroke) {
    drawCircle(
        color = color,
        radius = size.width * HOST_NODE_RADIUS,
        center = Offset(size.width * HOST_NODE_X, size.height * HOST_NODE_Y),
    )
    drawArc(
        color = color,
        startAngle = HOST_ARC_START_ANGLE,
        sweepAngle = HOST_ARC_SWEEP_ANGLE,
        useCenter = false,
        topLeft = Offset(size.width * HOST_INNER_ARC_LEFT, size.height * HOST_INNER_ARC_TOP),
        size = Size(size.width * HOST_INNER_ARC_WIDTH, size.height * HOST_INNER_ARC_HEIGHT),
        style = stroke,
    )
    drawArc(
        color = color,
        startAngle = HOST_ARC_START_ANGLE,
        sweepAngle = HOST_ARC_SWEEP_ANGLE,
        useCenter = false,
        topLeft = Offset(size.width * HOST_OUTER_ARC_LEFT, size.height * HOST_OUTER_ARC_TOP),
        size = Size(size.width * HOST_OUTER_ARC_WIDTH, size.height * HOST_OUTER_ARC_HEIGHT),
        style = stroke,
    )
}

private fun DrawScope.drawJoinGlyph(color: Color, stroke: Stroke) {
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width * JOIN_LARGE_DEVICE_LEFT, size.height * JOIN_LARGE_DEVICE_TOP),
        size = Size(size.width * JOIN_LARGE_DEVICE_WIDTH, size.height * JOIN_LARGE_DEVICE_HEIGHT),
        cornerRadius = CornerRadius(size.width * JOIN_LARGE_DEVICE_CORNER_RADIUS),
        style = stroke,
    )
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width * JOIN_SMALL_DEVICE_LEFT, size.height * JOIN_SMALL_DEVICE_TOP),
        size = Size(size.width * JOIN_SMALL_DEVICE_WIDTH, size.height * JOIN_SMALL_DEVICE_HEIGHT),
        cornerRadius = CornerRadius(size.width * JOIN_SMALL_DEVICE_CORNER_RADIUS),
        style = stroke,
    )
}

@Composable
private fun soloRequirement(playerCounts: IntRange): String =
    if (playerCounts.first == playerCounts.last) {
        stringResource(
            Res.string.setup_solo_requires_exact,
            playerCounts.first,
        )
    } else {
        stringResource(
            Res.string.setup_solo_requires_range,
            playerCounts.first,
            playerCounts.last,
        )
    }

private const val PICKER_LARGE_TYPE = 1.5f
private val NarrowPickerWidth = 420.dp
private val HeaderPrintSize = 88.dp
private val PickerWidth = 560.dp

private const val MODE_GLYPH_ALPHA = 0.12f
private const val ICON_STROKE_RATIO = 0.07f
private const val PHONE_TOP = 0.08f
private const val PHONE_HEIGHT = 0.84f
private const val PHONE_CORNER_RADIUS = 0.10f
private const val PHONE_HOME_RADIUS = 0.05f
private const val SOLO_PHONE_LEFT = 0.22f
private const val SOLO_PHONE_WIDTH = 0.56f
private const val PASS_PHONE_LEFT = 0.30f
private const val PASS_PHONE_WIDTH = 0.48f
private const val PRIMARY_PERSON_RADIUS = 0.10f
private const val PRIMARY_PERSON_X = 0.23f
private const val PRIMARY_PERSON_Y = 0.42f
private const val SECONDARY_PERSON_RADIUS = 0.08f
private const val SECONDARY_PERSON_X = 0.18f
private const val SECONDARY_PERSON_Y = 0.66f
private const val HOST_NODE_RADIUS = 0.07f
private const val HOST_NODE_X = 0.50f
private const val HOST_NODE_Y = 0.76f
private const val HOST_ARC_START_ANGLE = 220f
private const val HOST_ARC_SWEEP_ANGLE = 100f
private const val HOST_INNER_ARC_LEFT = 0.28f
private const val HOST_INNER_ARC_TOP = 0.44f
private const val HOST_INNER_ARC_WIDTH = 0.44f
private const val HOST_INNER_ARC_HEIGHT = 0.34f
private const val HOST_OUTER_ARC_LEFT = 0.12f
private const val HOST_OUTER_ARC_TOP = 0.18f
private const val HOST_OUTER_ARC_WIDTH = 0.76f
private const val HOST_OUTER_ARC_HEIGHT = 0.60f
private const val JOIN_LARGE_DEVICE_LEFT = 0.08f
private const val JOIN_LARGE_DEVICE_TOP = 0.18f
private const val JOIN_LARGE_DEVICE_WIDTH = 0.52f
private const val JOIN_LARGE_DEVICE_HEIGHT = 0.68f
private const val JOIN_LARGE_DEVICE_CORNER_RADIUS = 0.09f
private const val JOIN_SMALL_DEVICE_LEFT = 0.62f
private const val JOIN_SMALL_DEVICE_TOP = 0.30f
private const val JOIN_SMALL_DEVICE_WIDTH = 0.30f
private const val JOIN_SMALL_DEVICE_HEIGHT = 0.48f
private const val JOIN_SMALL_DEVICE_CORNER_RADIUS = 0.07f

private enum class SetupIcon { Solo, PassAndPlay, Host, Join }
