package com.parlor.games.mafia.ui.screens.announce

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.localization.asBidiArgument
import com.parlor.designsystem.theme.ParlorTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NightAnnouncementInteractionTest {
    @Test
    fun arabicDawnIsPublicAndAcknowledgmentDependsOnEligibility() = runComposeUiTest {
        val name = "سلمى (Salma 12)"
        val mayAcknowledge: MutableState<Boolean> = mutableStateOf(false)
        var acknowledgments = 0
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                ProvideAppLanguage(AppLanguage.Arabic) {
                    ParlorTheme(reducedMotion = true) {
                        NightAnnouncementScreen(
                            day = 2,
                            killedPlayerName = name,
                            revealedRole = null,
                            wasSaved = false,
                            onAcknowledged = if (mayAcknowledge.value) ({ acknowledgments++ }) else null,
                            waitingLabel = if (mayAcknowledge.value) null else "انتظر باقي اللاعبين.",
                            modifier = Modifier.size(320.dp, 640.dp),
                        )
                    }
                }
            }
        }
        onNodeWithText("يمكن للجميع النظر إلى الشاشة.").assertIsDisplayed()
        onNodeWithText("قُتل ${name.asBidiArgument()}").assertIsDisplayed()
        onNodeWithText("كان", substring = true).assertDoesNotExist()
        val acknowledge = onNodeWithContentDescription("تأكيد إعلان الليل")
        acknowledge.assertDoesNotExist()
        onNodeWithText("انتظر باقي اللاعبين.").assertExists()
        runOnIdle { mayAcknowledge.value = true }
        acknowledge.performScrollTo().performClick()
        runOnIdle { assertEquals(1, acknowledgments) }
    }
}
