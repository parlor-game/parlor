package com.parlor.app.shell.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.parlor.app.resources.settings_language_help
import com.parlor.app.resources.settings_appearance_help
import com.parlor.app.resources.settings_accessibility_help
import com.parlor.app.resources.Res
import com.parlor.app.resources.settings_appearance_dark
import com.parlor.app.resources.settings_appearance_label
import com.parlor.app.resources.settings_appearance_light
import com.parlor.app.resources.settings_appearance_system
import com.parlor.app.resources.settings_experience_label
import com.parlor.app.resources.settings_eyebrow
import com.parlor.app.resources.settings_language_arabic
import com.parlor.app.resources.settings_language_english
import com.parlor.app.resources.settings_language_label
import com.parlor.app.resources.settings_language_system
import com.parlor.app.resources.settings_reduced_motion_description
import com.parlor.app.resources.settings_reduced_motion_title
import com.parlor.app.resources.settings_save_failed
import com.parlor.app.resources.settings_subtitle
import com.parlor.app.resources.settings_title
import com.parlor.designsystem.components.LibrarySurface
import com.parlor.designsystem.components.LocalParlorToastState
import com.parlor.designsystem.components.EyebrowLabel
import com.parlor.designsystem.components.ParlorCard
import com.parlor.designsystem.components.ParlorToastSeverity
import com.parlor.designsystem.components.ScreenHeader
import com.parlor.designsystem.components.parlorSafeContentPadding
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.designsystem.theme.ThemeMode
import com.parlor.designsystem.tokens.AmberActionBrush
import com.parlor.designsystem.tokens.AmberActionInk
import androidx.compose.ui.graphics.Color
import com.parlor.storage.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Settings — language, appearance, and accessibility controls backed by
 * shipping behavior. Writes run in the app-owned [mutationScope], so leaving
 * this screen cannot cancel an accepted choice. The running UI updates in
 * place because `App.kt` collects the same [SettingsStore] flows and wires
 * them to [ParlorTheme] + `ProvideAppLanguage`.
 *
 * The screen inherits shared theme tokens and introduces no bespoke colors,
 * while accepted mutations outlive this screen's composition.
 */
@Composable
fun SettingsScreen(
    mutationScope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val settings: SettingsStore = koinInject()
    val toastState = LocalParlorToastState.current
    val saveFailedText = stringResource(Res.string.settings_save_failed)
    val mutations = remember(mutationScope, toastState, saveFailedText) {
        SettingsMutationDispatcher(mutationScope) {
            toastState.show(saveFailedText, ParlorToastSeverity.Danger)
        }
    }

    val languageTag by settings.languageOverride.collectAsState(initial = null)
    val themeModeTag by settings.themeMode.collectAsState(initial = ThemeMode.Default.tag)
    val reducedMotion by settings.reducedMotion.collectAsState(initial = false)

    val currentLanguage = languageTag?.let(AppLanguage::fromTag)
    val currentThemeMode = ThemeMode.fromTag(themeModeTag)

    LibrarySurface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .parlorSafeContentPadding(ParlorTheme.spacing.m),
            verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
        ) {
            SettingsHeading()

            SettingsSection(
                label = stringResource(Res.string.settings_language_label),
                description = stringResource(Res.string.settings_language_help),
                selectableGroup = true,
            ) {
                SettingsChoices(
                    listOf(
                        SettingsChoice(
                            label = stringResource(Res.string.settings_language_system),
                            selected = currentLanguage == null,
                            compactLabel = stringResource(Res.string.settings_appearance_system),
                        ) {
                            mutations.submit { settings.setLanguageOverride(null) }
                        },
                        SettingsChoice(stringResource(Res.string.settings_language_english), currentLanguage == AppLanguage.English) {
                            mutations.submit { settings.setLanguageOverride(AppLanguage.English.tag) }
                        },
                        SettingsChoice(stringResource(Res.string.settings_language_arabic), currentLanguage == AppLanguage.Arabic) {
                            mutations.submit { settings.setLanguageOverride(AppLanguage.Arabic.tag) }
                        },
                    ),
                )
            }

            SettingsSection(
                label = stringResource(Res.string.settings_appearance_label),
                description = stringResource(Res.string.settings_appearance_help),
                selectableGroup = true,
            ) {
                SettingsChoices(
                    listOf(
                        SettingsChoice(stringResource(Res.string.settings_appearance_system), currentThemeMode == ThemeMode.System) {
                            mutations.submit { settings.setThemeMode(ThemeMode.System.tag) }
                        },
                        SettingsChoice(stringResource(Res.string.settings_appearance_light), currentThemeMode == ThemeMode.Light) {
                            mutations.submit { settings.setThemeMode(ThemeMode.Light.tag) }
                        },
                        SettingsChoice(stringResource(Res.string.settings_appearance_dark), currentThemeMode == ThemeMode.Dark) {
                            mutations.submit { settings.setThemeMode(ThemeMode.Dark.tag) }
                        },
                    ),
                )
            }

            SettingsSection(
                label = stringResource(Res.string.settings_experience_label),
                description = if (LocalDensity.current.fontScale >= SETTINGS_STACKED_FONT_SCALE) {
                    stringResource(Res.string.settings_accessibility_help)
                } else null,
            ) {
                ToggleOption(
                    title = stringResource(Res.string.settings_reduced_motion_title),
                    description = stringResource(Res.string.settings_reduced_motion_description),
                    checked = reducedMotion,
                    onCheckedChange = { enabled ->
                        mutations.submit { settings.setReducedMotion(enabled) }
                    },
                )
            }

        }
    }
}

