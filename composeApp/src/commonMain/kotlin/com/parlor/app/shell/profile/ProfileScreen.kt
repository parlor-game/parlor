package com.parlor.app.shell.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parlor.app.resources.Res
import com.parlor.app.resources.home_guest
import com.parlor.app.resources.home_profile_greeting
import com.parlor.app.resources.navigation_profile_description
import com.parlor.app.resources.profile_cancel
import com.parlor.app.resources.profile_clear
import com.parlor.app.resources.profile_display_name
import com.parlor.app.resources.profile_edit
import com.parlor.app.resources.profile_guest_help
import com.parlor.app.resources.profile_name_help
import com.parlor.app.resources.profile_save
import com.parlor.app.resources.profile_saved
import com.parlor.app.resources.profile_subtitle
import com.parlor.app.resources.settings_save_failed
import com.parlor.app.shell.settings.SettingsMutationDispatcher
import com.parlor.core.text.DisplayNamePolicy
import com.parlor.designsystem.components.LibrarySurface
import com.parlor.designsystem.components.LibraryText
import com.parlor.designsystem.components.LocalParlorToastState
import com.parlor.designsystem.components.ParlorButton
import com.parlor.designsystem.components.ParlorButtonVariant
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.components.ParlorIconButton
import com.parlor.designsystem.components.ParlorTextField
import com.parlor.designsystem.components.ParlorToastSeverity
import com.parlor.designsystem.components.parlorSafeContentPadding
import com.parlor.designsystem.icons.ParlorIcons
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.storage.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.compose.resources.stringResource

/** A device preference for the editable seat label, independent of authenticated room identity. */
internal val LocalPreferredDisplayName = staticCompositionLocalOf<String?> { null }

@Composable
internal fun OpenProfileButton(onClick: () -> Unit) {
    val saved = LocalPreferredDisplayName.current != null
    ParlorIconButton(
        icon = if (saved) ParlorIcons.Lantern else ParlorIcons.Profile,
        contentDescription = stringResource(Res.string.navigation_profile_description),
        onClick = onClick,
        tint = ParlorTheme.colors.accentEmber.takeIf { saved },
    )
}

