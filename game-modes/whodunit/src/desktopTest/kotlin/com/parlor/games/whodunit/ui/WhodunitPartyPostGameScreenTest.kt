package com.parlor.games.whodunit.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.whodunit.ui.screens.postgame.PostGameScreen
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class WhodunitPartyPostGameScreenTest {
    @Test
    fun party_finishes_through_the_lobby_where_a_rematch_gets_new_identity(): Unit = runComposeUiTest {
        var returned = false
        setContent {
            ProvideAppLanguage(AppLanguage.English) {
                ParlorTheme(reducedMotion = true) {
                    PostGameScreen(
                        onReplaySameCase = { error("The finished match must not replay itself") },
                        onTryOtherMode = { error("Changing game belongs to the party lobby") },
                        onBackToLibrary = { returned = true },
                        returnToParty = true,
                    )
                }
            }
        }
        onNodeWithText("Play Again — New Killer").assertDoesNotExist()
        onNodeWithText("Try the Other Mode").assertDoesNotExist()
        onNodeWithText("Back to the Library").assertDoesNotExist()
        onNodeWithText("Back to party").assertIsDisplayed().performClick()
        runOnIdle { assertTrue(returned) }
    }
}
