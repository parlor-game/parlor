package com.parlor.app

import com.parlor.core.ids.GameId
import com.parlor.core.ids.SessionId
import kotlin.test.Test
import kotlin.test.assertEquals

class AppBackPolicyTest {
    @Test
    fun back_from_profile_returns_to_games_and_does_not_rearm_startup_recovery() {
        val navigator = AppNavigator()
        assertEquals(AppNavigationMutation.Applied, navigator.selectTopLevel(AppTopLevelDestination.Profile))
        assertEquals(AppRoute.Profile, navigator.currentRoute)
        assertEquals(AppBackAction.NavigateGames, appBackAction(navigator.currentRoute))
        assertEquals(AppNavigationMutation.Applied, navigator.navigateBack(AppRoute.Profile))
        assertEquals(AppRoute.Home, navigator.currentRoute)
        assertEquals(false, navigator.permitsStartupRecovery)
    }

    @Test
    fun home_allows_the_platform_to_finish_the_application() {
        assertEquals(AppBackAction.AllowPlatformExit, appBackAction(AppRoute.Home))
    }

    @Test
    fun settings_navigates_to_the_games_tab() {
        assertEquals(AppBackAction.NavigateGames, appBackAction(AppRoute.Settings))
    }

    @Test
    fun unreadable_save_recovery_is_owned_by_nav_display() {
        val recovery = AppRoute.LocalResumeFailure(SessionId("damaged-save"))

        assertEquals(AppBackAction.HandledByNavDisplay, appBackAction(recovery))
    }

    @Test
    fun guarded_game_exposes_only_its_current_entry_to_predictive_back() {
        val entries = listOf("catalog", "game")

        assertEquals(
            listOf("game"),
            visibleEntriesForBack(AppRoute.Game(GameId("fixture"), entryId = 1L), entries),
        )
        assertEquals(
            entries,
            visibleEntriesForBack(
                AppRoute.LocalResumeFailure(SessionId("damaged-save")),
                entries,
            ),
        )
    }

    @Test
    fun registered_game_content_receives_back_and_owns_transactional_cleanup() {
        val game = AppRoute.Game(GameId("fixture"), entryId = 1L)

        assertEquals(AppBackAction.DelegateToGame, appBackAction(game))
    }
}
