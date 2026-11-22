package com.parlor.app.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.parlor.app.AppNavigationBar
import com.parlor.app.AppTopLevelDestination
import com.parlor.app.permissions.P2pPermissionGate
import com.parlor.app.permissions.P2pPermissionRationaleScreen
import com.parlor.app.permissions.PermissionStatus
import com.parlor.app.resources.Res
import com.parlor.app.resources.room_game_host
import com.parlor.app.resources.room_game_opening
import com.parlor.app.resources.setup_host_title
import com.parlor.app.shell.game.DominoGameShellBinding
import com.parlor.app.shell.game.multiplayer.GameHostSetupCheckpoint
import com.parlor.app.shell.game.multiplayer.HostRoomControls
import com.parlor.app.shell.game.multiplayer.RoomGameStatus
import com.parlor.app.shell.game.multiplayer.kind
import com.parlor.app.shell.multiplayer.JoinPromptScreen
import com.parlor.app.shell.multiplayer.NameInputScreen
import com.parlor.app.shell.profile.LocalPreferredDisplayName
import com.parlor.app.shell.profile.ProfileScreen
import com.parlor.app.shell.settings.SettingsScreen
import com.parlor.core.ids.PlayerId
import com.parlor.designsystem.components.LocalParlorToastState
import com.parlor.designsystem.components.ParlorToastState
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.localization.asBidiArgument
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.designsystem.theme.ThemeMode
import com.parlor.games.dominoes.DominoDefinition
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.PendingAdmission
import com.parlor.networking.room.RoomInfo
import com.parlor.networking.room.RoomMember
import com.parlor.networking.testing.InMemoryHostRoom
import com.parlor.networking.testing.InMemoryRoomBus
import com.parlor.storage.settings.InMemorySettingsStore
import com.parlor.storage.settings.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.stringResource
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.Test

/** Composes production surfaces with public comparison fixtures only. */
@OptIn(ExperimentalTestApi::class)
class NeonShellVisualEvidenceTest {
    @Test
    fun capture_shared_shell_in_english_and_arabic_with_both_themes() {
        if (System.getenv("PARLOR_UI_SCREENSHOT_DIR").isNullOrBlank()) return
        for (language in listOf(AppLanguage.English, AppLanguage.Arabic)) {
            for (theme in listOf(ThemeMode.Dark, ThemeMode.Light)) {
                capture(language, theme)
            }
        }
    }

    private fun capture(language: AppLanguage, theme: ThemeMode) {
        val english = language == AppLanguage.English
        val name = if (english) "Layla" else "ليلى"
        val settings = InMemorySettingsStore(
            initialLanguageOverride = language.tag,
            initialThemeMode = theme.tag,
            initialPreferredDisplayName = name,
        )
        val gate = object : P2pPermissionGate {
            override val status = MutableStateFlow<PermissionStatus>(PermissionStatus.Unknown)
            override val canOpenNetworkSettings = true
            override suspend fun request() = PermissionStatus.Unknown
            override fun openAppSettings() = Unit
        }
        val spec = DominoGameShellBinding(DominoDefinition())
        val host = PlayerId("fixture-host")
        val room = object : LocalRoom by InMemoryHostRoom(InMemoryRoomBus(), host, name) {
            override val info = MutableStateFlow(RoomInfo("WX2Y3Z", name, host, RoomInfo.Status.Hosting))
            override val pendingAdmissions = MutableStateFlow(
                listOf(
                    PendingAdmission(PlayerId("fixture-guest-1"), if (english) "Sara" else "سارة", isRejoin = false),
                    PendingAdmission(PlayerId("fixture-guest-2"), if (english) "Mazen" else "مازن", isRejoin = false),
                ),
            )
            override val members = MutableStateFlow(
                listOf(
                    RoomMember(PlayerId("fixture-seat-1"), if (english) "Tarek" else "طارق", true),
                    RoomMember(PlayerId("fixture-seat-2"), if (english) "Salma" else "سلمى", true),
                ),
            )
        }
        val setup = GameHostSetupCheckpoint(spec.kind("setup"), spec.defaultCaseId)
        val screen = mutableStateOf("settings")
        val suffix = "${language.tag}-${theme.tag}-390"
        startKoin { modules(module { single<SettingsStore> { settings } }) }
        try {
            runComposeUiTest {
                setContent {
                    CompositionLocalProvider(
                        LocalDensity provides Density(1f, 1f),
                        LocalPreferredDisplayName provides name,
                        LocalParlorToastState provides ParlorToastState(),
                    ) {
                        ProvideAppLanguage(language) {
                            ParlorTheme(themeMode = theme, reducedMotion = true) {
                                Box(Modifier.size(390.dp, 740.dp).testTag("acceptance-viewport")) {
                                    val gameTitle = if (english) "Whodunit" else "من القاتل"
                                    when (screen.value) {
                                        "settings" -> Column {
                                            SettingsScreen(rememberCoroutineScope(), Modifier.weight(1f), onOpenProfile = {})
                                            AppNavigationBar(AppTopLevelDestination.Settings, {})
                                        }
                                        "profile" -> Column {
                                            ProfileScreen(settings, rememberCoroutineScope(), Modifier.weight(1f))
                                            AppNavigationBar(AppTopLevelDestination.Profile, {})
                                        }
                                        "permission" -> P2pPermissionRationaleScreen(
                                            gate, {}, {}, gameTitle = gameTitle,
                                            roomAction = stringResource(Res.string.setup_host_title),
                                        )
                                        "name-entry" -> NameInputScreen(true, "", {}, {}, gameTitle = gameTitle)
                                        "room-code" -> JoinPromptScreen(
                                            {}, {}, gameTitle = gameTitle, seatName = name, onChangeName = {},
                                        )
                                        "waiting" -> RoomGameStatus(
                                            stringResource(Res.string.room_game_opening), {},
                                            body = stringResource(Res.string.room_game_host, name.asBidiArgument()),
                                        )
                                        "lobby" -> HostRoomControls(spec, room, setup, {}, {}, false, Modifier)
                                    }
                                }
                            }
                        }
                    }
                }
                for (state in listOf("settings", "profile", "permission", "name-entry", "room-code", "waiting", "lobby")) {
                    runOnIdle { screen.value = state }
                    onNodeWithTag("acceptance-viewport").assertIsDisplayed()
                    captureShellAcceptance("$state-$suffix")
                    if (state == "profile") {
                        onNodeWithText(if (english) "Edit" else "تعديل").performClick()
                        captureShellAcceptance("profile-edit-$suffix")
                    }
                }
            }
        } finally {
            stopKoin()
        }
    }
}