@Composable
private fun SettingsHeading() {
    val title = stringResource(Res.string.settings_title)
    val summary = stringResource(Res.string.settings_subtitle)
    if (LocalDensity.current.fontScale >= SETTINGS_STACKED_FONT_SCALE) {
        ScreenHeader(title = title, eyebrow = stringResource(Res.string.settings_eyebrow), subtitle = summary)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xs)) {
            Text(
                text = title, style = ParlorTheme.typography.headingMedium, color = ParlorTheme.colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(text = summary, style = ParlorTheme.typography.bodySmall, color = ParlorTheme.colors.textSecondary)
        }
    }
}

@Composable
private fun SettingsSection(
    label: String,
    description: String?,
    selectableGroup: Boolean = false,
    content: @Composable () -> Unit,
) {
    val largeText = LocalDensity.current.fontScale >= SETTINGS_STACKED_FONT_SCALE
    ParlorCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = ParlorTheme.radii.card,
        contentPadding = if (largeText) ParlorTheme.spacing.l else ParlorTheme.spacing.m,
    ) {
        Column(
            modifier = if (selectableGroup) Modifier.selectableGroup() else Modifier,
            verticalArrangement = Arrangement.spacedBy(if (largeText) ParlorTheme.spacing.m else ParlorTheme.spacing.s),
        ) {
            if (largeText) {
                EyebrowLabel(text = label, accent = false, modifier = Modifier.semantics { heading() })
            } else {
                Text(
                    text = label, style = ParlorTheme.typography.labelLarge, color = ParlorTheme.colors.textPrimary,
                    modifier = Modifier.semantics { heading() },
                )
            }
            if (description != null) {
                Text(
                    text = description,
                    style = if (largeText) ParlorTheme.typography.bodyMedium else ParlorTheme.typography.bodySmall,
                    color = ParlorTheme.colors.textSecondary,
                )
            }
            content()
        }
    }
}

private data class SettingsChoice(
    val label: String,
    val selected: Boolean,
    val compactLabel: String = label,
    val select: () -> Unit,
)

