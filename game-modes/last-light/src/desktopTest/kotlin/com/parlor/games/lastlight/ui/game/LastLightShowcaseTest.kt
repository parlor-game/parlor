package com.parlor.games.lastlight.ui.game

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.theme.ThemeMode
import com.parlor.games.lastlight.domain.model.AvailableActions
import com.parlor.games.lastlight.domain.model.PublicClaim
import com.parlor.games.lastlight.ui.flow.passandplay.LastLightHandoffScreen
import com.parlor.games.lastlight.ui.flow.passandplay.LastLightLocalSetupScreen
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class LastLightShowcaseTest {
    @Test
    fun captures_public_standard_phone_screens_in_light_and_dark() {
        for (language in listOf(AppLanguage.English, AppLanguage.Arabic)) {
            for (themeMode in listOf(ThemeMode.Dark, ThemeMode.Light)) {
                captureSetup(language, themeMode)
                captureHandoff(language, themeMode)
                captureTableJourney(language, themeMode)
            }
        }
    }

    private fun captureSetup(language: AppLanguage, themeMode: ThemeMode) = runComposeUiTest {
        val names = if (language == AppLanguage.Arabic) {
            listOf("نور", "طارق", "سلمى", "كريم")
        } else {
            listOf("Nour", "Tarek", "Salma", "Karim")
        }
        setContent {
            LastLightTestFrame(width = 390.dp, height = 844.dp, fontScale = 1f, language = language, themeMode = themeMode) {
                LastLightLocalSetupScreen(
                    names = names,
                    onNamesChanged = {},
                    onStart = {},
                    onBack = {},
                    actionsEnabled = true,
                )
            }
        }
        waitForIdle()
        capturePublicLastLightFrame("setup-${language.tag}-${themeMode.name.lowercase()}-1.0")
    }

    private fun captureHandoff(language: AppLanguage, themeMode: ThemeMode) = runComposeUiTest {
        val name = if (language == AppLanguage.Arabic) "طارق" else "Tarek"
        setContent {
            LastLightTestFrame(width = 390.dp, height = 844.dp, fontScale = 1f, language = language, themeMode = themeMode) {
                TableBackdrop(Modifier.fillMaxSize()) {
                    LastLightHandoffScreen(
                        playerName = name,
                        enabled = true,
                        rejected = false,
                        onTakeDevice = {},
                    )
                }
            }
        }
        waitForIdle()
        capturePublicLastLightFrame("handoff-${language.tag}-${themeMode.name.lowercase()}-1.0")
    }

    private fun captureTableJourney(language: AppLanguage, themeMode: ThemeMode) = runComposeUiTest {
        val activeView = playingView().copy(
            latestClaim = PublicClaim("c", 2),
            availableActions = AvailableActions(canPlay = true, canChallenge = true, maxPlayableCards = 3),
        )
        var currentView by mutableStateOf(activeView)
        setContent {
            LastLightTestFrame(width = 390.dp, height = 844.dp, fontScale = 1f, language = language, themeMode = themeMode) {
                TestSessionTable(view = currentView)
            }
        }
        waitForIdle()
        capturePublicLastLightFrame("table-covered-${language.tag}-${themeMode.name.lowercase()}-1.0")

        runOnIdle { currentView = roundResultView() }
        waitForIdle()
        capturePublicLastLightFrame("round-result-${language.tag}-${themeMode.name.lowercase()}-1.0")

        runOnIdle { currentView = finishedView() }
        waitForIdle()
        capturePublicLastLightFrame("results-${language.tag}-${themeMode.name.lowercase()}-1.0")
    }

    private fun ComposeUiTest.capturePublicLastLightFrame(name: String) {
        val directory = System.getenv("PARLOR_UI_SCREENSHOT_DIR")?.takeIf { it.isNotBlank() }
            ?: "build/reports/tests/public-ux"
        val output = File(directory, "last-light-$name.png")
        check(output.parentFile.mkdirs() || output.parentFile.isDirectory)
        Image.makeFromBitmap(onNodeWithTag(LAST_LIGHT_VIEWPORT).captureToImage().asSkiaBitmap()).use { image ->
            checkNotNull(image.encodeToData()).use { data -> output.writeBytes(data.bytes) }
        }
    }
}
