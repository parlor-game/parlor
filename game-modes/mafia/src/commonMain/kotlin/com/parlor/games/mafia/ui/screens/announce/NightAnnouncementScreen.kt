package com.parlor.games.mafia.ui.screens.announce

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.parlor.designsystem.backdrop.HeroBackdrop
import com.parlor.designsystem.components.CatalogArtworkBadge
import com.parlor.designsystem.components.ContextRibbon
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.components.ParlorContextTone
import com.parlor.designsystem.components.parlorSafeContentPadding
import com.parlor.designsystem.icons.ParlorArtwork
import com.parlor.designsystem.localization.asBidiArgument
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.mafia.domain.state.Role
import com.parlor.games.mafia.resources.Res
import com.parlor.games.mafia.resources.night_announcement_continue
import com.parlor.games.mafia.resources.night_announcement_continue_description
import com.parlor.games.mafia.resources.night_announcement_eyebrow_format
import com.parlor.games.mafia.resources.night_announcement_killed_format
import com.parlor.games.mafia.resources.night_announcement_no_kill
import com.parlor.games.mafia.resources.night_announcement_public
import com.parlor.games.mafia.resources.night_announcement_revealed_role_format
import com.parlor.games.mafia.resources.night_announcement_saved
import com.parlor.games.mafia.ui.screens.reveal.roleDisplayName
import org.jetbrains.compose.resources.stringResource

/**
 * Public announcement of last night's outcome. Shown together to everyone:
 * the victim's name (or "no one died") and, only if `revealRoleOnDeath`,
 * their role.
 *
 * The Detective's private inspection result is delivered SEPARATELY on a
 * private handoff segment — it must NEVER appear here.
 */
@Composable
fun NightAnnouncementScreen(
    day: Int,
    killedPlayerName: String?,
    revealedRole: Role?,
    wasSaved: Boolean,
    onAcknowledged: (() -> Unit)?,
    waitingLabel: String? = null,
    modifier: Modifier = Modifier,
) {
    val compactTypography = LocalDensity.current.fontScale >= COMPACT_FONT_SCALE_LIMIT
    val headlineColor = when {
        killedPlayerName != null -> ParlorTheme.colors.semanticDanger
        wasSaved -> ParlorTheme.colors.semanticSuccess
        else -> ParlorTheme.colors.textPrimary
    }
    HeroBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .parlorSafeContentPadding(ParlorTheme.spacing.m),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ContextRibbon(
                label = stringResource(Res.string.night_announcement_eyebrow_format, day),
                detail = stringResource(Res.string.night_announcement_public),
                tone = ParlorContextTone.Public,
            )
            ParlorCard(modifier = Modifier.fillMaxWidth(), hero = true) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (!compactTypography) {
                        CatalogArtworkBadge(
                            artwork = ParlorArtwork.Moon,
                            modifier = Modifier.size(ParlorTheme.spacing.xxxl),
                        )
                    }
                    val headline = when {
                        killedPlayerName != null -> stringResource(
                            Res.string.night_announcement_killed_format,
                            killedPlayerName.asBidiArgument(),
                        )
                        wasSaved -> stringResource(Res.string.night_announcement_saved)
                        else -> stringResource(Res.string.night_announcement_no_kill)
                    }
                    Text(
                        text = headline,
                        style = ParlorTheme.typography.displayMedium,
                        color = headlineColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (killedPlayerName != null && revealedRole != null) {
                        Text(
                            text = stringResource(
                                Res.string.night_announcement_revealed_role_format,
                                roleDisplayName(revealedRole),
                            ),
                            style = ParlorTheme.typography.bodyLarge,
                            color = ParlorTheme.colors.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            if (onAcknowledged != null) {
                ParlorButton(
                    label = stringResource(Res.string.night_announcement_continue),
                    contentDescription = stringResource(
                        Res.string.night_announcement_continue_description,
                    ),
                    onClick = onAcknowledged,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (waitingLabel != null) {
                Text(
                    text = waitingLabel,
                    style = ParlorTheme.typography.bodyMedium,
                    color = ParlorTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private const val COMPACT_FONT_SCALE_LIMIT = 1.3f
