package com.parlor.app.shell.multiplayer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.parlor.app.App
import com.parlor.app.StartupMultiplayerRecoveryGate
import com.parlor.app.di.contentModule
import com.parlor.app.lifecycle.AppLifecycleCoordinator
import com.parlor.core.random.SessionSeedSource
import com.parlor.core.result.Result
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.games.dominoes.di.dominoModule
import com.parlor.games.ghamza.di.ghamzaModule
import com.parlor.games.lastlight.di.lastLightModule
import com.parlor.games.mafia.di.mafiaModule
import com.parlor.games.whodunit.di.whodunitModule
import com.parlor.games.wordimpostor.di.wordImpostorModule
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.transport.HostConfig
import com.parlor.networking.transport.RoomTransport
import com.parlor.networking.transport.TransportCapability
import com.parlor.session.multidevice.ProcessMultiplayerSessionOwner
import com.parlor.session.multidevice.ProcessMultiplayerState
import com.parlor.storage.settings.InMemorySettingsStore
import com.parlor.storage.snapshot.InMemorySnapshotStore
import com.parlor.storage.snapshot.SnapshotStore
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.koin.compose.KoinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals

/** Real App + registered routes and pointer input; not UIKit/XCTest delivery proof. */
@OptIn(ExperimentalTestApi::class)
class PublicSetupInputJourneyTest {
    @Test
    fun english_catalog_host_back_join_and_name_entry_use_the_registered_routes() {
        verifyJourneys(AppLanguage.English, mapOf(
            "Open Whodunit and pick how you want to play." to "Whodunit",
            "Open the Mafia game." to "Mafia",
            "Open Last Light and choose local or nearby play" to "Last Light",
            "Open Egyptian Dominoes" to "Egyptian Dominoes",
            "Open Ghamza" to "Ghamza",
            "Open Word Impostor" to "Word Impostor",
        ))
    }

    @Test
    fun arabic_catalog_host_back_join_and_name_entry_use_the_registered_routes() {
        verifyJourneys(AppLanguage.Arabic, mapOf(
            "افتح من القاتل واختر طريقة اللعب." to "من القاتل",
            "افتح لعبة حرامية وظباط." to "حرامية وظباط",
            "افتح الضوء الأخير واختر اللعب على جهاز واحد أو مع الأجهزة القريبة" to "الضوء الأخير",
            "افتح الدومنة المصرية" to "الدومنة المصرية",
            "افتح غمزة" to "غمزة",
            "افتح لعبة الإمبوستر" to "الإمبوستر",
        ))
    }

    @Test
    fun registered_library_counts_follow_the_active_phone_filter() {
        PublicSetupFixture(AppLanguage.English).use { fixture ->
            runComposeUiTest {
                installApp(fixture)
                for ((filter, count) in listOf("All" to 6, "One phone" to 3, "Nearby" to 6)) {
                    val chip = onNodeWithText(filter)
                    chip.performScrollTo().performTouchInput { click() }
                    chip.assertIsSelected()
                    onNodeWithText("$count games").assertIsDisplayed()
                }
            }
        }
    }

