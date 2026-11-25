package com.parlor.app

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.SystemClock
import android.test.InstrumentationTestCase
import androidx.core.view.WindowCompat
import com.parlor.designsystem.theme.ThemeMode
import com.parlor.storage.settings.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.getKoin

@Suppress("DEPRECATION") // Uses the platform instrumentation runner.
class AndroidStatusBarAppearanceRuntimeTest : InstrumentationTestCase() {
    fun testStatusBarIconsFollowTheAppTheme() = runBlocking {
        val app = instrumentation.targetContext.applicationContext as Application
        val settings = app.getKoin().get<SettingsStore>()
        val previousTheme = settings.themeMode.first()
        var activity: Activity? = null
        try {
            settings.setThemeMode(ThemeMode.Light.tag)
            val launched = instrumentation.startActivitySync(
                Intent(Intent.ACTION_MAIN)
                    .setClassName(app.packageName, MainActivity::class.java.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            activity = launched
            awaitStatusBarAppearance(launched, isLight = true)
            settings.setThemeMode(ThemeMode.Dark.tag)
            awaitStatusBarAppearance(launched, isLight = false)
            settings.setThemeMode(ThemeMode.Light.tag)
            awaitStatusBarAppearance(launched, isLight = true)
        } finally {
            activity?.let { instrumentation.runOnMainSync(it::finish) }
            settings.setThemeMode(previousTheme)
        }
    }

    private fun awaitStatusBarAppearance(activity: Activity, isLight: Boolean) {
        val deadline = SystemClock.uptimeMillis() + APPEARANCE_TIMEOUT_MILLIS
        var actual = !isLight
        do {
            instrumentation.runOnMainSync {
                val window = activity.window
                actual = WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars
            }
            if (actual == isLight) return
            SystemClock.sleep(APPEARANCE_POLL_MILLIS)
        } while (SystemClock.uptimeMillis() < deadline)
        assertEquals("Status-bar icon contrast should follow Parlor's palette", isLight, actual)
    }

    private companion object {
        const val APPEARANCE_TIMEOUT_MILLIS = 10_000L
        const val APPEARANCE_POLL_MILLIS = 100L
    }
}
