package com.parlor.games.mafia.ui

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
import com.parlor.core.ids.PlayerId
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.theme.ParlorAccent
import com.parlor.designsystem.theme.ParlorAccentScope
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.designsystem.theme.ThemeMode
import com.parlor.games.mafia.domain.settings.MafiaRoleCounts
import com.parlor.games.mafia.domain.settings.MafiaSettings
import com.parlor.games.mafia.domain.state.Role
import com.parlor.games.mafia.domain.state.Team
import com.parlor.games.mafia.ui.components.MafiaCandlelitCover
import com.parlor.games.mafia.ui.screens.announce.NightAnnouncementScreen
import com.parlor.games.mafia.ui.screens.discussion.DiscussionScreen
import com.parlor.games.mafia.ui.screens.night.PickableTarget
import com.parlor.games.mafia.ui.screens.postgame.PostGameScreen
import com.parlor.games.mafia.ui.screens.setup.MafiaSetupScreen
import com.parlor.games.mafia.ui.screens.vote.VoteAnnouncementScreen
import com.parlor.games.mafia.ui.screens.vote.VoteCastScreen
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class MafiaShowcaseTest {
    @Test
    fun captures_public_standard_phone_screens_in_light_and_dark() {
        for (language in listOf(AppLanguage.English, AppLanguage.Arabic)) {
            for (themeMode in listOf(ThemeMode.Dark, ThemeMode.Light)) {
                captureSetup(language, themeMode)
                captureHandoffCover(language, themeMode)
                captureDawnAnnouncement(language, themeMode)
                captureDiscussion(language, themeMode)
                capturePublicVote(language, themeMode)
                captureVoteResult(language, themeMode)
                capturePostGame(language, themeMode)
            }
        }
    }

    private fun captureSetup(language: AppLanguage, themeMode: ThemeMode) = runComposeUiTest {
        setContent {
            MafiaShowcaseFrame(language, themeMode) {
                MafiaSetupScreen(
                    playerCount = 6,
                    initialSettings = MafiaSettings(
                        roleCounts = MafiaRoleCounts(mafia = 1, detective = 1, doctor = 1),
                    ),
                    onStart = {},
                )
            }
        }
        waitForIdle()
        captureFrame("setup-${language.tag}-${themeMode.name.lowercase()}-1.0")
    }

    private fun captureHandoffCover(language: AppLanguage, themeMode: ThemeMode) = runComposeUiTest {
        val player = if (language == AppLanguage.Arabic) "سارة" else "Sarah"
        setContent {
            MafiaShowcaseFrame(language, themeMode) {
                MafiaCandlelitCover(
                    playerName = player,
                    onDismiss = {},
                )
            }
        }
        waitForIdle()
        captureFrame("handoff-cover-${language.tag}-${themeMode.name.lowercase()}-1.0")
    }

    private fun captureDawnAnnouncement(language: AppLanguage, themeMode: ThemeMode) = runComposeUiTest {
        val victim = if (language == AppLanguage.Arabic) "عمر" else "Omar"
        setContent {
            MafiaShowcaseFrame(language, themeMode) {
                NightAnnouncementScreen(
                    day = 2,
                    killedPlayerName = victim,
                    revealedRole = Role.Civilian,
                    wasSaved = false,
                    onAcknowledged = {},
                )
            }
        }
        waitForIdle()
        captureFrame("dawn-announcement-${language.tag}-${themeMode.name.lowercase()}-1.0")
    }

    private fun captureDiscussion(language: AppLanguage, themeMode: ThemeMode) = runComposeUiTest {
        val alive = if (language == AppLanguage.Arabic) {
            listOf("سارة", "كريم", "ليلى", "طارق", "نور")
        } else {
            listOf("Sarah", "Karim", "Layla", "Tarek", "Nour")
        }
        val dead = if (language == AppLanguage.Arabic) listOf("عمر") else listOf("Omar")
        setContent {
            MafiaShowcaseFrame(language, themeMode) {
                DiscussionScreen(
                    day = 2,
                    aliveNames = alive,
                    deadNames = dead,
                    timerLabel = "02:30",
                    isHost = true,
                    onOpenVote = {},
                )
            }
        }
        waitForIdle()
        captureFrame("discussion-${language.tag}-${themeMode.name.lowercase()}-1.0")
    }

    private fun capturePublicVote(language: AppLanguage, themeMode: ThemeMode) = runComposeUiTest {
        val voter = if (language == AppLanguage.Arabic) "سارة" else "Sarah"
        val names = if (language == AppLanguage.Arabic) {
            listOf("كريم", "ليلى", "طارق", "نور")
        } else {
            listOf("Karim", "Layla", "Tarek", "Nour")
        }
        val candidates = names.mapIndexed { idx, name ->
            PickableTarget(id = PlayerId("p${idx + 2}"), name = name)
        }
        setContent {
            MafiaShowcaseFrame(language, themeMode) {
                VoteCastScreen(
                    voterName = voter,
                    candidates = candidates,
                    revoteRound = 0,
                    onCast = {},
                    onAbstain = {},
                )
            }
        }
        waitForIdle()
        captureFrame("public-vote-${language.tag}-${themeMode.name.lowercase()}-1.0")
    }

    private fun captureVoteResult(language: AppLanguage, themeMode: ThemeMode) = runComposeUiTest {
        val names = if (language == AppLanguage.Arabic) {
            mapOf(
                PlayerId("p2") to "كريم",
                PlayerId("p3") to "ليلى",
                PlayerId("p4") to "طارق",
            )
        } else {
            mapOf(
                PlayerId("p2") to "Karim",
                PlayerId("p3") to "Layla",
                PlayerId("p4") to "Tarek",
            )
        }
        val outcome = if (language == AppLanguage.Arabic) {
            "صوّتت البلدة على إقصاء كريم."
        } else {
            "The town voted to eliminate Karim."
        }
        val role = if (language == AppLanguage.Arabic) "مافيا" else "Mafia"
        setContent {
            MafiaShowcaseFrame(language, themeMode) {
                VoteAnnouncementScreen(
                    day = 2,
                    tally = mapOf(
                        PlayerId("p2") to 3,
                        PlayerId("p3") to 1,
                        PlayerId("p4") to 1,
                    ),
                    nameLookup = { id -> names[id].orEmpty() },
                    eliminatedName = names[PlayerId("p2")],
                    eliminatedRole = role,
                    outcomeLine = outcome,
                    onAcknowledged = {},
                )
            }
        }
        waitForIdle()
        captureFrame("vote-result-${language.tag}-${themeMode.name.lowercase()}-1.0")
    }

    private fun capturePostGame(language: AppLanguage, themeMode: ThemeMode) = runComposeUiTest {
        val roster = if (language == AppLanguage.Arabic) {
            listOf(
                "سارة" to Role.Detective,
                "كريم" to Role.Mafia,
                "ليلى" to Role.Doctor,
                "طارق" to Role.Civilian,
                "نور" to Role.Civilian,
                "عمر" to Role.Civilian,
            )
        } else {
            listOf(
                "Sarah" to Role.Detective,
                "Karim" to Role.Mafia,
                "Layla" to Role.Doctor,
                "Tarek" to Role.Civilian,
                "Nour" to Role.Civilian,
                "Omar" to Role.Civilian,
            )
        }
        setContent {
            MafiaShowcaseFrame(language, themeMode) {
                PostGameScreen(
                    winner = Team.Town,
                    finalRoles = roster,
                    onExit = {},
                )
            }
        }
        waitForIdle()
        captureFrame("postgame-${language.tag}-${themeMode.name.lowercase()}-1.0")
    }

    private fun ComposeUiTest.captureFrame(name: String) {
        val directory = System.getenv("PARLOR_UI_SCREENSHOT_DIR")?.takeIf { it.isNotBlank() }
            ?: "build/reports/tests/public-ux"
        val output = File(directory, "mafia-$name.png")
        check(output.parentFile.mkdirs() || output.parentFile.isDirectory)
        Image.makeFromBitmap(onNodeWithTag(MAFIA_VIEWPORT).captureToImage().asSkiaBitmap()).use { image ->
            checkNotNull(image.encodeToData()).use { data -> output.writeBytes(data.bytes) }
        }
    }
}

@Composable
private fun MafiaShowcaseFrame(
    language: AppLanguage,
    themeMode: ThemeMode,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
        ProvideAppLanguage(language) {
            ParlorTheme(themeMode = themeMode, reducedMotion = true) {
                ParlorAccentScope(ParlorAccent.Crimson) {
                    Box(modifier = Modifier.size(390.dp, 844.dp).testTag(MAFIA_VIEWPORT)) {
                        content()
                    }
                }
            }
        }
    }
}

private const val MAFIA_VIEWPORT = "mafia-showcase-viewport"