@Composable
internal fun ProfileScreen(settings: SettingsStore, scope: CoroutineScope, modifier: Modifier = Modifier) {
    val saved by settings.preferredDisplayName.collectAsState()
    var editing by remember { mutableStateOf(saved == null) }
    val guest = stringResource(Res.string.home_guest)
    val caption = stringResource(if (saved == null) Res.string.profile_guest_help else Res.string.profile_subtitle)
    val focusManager = LocalFocusManager.current
    val toast = LocalParlorToastState.current
    val failed = stringResource(Res.string.settings_save_failed)
    val success = stringResource(Res.string.profile_saved)
    val mutations = remember(scope, toast, failed) {
        SettingsMutationDispatcher(scope) { toast.show(failed, ParlorToastSeverity.Danger) }
    }

    LibrarySurface(modifier.fillMaxSize(), litHeader = false) {
        Column(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxSize()
                .verticalScroll(rememberScrollState()).parlorSafeContentPadding(ParlorTheme.spacing.m),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ProfileGreeting(saved ?: guest, Modifier.semantics { heading() })
                LibraryText(caption, ParlorTheme.typography.bodySmall, ParlorTheme.colors.textSecondary)
            }
            SeatIdentityCard(saved ?: guest, caption, emblemSize = 54.dp) {
                if (!editing) {
                    TextButton(onClick = { editing = true }) {
                        LibraryText(
                            stringResource(Res.string.profile_edit),
                            ParlorTheme.typography.labelMedium,
                            ParlorTheme.colors.accentEmber,
                        )
                    }
                }
            }
            if (editing) {
                ProfileNameEditor(
                    saved = saved,
                    onSave = { name ->
                        focusManager.clearFocus()
                        mutations.submit {
                            settings.setPreferredDisplayName(name)
                            editing = false
                            toast.show(success)
                        }
                    },
                    onCancel = { editing = false },
                )
            } else {
                LibraryText(
                    stringResource(Res.string.profile_name_help),
                    ParlorTheme.typography.bodySmall,
                    ParlorTheme.colors.textSecondary,
                )
                val clearLabel = stringResource(Res.string.profile_clear)
                ParlorButton(
                    label = clearLabel,
                    contentDescription = clearLabel,
                    modifier = Modifier.fillMaxWidth(),
                    variant = ParlorButtonVariant.Secondary,
                    onClick = {
                        mutations.submit {
                            settings.setPreferredDisplayName(null)
                            editing = true
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ProfileNameEditor(saved: String?, onSave: (String) -> Unit, onCancel: () -> Unit) {
    // Draft text remains in process memory until explicitly saved.
    var draft by remember(saved) { mutableStateOf(saved.orEmpty()) }
    val normalized = DisplayNamePolicy.normalizeDisplayName(draft)
    ParlorCard(contentPadding = ParlorTheme.spacing.m) {
        Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.s)) {
            ParlorTextField(
                value = draft,
                onValueChange = { draft = DisplayNamePolicy.sanitizeDisplayNameInput(it) },
                label = stringResource(Res.string.profile_display_name),
            )
            LibraryText(
                text = stringResource(Res.string.profile_name_help),
                style = ParlorTheme.typography.bodySmall,
                color = ParlorTheme.colors.textSecondary,
            )
            val save = stringResource(Res.string.profile_save)
            ParlorButton(
                label = save,
                contentDescription = save,
                modifier = Modifier.fillMaxWidth(),
                enabled = normalized != saved && DisplayNamePolicy.isValidDisplayName(normalized),
                illuminated = true,
                onClick = { onSave(normalized) },
            )
            if (saved != null) {
                val cancel = stringResource(Res.string.profile_cancel)
                ParlorButton(
                    label = cancel,
                    contentDescription = cancel,
                    modifier = Modifier.fillMaxWidth(),
                    variant = ParlorButtonVariant.Secondary,
                    onClick = onCancel,
                )
            }
        }
    }
}

@Composable
internal fun ProfileGreeting(name: String, modifier: Modifier = Modifier) {
    val template = stringResource(Res.string.home_profile_greeting, GREETING_NAME_PLACEHOLDER)
    val colors = ParlorTheme.colors
    val greeting = buildAnnotatedString {
        append(template.substringBefore(GREETING_NAME_PLACEHOLDER))
        withStyle(SpanStyle(color = colors.accentEmber)) { append(name) }
        append(template.substringAfter(GREETING_NAME_PLACEHOLDER, missingDelimiterValue = ""))
    }
    Text(
        text = greeting,
        style = ParlorTheme.typography.headingMedium.copy(
            fontSize = 17.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.Bold,
        ),
        color = colors.textPrimary,
        modifier = modifier,
    )
}

private const val GREETING_NAME_PLACEHOLDER = "\uFFF0"

/** A public display label. The emblem is decorative and independent of room identity. */
@Composable
internal fun SeatIdentityCard(
    name: String,
    caption: String,
    modifier: Modifier = Modifier,
    emblemSize: Dp = 32.dp,
    trailing: @Composable () -> Unit = {},
) {
    val colors = ParlorTheme.colors
    ParlorCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = ParlorTheme.spacing.s,
        cornerRadius = ParlorTheme.radii.card,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(emblemSize).background(colors.accentEmber.copy(alpha = 0.13f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(ParlorIcons.Lantern, null, Modifier.size(emblemSize / 2), tint = colors.accentEmber)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                LibraryText(name, ParlorTheme.typography.labelLarge, colors.textPrimary)
                LibraryText(caption, ParlorTheme.typography.bodySmall.copy(fontSize = 11.sp), colors.textSecondary)
            }
            trailing()
        }
    }
}
