package com.parlor.games.ghamza.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.designsystem.theme.ThemeMode
import com.parlor.games.ghamza.domain.GhamzaAction
import com.parlor.games.ghamza.domain.GhamzaProjection
import com.parlor.games.ghamza.domain.GhamzaReducer
import com.parlor.games.ghamza.domain.GhamzaSettings
import com.parlor.games.ghamza.resources.Res
import com.parlor.games.ghamza.resources.gh_attempt_option
import org.jetbrains.compose.resources.pluralStringResource
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GhamzaShowcaseTest {
    @Test
    fun settings_content_updates_attempts_and_rounds_in_en_and_ar() {
        for (language in listOf(AppLanguage.English, AppLanguage.Arabic)) {
            for (themeMode in listOf(ThemeMode.Dark, ThemeMode.Light)) {
                verifySettings(language, themeMode)
            }
        }
    }

    private fun verifySettings(language: AppLanguage, themeMode: ThemeMode) = runComposeUiTest {
        var caseId by mutableStateOf(GhamzaSettings(attempts = 2, rounds = 3).caseId)
        var target = ""
        setContent {
            GameUiFrame(language = language, width = 390, height = 844, fontScale = 1f, themeMode = themeMode) {
                target = pluralStringResource(Res.plurals.gh_attempt_option, 3, 3)
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(ParlorTheme.colors.surfaceCanvas)
                        .verticalScroll(rememberScrollState())
                        .padding(ParlorTheme.spacing.l),
                ) {
                    GhamzaSettingsContent(caseId = caseId, enabled = true, onChange = { caseId = it })
                }
            }
        }
        waitForIdle()
        capturePublicGameFrame("setup-${language.tag}-${themeMode.name.lowercase()}-1.0")
        onNodeWithText(target).performClick()
        assertEquals(3, GhamzaSettings.fromCaseId(caseId.raw)?.attempts)
    }

    @Test
    fun captures_public_standard_phone_screens_in_light_and_dark() {
        for (language in listOf(AppLanguage.English, AppLanguage.Arabic)) {
            for (themeMode in listOf(ThemeMode.Dark, ThemeMode.Light)) {
                captureStandardJourney(language, themeMode)
            }
        }
    }

    private fun captureStandardJourney(language: AppLanguage, themeMode: ThemeMode) = runComposeUiTest {
        val reducer = GhamzaReducer()
        val initial = reducer.initial(uiPlayers(5), GhamzaSettings(attempts = 2, rounds = 1), 7)
        val self = initial.players.first { it.id != initial.hostOnly.winker }.id
        var state by mutableStateOf(initial)
        setContent {
            GameUiFrame(language = language, width = 390, height = 844, fontScale = 1f, themeMode = themeMode) {
                GhamzaTable(
                    state = GhamzaProjection.toPlayer(state, self).state,
                    self = self,
                    isHost = true,
                    enabled = true,
                    privacyEpoch = 0,
                    onAction = { state = reducer.apply(state, it) },
                    onLeave = {},
                )
            }
        }
        waitForIdle()
        capturePublicGameFrame("reveal-covered-${language.tag}-${themeMode.name.lowercase()}-1.0")

        runOnIdle {
            state = state.players.fold(state) { acc, p ->
                reducer.apply(acc, GhamzaAction.Ready(p.id, acc.public.token))
            }
        }
        waitForIdle()
        capturePublicGameFrame("social-${language.tag}-${themeMode.name.lowercase()}-1.0")

        runOnIdle {
            state.players.filter { it.id != self && it.id != state.hostOnly.winker }.forEach { player ->
                for (attempt in 1..2) {
                    state = reducer.apply(state, GhamzaAction.Winked(player.id, state.public.token, attempt))
                }
            }
        }
        waitForIdle()
        capturePublicGameFrame("final-guess-${language.tag}-${themeMode.name.lowercase()}-1.0")

        runOnIdle {
            val winker = checkNotNull(state.hostOnly.winker)
            state = reducer.apply(state, GhamzaAction.Guess(self, state.public.token, winker))
        }
        waitForIdle()
        capturePublicGameFrame("results-${language.tag}-${themeMode.name.lowercase()}-1.0")
    }
}
