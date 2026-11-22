package com.parlor.app.shell.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.parlor.app.AppNavigationBar
import com.parlor.app.AppTopLevelDestination
import com.parlor.app.shell.fullTextLayout
import com.parlor.app.shell.game.DominoGameShellBinding
import com.parlor.app.shell.game.GhamzaGameShellBinding
import com.parlor.app.shell.game.LastLightGameShellBinding
import com.parlor.app.shell.game.MafiaGameShellBinding
import com.parlor.app.shell.game.WhodunitGameShellBinding
import com.parlor.app.shell.game.WordImpostorGameShellBinding
import com.parlor.app.shell.playmode.PlayModePickerScreen
import com.parlor.app.shell.profile.LocalPreferredDisplayName
import com.parlor.core.ids.GameId
import com.parlor.core.ids.SessionId
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.designsystem.theme.ThemeMode
import com.parlor.games.dominoes.DominoDefinition
import com.parlor.games.ghamza.GhamzaDefinition
import com.parlor.games.lastlight.LastLightDefinition
import com.parlor.games.mafia.MafiaDefinition
import com.parlor.games.whodunit.WhodunitDefinition
import com.parlor.games.wordimpostor.WordImpostorDefinition
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import org.jetbrains.skia.Image

/** Render the shipping composables, not screenshot-only replicas or production-private state. */
@OptIn(ExperimentalTestApi::class)
class HomeCatalogVisualEvidenceTest {
    @Test
    fun catalog_comparison_includes_the_real_dock_and_saved_name_fixture() {
        for (language in listOf(AppLanguage.English, AppLanguage.Arabic)) {
            for (theme in listOf(ThemeMode.Dark, ThemeMode.Light)) {
                catalogWithDock(language, theme)
            }
        }
    }