@Composable
private fun SettingsChoices(options: List<SettingsChoice>) {
    val colors = ParlorTheme.colors
    val shape = RoundedCornerShape(ParlorTheme.radii.subtle)
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < SegmentsMinimumWidth || fontScale >= SETTINGS_STACKED_FONT_SCALE) {
            Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xs)) {
                options.forEach { OptionRow(it.label, it.selected, it.select) }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().clip(shape).background(colors.surfaceHigher)
                    .border(ParlorTheme.borders.hairline, colors.borderSubtle, shape).padding(ParlorTheme.spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xs),
            ) {
                options.forEach { option ->
                    Box(
                        modifier = Modifier.weight(1f).heightIn(min = ParlorTheme.spacing.xxl)
                            .clip(shape)
                            .selectable(selected = option.selected, role = Role.RadioButton, onClick = option.select),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp).clip(shape)
                                .then(if (option.selected) Modifier.background(AmberActionBrush) else Modifier)
                                .padding(horizontal = ParlorTheme.spacing.xs),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = option.compactLabel,
                                style = ParlorTheme.typography.labelMedium,
                                color = if (option.selected) AmberActionInk else colors.textSecondary,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleOption(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = ParlorTheme.colors
    val fontScale = LocalDensity.current.fontScale
    val enlarged = fontScale >= SETTINGS_STACKED_FONT_SCALE
    val shape = RoundedCornerShape(ParlorTheme.radii.card)
    val container = if (enlarged) {
        Modifier.clip(shape).background(colors.surfaceHigher)
            .border(ParlorTheme.borders.hairline, colors.borderElevated, shape)
    } else Modifier
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ParlorTheme.spacing.xxl)
            .then(container)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(
                horizontal = if (enlarged) ParlorTheme.spacing.l else 0.dp,
                vertical = if (enlarged) ParlorTheme.spacing.m else ParlorTheme.spacing.xs,
            ),
    ) {
        // A fixed switch must not squeeze enlarged text into a narrow column.
        // Keep one whole-card toggle action; the visual switch is not a second target.
        if (maxWidth / fontScale < ToggleRowMinimumWidth) {
            Column(verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m)) {
                ToggleText(title, description, Modifier.fillMaxWidth())
                SettingsSwitchTrack(checked, Modifier.align(Alignment.End))
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToggleText(title, description, Modifier.weight(1f))
                SettingsSwitchTrack(checked)
            }
        }
    }
}

@Composable
private fun SettingsSwitchTrack(checked: Boolean, modifier: Modifier = Modifier) {
    val colors = ParlorTheme.colors
    Box(
        modifier = modifier.size(width = 46.dp, height = 26.dp).clip(CircleShape)
            .then(if (checked) Modifier.background(AmberActionBrush) else Modifier.background(colors.surfaceHigher))
            .border(ParlorTheme.borders.hairline, colors.borderElevated, CircleShape)
            .padding(horizontal = ParlorTheme.spacing.xs),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(18.dp).background(Color.White, CircleShape))
    }
}

@Composable
private fun ToggleText(title: String, description: String, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(ParlorTheme.spacing.xs)) {
        Text(text = title, style = ParlorTheme.typography.bodyLarge, color = ParlorTheme.colors.textPrimary)
        Text(text = description, style = ParlorTheme.typography.bodySmall, color = ParlorTheme.colors.textSecondary)
    }
}

@Composable
private fun OptionRow(
    label: String,
    selected: Boolean,
    onSelected: () -> Unit,
) {
    val colors = ParlorTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ParlorTheme.radii.card))
            .background(colors.surfaceHigher)
            .background(
                if (selected) colors.accentEmber.copy(alpha = SELECTED_SURFACE_ALPHA)
                else colors.transparent,
            )
            .border(
                ParlorTheme.borders.hairline,
                if (selected) colors.accentEmber else colors.borderElevated,
                RoundedCornerShape(ParlorTheme.radii.card),
            )
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelected,
            )
            .padding(horizontal = ParlorTheme.spacing.l, vertical = ParlorTheme.spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = ParlorTheme.typography.bodyLarge,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        RadioButton(selected = selected, onClick = null)
    }
}

private const val SELECTED_SURFACE_ALPHA = 0.12f

private val ToggleRowMinimumWidth = 240.dp

private const val SETTINGS_STACKED_FONT_SCALE = 1.4f
private val SegmentsMinimumWidth = 252.dp