    @Test
    fun one_phone_entries_reach_each_game_owned_setup_without_networking() {
        val games = mapOf(
            "Open Whodunit and pick how you want to play." to "Choose tonight’s mystery.",
            "Open the Mafia game." to "How many players?",
            "Open Last Light and choose local or nearby play" to "Gather around one device",
        )
        for ((game, destination) in games) {
            PublicSetupFixture(AppLanguage.English).use { fixture ->
                runComposeUiTest {
                    installApp(fixture)
                    onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription(game))
                    tapPublicControl(game)
                    tapPublicControl("Start a pass-and-play session on this device.")
                    onNodeWithText(destination).assertIsDisplayed()
                    runOnIdle { assertEquals(0, fixture.transport.lanCalls.get()) }
                }
            }
        }
    }

    private fun verifyJourneys(language: AppLanguage, games: Map<String, String>) {
        for ((game, title) in games) {
            PublicSetupFixture(language).use { fixture ->
                runComposeUiTest {
                    installApp(fixture)
                    verifyPublicJourney(game, title, language)
                    runOnIdle {
                        assertEquals(0, fixture.transport.lanCalls.get(), "Public setup must not start networking")
                        assertEquals(ProcessMultiplayerState.Idle, fixture.owner.state.value)
                    }
                }
            }
        }
    }

    private fun ComposeUiTest.installApp(fixture: PublicSetupFixture) {
        setContent {
            KoinApplication(application = {
                modules(
                    whodunitModule, mafiaModule, lastLightModule, dominoModule,
                    ghamzaModule, wordImpostorModule, contentModule,
                    module {
                        single { Json { encodeDefaults = true } }
                        single<RoomTransport> { fixture.transport }
                        single { fixture.owner }
                        single { StartupMultiplayerRecoveryGate() }
                        single<SessionSeedSource> { SessionSeedSource { 1L } }
                        single<SnapshotStore> { InMemorySnapshotStore() }
                        single { AppLifecycleCoordinator(fixture.transport).apply { notifyActive() } }
                    },
                )
            }) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                    Box(Modifier.size(390.dp, 740.dp)) { App(fixture.settings) }
                }
            }
        }
    }

    private fun ComposeUiTest.verifyPublicJourney(game: String, title: String, language: AppLanguage) {
        val arabic = language == AppLanguage.Arabic
        val host = if (arabic) "افتح غرفة وأظهر الرمز لأصدقائك للانضمام."
            else "Open a room and show a code your friends can join."
        val join = if (arabic) "أدخل رمز المضيف للدخول إلى طاولته."
            else "Enter the host's room code to join their table."
        val back = if (arabic) "العودة إلى الشاشة الرئيسية." else "Return to the home screen."
        val hostContinue = if (arabic) "احفظ اسمك وتابع لإعداد طاولتك."
            else "Save your name and continue to set up your table."
        val peerContinue = if (arabic) "احفظ اسمك وتابع لإدخال رمز الغرفة."
            else "Save your name and continue to enter a room code."
        val findRoom = if (arabic) "الاتصال بالمضيف باستخدام الرمز المُدخل."
            else "Connect to the host using the entered room code."
        val input = if (arabic) "إيمان المصري" else "Table Test"

        onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription(game))
        tapPublicControl(game)
        tapPublicControl(host)
        // Desktop correctly has no permission rationale; exercise the real
        // platform route, without overriding the gate or claiming iOS coverage.
        onNode(hasSetTextAction()).assertExists()
        onNodeWithText(title.uppercase()).assertIsDisplayed()
        onNodeWithContentDescription(hostContinue).assertIsNotEnabled()
        tapPublicControl(back)
        onNode(hasSetTextAction()).assertDoesNotExist()
        onNodeWithContentDescription(hostContinue).assertDoesNotExist()
        onNodeWithContentDescription(game).assertIsDisplayed().assertIsEnabled()

        tapPublicControl(game)
        tapPublicControl(join)
        onNodeWithText(title.uppercase()).assertIsDisplayed()
        val field = onNode(hasSetTextAction())
        assertDraft("")
        onNodeWithContentDescription(peerContinue).assertIsNotEnabled()
        field.performTouchInput { click() }.assertIsFocused()
        input.forEachIndexed { index, character ->
            field.performTextInput(character.toString())
            assertDraft(input.take(index + 1))
        }
        field.performImeAction()
        field.assertIsNotFocused()
        assertDraft(input)
        onNodeWithContentDescription(peerContinue).assertIsEnabled()
        tapPublicControl(peerContinue, scrollable = false)
        onNodeWithText(title.uppercase()).assertIsDisplayed()
        assertDraft("")
        onNodeWithContentDescription(peerContinue).assertDoesNotExist()
        onNodeWithContentDescription(findRoom).assertExists().assertIsNotEnabled()
    }

    private fun ComposeUiTest.tapPublicControl(description: String, scrollable: Boolean = true) {
        val control = onNodeWithContentDescription(description)
        if (scrollable) control.performScrollTo()
        control.assertIsDisplayed().assertIsEnabled().performTouchInput { click() }
    }

    private fun ComposeUiTest.assertDraft(expected: String) {
        val draft = onNode(hasSetTextAction()).fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        assertEquals(expected, draft)
    }
}

private class PublicSetupFixture(language: AppLanguage) : AutoCloseable {
    val transport = PublicSetupTransport()
    val settings = InMemorySettingsStore(initialLanguageOverride = language.tag, initialReducedMotion = true)
    private val job = SupervisorJob()
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    val owner = ProcessMultiplayerSessionOwner(CoroutineScope(job + dispatcher))

    override fun close() {
        try {
            runBlocking { withTimeout(5_000) { job.cancelAndJoin() } }
        } finally {
            dispatcher.close()
        }
    }
}

private class PublicSetupTransport : RoomTransport {
    override val capability = TransportCapability(false, 1, 1024)
    val lanCalls = AtomicInteger()

    override suspend fun host(config: HostConfig): Result<LocalRoom, NetError> = unexpectedLanCall()
    override suspend fun join(code: String, displayName: String): Result<LocalRoom, NetError> = unexpectedLanCall()

    private fun unexpectedLanCall(): Result<LocalRoom, NetError> {
        lanCalls.incrementAndGet()
        return Result.Failure(NetError.NotConnected)
    }
}
