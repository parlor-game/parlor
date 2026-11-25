package com.parlor.games.dominoes.ui

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
import androidx.compose.ui.test.runComposeUiTest
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.designsystem.theme.ThemeMode
import com.parlor.games.dominoes.domain.DominoAction
import com.parlor.games.dominoes.domain.DominoCompetition
import com.parlor.games.dominoes.domain.DominoPhase
import com.parlor.games.dominoes.domain.DominoProjection
import com.parlor.games.dominoes.domain.DominoReducer
import com.parlor.games.dominoes.domain.DominoRules
import com.parlor.games.dominoes.domain.DominoSettings
import com.parlor.games.dominoes.domain.DominoState
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class DominoShowcaseTest {
    @Test
    fun captures_public_standard_phone_screens_in_light_and_dark() {
        for (language in listOf(AppLanguage.English, AppLanguage.Arabic)) {
            for (themeMode in listOf(ThemeMode.Dark, ThemeMode.Light)) {
                captureSetup(language, themeMode)
                captureMatchJourney(language, themeMode)
            }
        }
    }

    private fun captureSetup(language: AppLanguage, themeMode: ThemeMode) = runComposeUiTest {
        var caseId by mutableStateOf(
            DominoSettings(target = 101, competition = DominoCompetition.Teams).caseId,
        )
        setContent {
            GameUiFrame(language = language, width = 390, height = 844, fontScale = 1f, themeMode = themeMode) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(ParlorTheme.colors.surfaceCanvas)
                        .verticalScroll(rememberScrollState())
                        .padding(ParlorTheme.spacing.l),
                ) {
                    DominoSettingsContent(
                        caseId = caseId,
                        playerCount = 4,
                        enabled = true,
                        onChange = { caseId = it },
                    )
                }
            }
        }
        waitForIdle()
        capturePublicGameFrame("setup-${language.tag}-${themeMode.name.lowercase()}-1.0")
    }

    private fun captureMatchJourney(language: AppLanguage, themeMode: ThemeMode) = runComposeUiTest {
        val reducer = DominoReducer()
        val initial = reducer.initial(uiPlayers(4), DominoSettings(target = 51), 18)
        val midGame = advanceMoves(reducer, initial, moves = 5)
        val completed = completeMatch(reducer, initial)
        val self = initial.players.first().id
        var state by mutableStateOf(midGame)
        setContent {
            GameUiFrame(language = language, width = 390, height = 844, fontScale = 1f, themeMode = themeMode) {
                DominoTable(
                    state = DominoProjection.toPlayer(state, self).state,
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
        capturePublicGameFrame("table-chain-${language.tag}-${themeMode.name.lowercase()}-1.0")

        runOnIdle { state = completed }
        waitForIdle()
        capturePublicGameFrame("results-${language.tag}-${themeMode.name.lowercase()}-1.0")
    }

    private fun advanceMoves(reducer: DominoReducer, initial: DominoState, moves: Int): DominoState {
        var state = initial
        repeat(moves) {
            if (state.phase != DominoPhase.Playing) return state
            val by = checkNotNull(state.public.turn)
            val tile = state.privatePerPlayer.getValue(by).hand.firstOrNull {
                DominoRules.playableEnds(state, by, it).isNotEmpty()
            }
            val action = when {
                tile != null -> DominoAction.Place(
                    by,
                    state.public.token,
                    state.public.move,
                    tile.id,
                    DominoRules.playableEnds(state, by, tile).first(),
                )
                DominoRules.canDraw(state, by) -> DominoAction.Draw(by, state.public.token, state.public.move)
                else -> DominoAction.Pass(by, state.public.token, state.public.move)
            }
            state = reducer.apply(state, action)
        }
        return state
    }

    private fun completeMatch(reducer: DominoReducer, initial: DominoState): DominoState {
        var state = initial
        repeat(DominoRules.MAX_HANDS * DominoRules.MAX_MOVE) {
            if (state.phase == DominoPhase.MatchResult) return state
            val action = if (state.phase == DominoPhase.RoundResult) {
                DominoAction.NextRound(state.public.token)
            } else {
                val by = checkNotNull(state.public.turn)
                val tile = state.privatePerPlayer.getValue(by).hand.firstOrNull {
                    DominoRules.playableEnds(state, by, it).isNotEmpty()
                }
                when {
                    tile != null -> DominoAction.Place(
                        by,
                        state.public.token,
                        state.public.move,
                        tile.id,
                        DominoRules.playableEnds(state, by, tile).first(),
                    )
                    DominoRules.canDraw(state, by) -> DominoAction.Draw(by, state.public.token, state.public.move)
                    else -> DominoAction.Pass(by, state.public.token, state.public.move)
                }
            }
            state = reducer.apply(state, action)
        }
        error("Deterministic match did not terminate within its rules bound")
    }
}
