package com.parlor.games.wordimpostor.ui

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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.designsystem.theme.ThemeMode
import com.parlor.games.wordimpostor.domain.WordImpostorAction
import com.parlor.games.wordimpostor.domain.WordImpostorProjection
import com.parlor.games.wordimpostor.domain.WordImpostorReducer
import com.parlor.games.wordimpostor.domain.WordImpostorSettings
import com.parlor.games.wordimpostor.domain.WordTopic
import com.parlor.games.wordimpostor.resources.Res
import com.parlor.games.wordimpostor.resources.wi_need_players
import org.jetbrains.compose.resources.stringResource
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class WordImpostorShowcaseTest {
    @Test
    fun settings_content_updates_topic_and_flags_insufficient_players_in_en_and_ar() {
        for (language in listOf(AppLanguage.English, AppLanguage.Arabic)) {
            for (themeMode in listOf(ThemeMode.Dark, ThemeMode.Light)) {
                verifySettings(language, themeMode)
            }
        }
    }

    private fun verifySettings(language: AppLanguage, themeMode: ThemeMode) = runComposeUiTest {
        var caseId by mutableStateOf(WordImpostorSettings(topic = WordTopic.Food, impostors = 2, rounds = 3).caseId)
        var expectedWarning = ""
        var targetTopic = ""
        setContent {
            GameUiFrame(language = language, width = 390, height = 844, fontScale = 1f, themeMode = themeMode) {
                expectedWarning = stringResource(Res.string.wi_need_players, 5)
                targetTopic = stringResource(WordContentResources.title(WordTopic.Movies))
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(ParlorTheme.colors.surfaceCanvas)
                        .verticalScroll(rememberScrollState())
                        .padding(ParlorTheme.spacing.l),
                ) {
                    WordImpostorSettingsContent(
                        caseId = caseId,
                        playerCount = 4,
                        enabled = true,
                        onChange = { caseId = it },
                    )
                }
            }
        }
        waitForIdle()
        onNodeWithText(expectedWarning).assertIsDisplayed()
        capturePublicGameFrame("setup-${language.tag}-${themeMode.name.lowercase()}-1.0")
        onNodeWithText(targetTopic).performClick()
        assertEquals(WordTopic.Movies, WordImpostorSettings.fromCaseId(caseId.raw)?.topic)
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
        val reducer = WordImpostorReducer()
        val initial = reducer.initial(uiPlayers(5), WordImpostorSettings(impostors = 1, rounds = 1), 19)
        val self = initial.players.first().id
        var state by mutableStateOf(initial)
        setContent {
            GameUiFrame(language = language, width = 390, height = 844, fontScale = 1f, themeMode = themeMode) {
                WordImpostorTable(
                    state = WordImpostorProjection.toPlayer(state, self).state,
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
                reducer.apply(acc, WordImpostorAction.Ready(p.id, acc.public.token))
            }
        }
        waitForIdle()
        capturePublicGameFrame("questions-${language.tag}-${themeMode.name.lowercase()}-1.0")

        runOnIdle {
            state.public.interactions.forEachIndexed { index, pair ->
                state = reducer.apply(state, WordImpostorAction.Answered(pair.asker, state.public.token, index))
            }
        }
        waitForIdle()
        capturePublicGameFrame("discussion-${language.tag}-${themeMode.name.lowercase()}-1.0")

        runOnIdle {
            state = reducer.apply(state, WordImpostorAction.OpenVoting(state.public.token))
        }
        waitForIdle()
        capturePublicGameFrame("voting-${language.tag}-${themeMode.name.lowercase()}-1.0")

        runOnIdle {
            state.players.forEach { p ->
                val target = state.players.first { it.id != p.id }.id
                state = reducer.apply(state, WordImpostorAction.Vote(p.id, state.public.token, target))
            }
        }
        waitForIdle()
        capturePublicGameFrame("guessing-covered-${language.tag}-${themeMode.name.lowercase()}-1.0")

        runOnIdle {
            val wordId = checkNotNull(state.hostOnly.wordId)
            state.hostOnly.impostors.forEach { imp ->
                state = reducer.apply(state, WordImpostorAction.Guess(imp, state.public.token, wordId))
            }
        }
        waitForIdle()
        capturePublicGameFrame("results-${language.tag}-${themeMode.name.lowercase()}-1.0")
    }
}
