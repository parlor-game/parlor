package com.parlor.app

/** Global shell response; each game binding owns back policy inside its flow. */
internal enum class AppBackAction {
    AllowPlatformExit,
    NavigateGames,
    DelegateToFlow,
    HandledByNavDisplay,
}

internal fun appBackAction(route: AppRoute): AppBackAction = when (route) {
    AppRoute.Home -> AppBackAction.AllowPlatformExit
    AppRoute.Profile, AppRoute.Settings -> AppBackAction.NavigateGames
    AppRoute.Party -> AppBackAction.DelegateToFlow
    is AppRoute.LocalResumeFailure -> AppBackAction.HandledByNavDisplay
    is AppRoute.Game -> AppBackAction.DelegateToFlow
}


/** Prevents guarded game flows from exposing the catalog during predictive Back. */
internal fun <T> visibleEntriesForBack(
    route: AppRoute,
    entries: List<T>,
): List<T> = if (route is AppRoute.Game || route == AppRoute.Party) entries.takeLast(1) else entries
