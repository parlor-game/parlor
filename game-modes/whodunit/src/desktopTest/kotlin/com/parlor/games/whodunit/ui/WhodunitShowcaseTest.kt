package com.parlor.games.whodunit.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.parlor.content.repository.CaseRepository
import com.parlor.content.repository.CaseUpdate
import com.parlor.content.schema.CaseSummary
import com.parlor.content.schema.IntRangePair
import com.parlor.content.validation.PayloadValidator
import com.parlor.content.validation.ValidatedCase
import com.parlor.core.ids.CaseId
import com.parlor.core.ids.ClueId
import com.parlor.core.ids.GameId
import com.parlor.core.result.DataError
import com.parlor.core.result.Result
import com.parlor.core.versioning.SemVer
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.theme.ParlorAccent
import com.parlor.designsystem.theme.ParlorAccentScope
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.designsystem.theme.ThemeMode
import com.parlor.games.whodunit.WhodunitIds
import com.parlor.games.whodunit.domain.event.Verdict
import com.parlor.games.whodunit.domain.state.PublicTimerState
import com.parlor.games.whodunit.domain.state.RevealedClue
import com.parlor.games.whodunit.ui.screens.postgame.PostGameScreen
import com.parlor.games.whodunit.ui.screens.reveal.RevealStageScreen
import com.parlor.games.whodunit.ui.screens.round.ClueRevealScreen
import com.parlor.games.whodunit.ui.screens.round.DiscussionScreen
import com.parlor.games.whodunit.ui.screens.setup.ModeSelectionScreen
import com.parlor.games.whodunit.ui.screens.setup.PublicIntroScreen
import com.parlor.games.whodunit.ui.screens.setup.WhodunitCasePickerScreen
import com.parlor.games.whodunit.ui.screens.vote.VoteHandoffScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class WhodunitShowcaseTest {
    @Test
    fun captures_public_standard_phone_screens_in_light_and_dark() {
        for (language in listOf(AppLanguage.English, AppLanguage.Arabic)) {
            for (themeMode in listOf(ThemeMode.Dark, ThemeMode.Light)) {
                captureCasePicker(language, themeMode)
                captureModeSelection(language, themeMode)
                capturePublicIntro(language, themeMode)
                captureClueReveal(language, themeMode)
                captureDiscussion(language, themeMode)
                captureVoteHandoff(language, themeMode)
                captureRevealStage(language, themeMode)
                capturePostGame(language, themeMode)
            }
        }
    }

    private fun captureCasePicker(language: AppLanguage, themeMode: ThemeMode) = runComposeUiTest {
        setContent {
            WhodunitShowcaseFrame(language, themeMode) {
                WhodunitCasePickerScreen(
                    repository = ShowcaseCaseRepository,
                    onCasePicked = {},
                    onBack = {},
                )
            }
        }
        waitForIdle()
        captureFrame("case-picker-${language.tag}-${themeMode.name.lowercase()}-1.0")
    }

    private fun captureModeSelection(language: AppLanguage, themeMode: ThemeMode) = runComposeUiTest {
        setContent {
            WhodunitShowcaseFrame(language, themeMode) {
                ModeSelectionScreen(
                    onModeSelected = {},
                    onBack = {},
                    caseSupportedModes = listOf(
                        WhodunitIds.ClassicVoteModeId.raw,
                        WhodunitIds.EliminationModeId.raw,
                    ),
                    caseSupportedPlayerCounts = 4..8,
                )
            }
        }
        waitForIdle()
        captureFrame("mode-selection-${language.tag}-${themeMode.name.lowercase()}-1.0")
    }

    private fun capturePublicIntro(language: AppLanguage, themeMode: ThemeMode) = runComposeUiTest {
        val arabic = language == AppLanguage.Arabic
        setContent {
            WhodunitShowcaseFrame(language, themeMode) {
                PublicIntroScreen(
                    title = if (arabic) "قصر النيل في منتصف الليل" else "Midnight at the Conservatory",
                    intro = if (arabic) {
                        "انقطعت الكهرباء عن القاعة الكبرى عند دقّ الساعة الحادية عشرة، وعند عودة الأنوار كان جامع التحف قد سقط بجوار الخزنة المفتوحة."
                    } else {
                        "When the chandelier dimmed at eleven, the estate collector collapsed beside the unlocked vitrine with every exit latched from within."
                    },
                    bedrockClues = if (arabic) {
                        listOf(
                            "أُغلقت جميع الأبواب الخارجية من الداخل قبل انقطاع الضوء.",
                            "كان بحوزة أحد الضيوف نسخة احتياطية من مفتاح المعرض.",
                        )
                    } else {
                        listOf(
                            "Every exterior door stayed bolted from inside before the blackout.",
                            "Only one guest carried a duplicate key to the glass gallery.",
                        )
                    },
                    onContinue = {},
                )
            }
        }
        waitForIdle()
        captureFrame("public-intro-${language.tag}-${themeMode.name.lowercase()}-1.0")
    }

    private fun captureClueReveal(language: AppLanguage, themeMode: ThemeMode) = runComposeUiTest {
        val arabic = language == AppLanguage.Arabic
        setContent {
            WhodunitShowcaseFrame(language, themeMode) {
                ClueRevealScreen(
                    clue = RevealedClue(
                        id = ClueId("c2"),
                        text = if (arabic) {
                            "وُجد أثر شمع دافئ على حافة السلم الشرقي المؤدي إلى الشرفة."
                        } else {
                            "Warm candle wax pooled along the east staircase rail leading to the terrace."
                        },
                        roundIndex = 2,
                    ),
                    authorityLabel = if (arabic) "المضيف يتحكم بالتقدم" else "Host leads the reveal",
                    onContinue = {},
                )
            }
        }
        waitForIdle()
        captureFrame("clue-reveal-${language.tag}-${themeMode.name.lowercase()}-1.0")
    }

    private fun captureDiscussion(language: AppLanguage, themeMode: ThemeMode) = runComposeUiTest {
        val arabic = language == AppLanguage.Arabic
        setContent {
            WhodunitShowcaseFrame(language, themeMode) {
                DiscussionScreen(
                    timer = PublicTimerState("discussion-2", totalSeconds = 120, remainingSeconds = 74, paused = false),
                    revealedClues = listOf(
                        RevealedClue(
                            id = ClueId("c1"),
                            text = if (arabic) {
                                "كانت الساعة المعلقة متوقفة عند تمام الحادية عشرة وعشر دقائق."
                            } else {
                                "The hallway clock stopped at exactly 11:10."
                            },
                            roundIndex = 1,
                        ),
                        RevealedClue(
                            id = ClueId("c2"),
                            text = if (arabic) {
                                "وُجد أثر شمع دافئ على حافة السلم الشرقي المؤدي إلى الشرفة."
                            } else {
                                "Warm candle wax pooled along the east staircase rail leading to the terrace."
                            },
                            roundIndex = 2,
                        ),
                    ),
                    authorityLabel = if (arabic) "المضيف يتحكم بالتقدم" else "Host leads the discussion",
                    onAdvance = {},
                )
            }
        }
        waitForIdle()
        captureFrame("discussion-${language.tag}-${themeMode.name.lowercase()}-1.0")
    }

    private fun captureVoteHandoff(language: AppLanguage, themeMode: ThemeMode) = runComposeUiTest {
        val nextVoter = if (language == AppLanguage.Arabic) "سلمى" else "Salma"
        setContent {
            WhodunitShowcaseFrame(language, themeMode) {
                VoteHandoffScreen(
                    nextVoterName = nextVoter,
                    onContinue = {},
                )
            }
        }
        waitForIdle()
        captureFrame("vote-handoff-${language.tag}-${themeMode.name.lowercase()}-1.0")
    }

    private fun captureRevealStage(language: AppLanguage, themeMode: ThemeMode) = runComposeUiTest {
        val arabic = language == AppLanguage.Arabic
        setContent {
            WhodunitShowcaseFrame(language, themeMode) {
                RevealStageScreen(
                    verdict = Verdict.PlayersWin(killerCharacterId = "char-nadim"),
                    killerDisplayName = if (arabic) "نديم" else "Nadim",
                    revealNarrative = if (arabic) {
                        "تسلل نديم عبر الشرفة الشرقية قبل عودة الأضواء، لكنه ترك أثر الشمع الدافئ الذي كشف خط سيره."
                    } else {
                        "Nadim slipped across the east terrace during the blackout, leaving warm wax along the balustrade that broke his alibi."
                    },
                    onAcknowledge = {},
                )
            }
        }
        waitForIdle()
        captureFrame("reveal-stage-${language.tag}-${themeMode.name.lowercase()}-1.0")
    }

    private fun capturePostGame(language: AppLanguage, themeMode: ThemeMode) = runComposeUiTest {
        setContent {
            WhodunitShowcaseFrame(language, themeMode) {
                PostGameScreen(
                    onReplaySameCase = {},
                    onTryOtherMode = {},
                    onBackToLibrary = {},
                )
            }
        }
        waitForIdle()
        captureFrame("postgame-${language.tag}-${themeMode.name.lowercase()}-1.0")
    }

    private fun ComposeUiTest.captureFrame(name: String) {
        val directory = System.getenv("PARLOR_UI_SCREENSHOT_DIR")?.takeIf { it.isNotBlank() }
            ?: "build/reports/tests/public-ux"
        val output = File(directory, "whodunit-$name.png")
        check(output.parentFile.mkdirs() || output.parentFile.isDirectory)
        Image.makeFromBitmap(onNodeWithTag(WHODUNIT_VIEWPORT).captureToImage().asSkiaBitmap()).use { image ->
            checkNotNull(image.encodeToData()).use { data -> output.writeBytes(data.bytes) }
        }
    }

    private object ShowcaseCaseRepository : CaseRepository {
        override suspend fun listCases(gameId: GameId): Result<List<CaseSummary>, DataError> =
            Result.Success(
                listOf(
                    CaseSummary(
                        caseId = "midnight-conservatory",
                        title = "Midnight at the Conservatory",
                        subtitle = "A locked-room blackout during a private auction.",
                        version = SemVer(1, 0, 0),
                        gameId = WhodunitIds.GameId.raw,
                        supportedPlayerCounts = IntRangePair(4, 8),
                        supportedModes = listOf(
                            WhodunitIds.ClassicVoteModeId.raw,
                            WhodunitIds.EliminationModeId.raw,
                        ),
                        language = "en",
                        theme = "mystery",
                        estimatedDuration = IntRangePair(25, 35),
                        minimumAppVersion = SemVer(1, 0, 0),
                    ),
                    CaseSummary(
                        caseId = "nile-manor-ar",
                        title = "قصر النيل في منتصف الليل",
                        subtitle = "لغز غرفة مغلقة أثناء حفل عائلي على ضفاف النيل.",
                        version = SemVer(1, 0, 0),
                        gameId = WhodunitIds.GameId.raw,
                        supportedPlayerCounts = IntRangePair(4, 8),
                        supportedModes = listOf(
                            WhodunitIds.ClassicVoteModeId.raw,
                            WhodunitIds.EliminationModeId.raw,
                        ),
                        language = "ar",
                        theme = "mystery",
                        estimatedDuration = IntRangePair(25, 35),
                        minimumAppVersion = SemVer(1, 0, 0),
                    ),
                ),
            )

        override suspend fun <TPayload> loadCase(
            id: CaseId,
            payloadValidator: PayloadValidator<TPayload>,
        ): Result<ValidatedCase<TPayload>, DataError> = Result.Failure(DataError.NotFound)

        override fun observeCacheUpdates(): Flow<CaseUpdate> = emptyFlow()

        override suspend fun <TPayload> refresh(
            gameId: GameId,
            payloadValidator: PayloadValidator<TPayload>,
        ): Result<Unit, DataError> = Result.Success(Unit)
    }
}

@Composable
private fun WhodunitShowcaseFrame(
    language: AppLanguage,
    themeMode: ThemeMode,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
        ProvideAppLanguage(language) {
            ParlorTheme(themeMode = themeMode, reducedMotion = true) {
                ParlorAccentScope(ParlorAccent.Amber) {
                    Box(modifier = Modifier.size(390.dp, 844.dp).testTag(WHODUNIT_VIEWPORT)) {
                        content()
                    }
                }
            }
        }
    }
}

private const val WHODUNIT_VIEWPORT = "whodunit-showcase-viewport"
