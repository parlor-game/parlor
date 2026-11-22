package com.parlor.app.shell.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.parlor.app.shell.captureShellAcceptance
import com.parlor.app.shell.multiplayer.NameInputScreen
import com.parlor.designsystem.components.LocalParlorToastState
import com.parlor.designsystem.components.ParlorToastState
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.designsystem.theme.ThemeMode
import com.parlor.storage.settings.InMemorySettingsStore
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ProfileScreenTest {
    @Test
    fun saved_profile_prefills_host_and_join_without_replacing_an_existing_room_draft() = runComposeUiTest {
        val settings = InMemorySettingsStore()
        val screen = mutableStateOf(Screen.Profile)
        val decisions = mutableListOf<String>()
        setContent {
            val preferredName by settings.preferredDisplayName.collectAsState(initial = null)
            CompositionLocalProvider(
                LocalPreferredDisplayName provides preferredName,
                LocalParlorToastState provides ParlorToastState(),
            ) {
                ProvideAppLanguage(AppLanguage.English) {
                    ParlorTheme(themeMode = ThemeMode.Dark, reducedMotion = true) {
                        Box(Modifier.size(390.dp, 760.dp).testTag("acceptance-viewport")) {
                            when (screen.value) {
                                Screen.Profile -> ProfileScreen(settings = settings, scope = rememberCoroutineScope())
                                Screen.Host -> NameInputScreen(true, "", decisions::add, {})
                                Screen.Join -> NameInputScreen(false, "", decisions::add, {})
                                Screen.RoomDraft -> NameInputScreen(false, "Room alias", decisions::add, {})
                            }
                        }
                    }
                }
            }
        }

        onNodeWithText("Display name").performTextInput("Nour")
        onNodeWithText("Save name").performClick()
        waitUntil { settings.preferredDisplayName.value == "Nour" }
        captureShellAcceptance("compose-profile-en-dark-390")

        runOnIdle { screen.value = Screen.Host }
        onNodeWithText("Nour").assertIsDisplayed()
        onNodeWithContentDescription("Save your name and continue to set up your table.").performClick()

        runOnIdle { screen.value = Screen.Join }
        onNodeWithText("Nour").assertIsDisplayed()
        onNodeWithContentDescription("Save your name and continue to enter a room code.").performClick()

        runOnIdle { screen.value = Screen.RoomDraft }
        onNodeWithText("Room alias").assertIsDisplayed()
        onNodeWithContentDescription("Save your name and continue to enter a room code.").performClick()
        runOnIdle { assertEquals(listOf("Nour", "Nour", "Room alias"), decisions) }
    }
}

private enum class Screen { Profile, Host, Join, RoomDraft }