    private fun catalogWithDock(language: AppLanguage, theme: ThemeMode) = runComposeUiTest {
        val games = catalogBindings()
        val english = language == AppLanguage.English
        setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, 1f),
                LocalPreferredDisplayName provides if (english) "Layla" else "ليلى",
            ) {
                ProvideAppLanguage(language) {
                    ParlorTheme(themeMode = theme, reducedMotion = true) {
                        Box(Modifier.size(390.dp, 740.dp).testTag("shell-preview")) {
                            Column {
                                HomeScreen(games, onGameSelected = {}, modifier = Modifier.weight(1f), onOpenProfile = {})
                                AppNavigationBar(AppTopLevelDestination.Games, {})
                            }
                        }
                    }
                }
            }
        }
        onNodeWithText(if (english) "Play Last Light" else "العب الضوء الأخير").assertIsDisplayed()
        val outputDir = System.getenv("PARLOR_UI_SCREENSHOT_DIR") ?: "build/reports/tests/public-ux"
        val output = File(outputDir, "library-${language.tag}-${theme.tag}-390.png")
        check(output.parentFile.mkdirs() || output.parentFile.isDirectory)
        capturePublicScreen(output)
        val last = if (english) "Open Word Impostor" else "افتح لعبة الإمبوستر"
        onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription(last, substring = true))
        capturePublicScreen(File(outputDir, "catalog-${language.tag}-${theme.tag}-390.png"))
    }

    private fun catalogBindings() = listOf(
        WhodunitGameShellBinding(WhodunitDefinition(Json.Default)),
        MafiaGameShellBinding(MafiaDefinition(Json.Default)),
        LastLightGameShellBinding(LastLightDefinition()),
        DominoGameShellBinding(DominoDefinition()),
        GhamzaGameShellBinding(GhamzaDefinition()),
        WordImpostorGameShellBinding(WordImpostorDefinition()),
    )

    @Test
    fun featured_picker_changes_the_game_opened_by_play() = runComposeUiTest {
        val games = listOf(WhodunitGameShellBinding(WhodunitDefinition(Json.Default)), MafiaGameShellBinding(MafiaDefinition(Json.Default)))
        var opened: GameId? = null
        setContent {
            ProvideAppLanguage(AppLanguage.English) {
                ParlorTheme(themeMode = ThemeMode.Dark, reducedMotion = true) {
                    Box(Modifier.size(390.dp, 740.dp)) {
                        FeaturedGames(games) { opened = it }
                    }
                }
            }
        }
        onNodeWithContentDescription("Next featured game").performClick()
        onNodeWithText("Play Mafia").performClick()
        assertEquals(games[1].definition.id, opened)
        onNodeWithContentDescription("Feature Whodunit").performClick()
        onNodeWithText("Play Whodunit").performClick()
        assertEquals(games[0].definition.id, opened)
    }

    @Test
    fun phone_screens_render_in_both_languages_and_themes_at_normal_and_enlarged_text() {
        for (language in listOf(AppLanguage.English, AppLanguage.Arabic)) {
            for (theme in listOf(ThemeMode.Light, ThemeMode.Dark)) {
                for (fontScale in listOf(1f, 2f)) {
                    renderScreen(language, theme, fontScale, catalog = true)
                    renderScreen(language, theme, fontScale, catalog = false)
                }
                renderScreen(language, theme, 1f, catalog = true, width = 320)
                renderScreen(language, theme, 2f, catalog = true, width = 320)
                renderScreen(language, theme, 1f, catalog = true, width = 768)
            }
        }
    }

    @Test
    fun six_game_setup_pickers_expose_supported_actions_at_compact_large_text() {
        for (language in listOf(AppLanguage.English, AppLanguage.Arabic)) {
            for (theme in listOf(ThemeMode.Light, ThemeMode.Dark)) {
                for (gameIndex in 0..5) {
                    renderScreen(language, theme, 2f, catalog = false, width = 320, setupGameIndex = gameIndex)
                }
            }
        }
    }

    @Test
    fun selected_title_autosizes_on_a_small_phone_and_grows_with_accessibility_text() = runComposeUiTest {
        val width = mutableStateOf(520.dp)
        val scale = mutableStateOf(1f)
        val game = WhodunitGameShellBinding(WhodunitDefinition(Json.Default))
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, scale.value)) {
                ProvideAppLanguage(AppLanguage.English) {
                    ParlorTheme(reducedMotion = true) {
                        Box(Modifier.size(width.value, 740.dp)) {
                            PlayModePickerScreen(
                                presentation = game.catalogPresentation(),
                                capabilities = game.capabilities,
                                supportedPlayerCounts = game.multiplayerContract.supportedPlayerCounts,
                                onModeSelected = {}, onHost = {}, onJoin = {}, onBack = {},
                            )
                        }
                    }
                }
            }
        }
        val title = onNode(hasText("Whodunit"))
        fun renderedWidth() = title.getUnclippedBoundsInRoot().let { it.right - it.left }
        val wide = renderedWidth()
        runOnIdle { width.value = 320.dp }
        val compact = renderedWidth()
        assertTrue(compact < wide, "The rendered title should occupy less width on a small phone: $compact vs $wide")
        runOnIdle { scale.value = 2f }
        title.assertIsDisplayed().fullTextLayout()
        assertTrue(renderedWidth() > compact, "User text enlargement must still increase the rendered size")
    }

    @Test
    fun recovery_uses_public_session_metadata_alongside_the_library() {
        renderScreen(AppLanguage.English, ThemeMode.Light, 1f, catalog = true, recovery = true)
    }

    private fun renderScreen(
        language: AppLanguage, theme: ThemeMode, fontScale: Float, catalog: Boolean, width: Int = 390,
        setupGameIndex: Int? = null, recovery: Boolean = false,
    ) = runComposeUiTest {
        val games = catalogBindings()
        val setupGame = games[setupGameIndex ?: 0]
        lateinit var selectedTitle: String
        setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, fontScale),
                LocalPreferredDisplayName provides if (language == AppLanguage.English) "Layla" else "ليلى",
            ) {
                ProvideAppLanguage(language) {
                    ParlorTheme(themeMode = theme, reducedMotion = true) {
                        Box(Modifier.size(width.dp, 740.dp).testTag("shell-preview")) {
                            if (catalog) {
                                HomeScreen(
                                    games = games,
                                    onGameSelected = {},
                                    unfinishedSessions = if (recovery) listOf(
                                        LocalRecoveryEntry(SessionId("synthetic-public-fixture"), games.first().definition.id),
                                    ) else emptyList(),
                                    hasResumableMultiplayer = recovery,
                                )
                            } else {
                                val presentation = setupGame.catalogPresentation()
                                selectedTitle = presentation.title
                                PlayModePickerScreen(
                                    onModeSelected = {}, onHost = {}, onJoin = {}, onBack = {},
                                    presentation = presentation,
                                    capabilities = setupGame.capabilities,
                                    supportedPlayerCounts = setupGame.multiplayerContract
                                        ?.supportedPlayerCounts ?: setupGame.definition.supportedPlayerCounts,
                                )
                            }
                        }
                    }
                }
            }
        }
        onNodeWithTag("shell-preview").assertIsDisplayed()
        if (!catalog) onNode(hasText(selectedTitle)).assertIsDisplayed().fullTextLayout()
        if (catalog && fontScale == 2f) {
            assertFirstGameChoicesVisible(language)
        }
        if (setupGameIndex != null) {
            assertSetupActions(language, supportsPassAndPlay = setupGame.capabilities.supportsLocalPlay())
            val back = if (language == AppLanguage.Arabic) "العودة إلى الشاشة الرئيسية." else "Return to the home screen."
            onNodeWithContentDescription(back).performScrollTo()
        }
        // Public fixtures only. Stay below the existing **/build/reports/tests/
        // artifact glob, but outside desktopTest so Gradle's HTML writer cannot
        // replace the images. The environment override reaches the test worker.
        val directory = System.getProperty("parlor.ux.screenshotDir")
            ?: System.getenv("PARLOR_UI_SCREENSHOT_DIR")
            ?: "build/reports/tests/public-ux"
        if (!directory.isNullOrBlank()) {
            val screen = when {
                recovery -> "recovery"
                catalog -> "home"
                setupGameIndex != null -> "setup-${setupGame.definition.id.raw}"
                else -> "setup"
            }
            val sizeSuffix = if (width == 390) "" else "-${width}dp"
            val output = File(directory, "$screen-${language.tag}-${theme.tag}-$fontScale$sizeSuffix.png")
            check(output.parentFile.mkdirs() || output.parentFile.isDirectory)
            capturePublicScreen(output)
            if (width == 768 && catalog) {
                onNode(hasScrollToIndexAction()).performScrollToNode(
                    hasContentDescription(if (language == AppLanguage.Arabic) "افتح لعبة الإمبوستر" else "Open Word Impostor", substring = true),
                )
                capturePublicScreen(File(directory, "library-end-${language.tag}-${theme.tag}-768dp.png"))
            }
        }
    }

    private fun ComposeUiTest.capturePublicScreen(output: File) {
        Image.makeFromBitmap(onNodeWithTag("shell-preview").captureToImage().asSkiaBitmap()).use { image ->
            checkNotNull(image.encodeToData()).use { data -> output.writeBytes(data.bytes) }
        }
    }

    private fun ComposeUiTest.assertSetupActions(language: AppLanguage, supportsPassAndPlay: Boolean) {
        val arabic = language == AppLanguage.Arabic
        val host = if (arabic) "افتح غرفة وأظهر الرمز لأصدقائك للانضمام."
            else "Open a room and show a code your friends can join."
        val join = if (arabic) "أدخل رمز المضيف للدخول إلى طاولته."
            else "Enter the host's room code to join their table."
        val local = if (arabic) "ابدأ جلسة تمرير ولعب على هذا الجهاز."
            else "Start a pass-and-play session on this device."
        onNodeWithContentDescription(host).performScrollTo().assertIsDisplayed().assertIsEnabled()
        onNodeWithContentDescription(join).performScrollTo().assertIsDisplayed().assertIsEnabled()
        if (supportsPassAndPlay) {
            onNodeWithContentDescription(local).performScrollTo().assertIsDisplayed().assertIsEnabled()
        } else {
            onNodeWithContentDescription(local).assertDoesNotExist()
        }
        onNodeWithContentDescription(host).performScrollTo()
    }

    private fun ComposeUiTest.assertFirstGameChoicesVisible(language: AppLanguage) {
        val arabic = language == AppLanguage.Arabic
        val description = if (arabic) "افتح من القاتل واختر طريقة اللعب."
            else "Open Whodunit and pick how you want to play."
        onNodeWithContentDescription(description).assertIsDisplayed()
        val viewport = onNodeWithTag("shell-preview").getUnclippedBoundsInRoot()
        val labels = if (arabic) listOf("6 لاعبين", "هاتف واحد أو هواتف قريبة", "افتح من القاتل")
            else listOf("6 players", "One phone or nearby", "Open Whodunit")
        for (label in labels) {
            val node = onNode(hasText(label) and hasAnyAncestor(hasContentDescription(description)), useUnmergedTree = true)
            node.assertIsDisplayed().fullTextLayout()
            val bounds = node.getUnclippedBoundsInRoot()
            assertTrue(bounds.top >= viewport.top && bounds.bottom <= viewport.bottom &&
                bounds.left >= viewport.left && bounds.right <= viewport.right,
                "Actual game requirements/action must be fully visible without scrolling: $label at $bounds in $viewport")
        }
    }
}
