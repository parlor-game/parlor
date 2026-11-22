package com.parlor.app.shell.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.parlor.app.resources.Res
import com.parlor.app.resources.navigation_profile_description
import com.parlor.app.resources.profile_clear
import com.parlor.app.resources.profile_display_name
import com.parlor.app.resources.profile_name_help
import com.parlor.app.resources.profile_save
import com.parlor.app.resources.profile_saved
import com.parlor.app.resources.profile_subtitle
import com.parlor.app.resources.profile_title
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
import com.parlor.designsystem.components.ScreenHeader
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
    ParlorIconButton(
        icon = ParlorIcons.Profile,
        contentDescription = stringResource(Res.string.navigation_profile_description),
        onClick = onClick,
    )
}

@Composable
internal fun ProfileScreen(settings: SettingsStore, scope: CoroutineScope, modifier: Modifier = Modifier) {
    val saved by settings.preferredDisplayName.collectAsState(initial = LocalPreferredDisplayName.current)
    // A profile name stays in process memory while editing and is saved only by explicit action.
    var draft by remember(saved) { mutableStateOf(saved.orEmpty()) }
    val normalized = DisplayNamePolicy.normalizeDisplayName(draft)
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
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
        ) {
            ScreenHeader(
                title = stringResource(Res.string.profile_title),
                subtitle = stringResource(Res.string.profile_subtitle),
            )
            ParlorCard(contentPadding = ParlorTheme.spacing.m) {
                Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m)) {
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
                    val saveLabel = stringResource(Res.string.profile_save)
                    ParlorButton(
                        label = saveLabel,
                        contentDescription = saveLabel,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = normalized != saved && DisplayNamePolicy.isValidDisplayName(normalized),
                        illuminated = true,
                        onClick = {
                            focusManager.clearFocus()
                            mutations.submit {
                                settings.setPreferredDisplayName(normalized)
                                toast.show(success)
                            }
                        },
                    )
                    if (saved != null) {
                        val clearLabel = stringResource(Res.string.profile_clear)
                        ParlorButton(
                            label = clearLabel,
                            contentDescription = clearLabel,
                            modifier = Modifier.fillMaxWidth(),
                            variant = ParlorButtonVariant.Secondary,
                            onClick = {
                                focusManager.clearFocus()
                                mutations.submit { settings.setPreferredDisplayName(null) }
                            },
                        )
                    }
                }
            }
        }
    }
}
