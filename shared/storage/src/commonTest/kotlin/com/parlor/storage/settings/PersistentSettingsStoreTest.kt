package com.parlor.storage.settings

import com.parlor.core.ids.GameId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersistentSettingsStoreTest {

    @Test
    fun absent_preferences_use_safe_defaults() = runTest {
        val store = PersistentSettingsStore(FakeBacking())

        assertFalse(store.reducedMotion.first())
        assertNull(store.languageOverride.first())
        assertEquals("system", store.themeMode.first())
    }

    @Test
    fun every_preference_survives_store_recreation() = runTest {
        val backing = FakeBacking()
        PersistentSettingsStore(backing).apply {
            setReducedMotion(true)
            setLanguageOverride("AR")
            setThemeMode("DARK")
        }

        val restored = PersistentSettingsStore(backing)
        assertTrue(restored.reducedMotion.first())
        assertEquals("ar", restored.languageOverride.first())
        assertEquals("dark", restored.themeMode.first())
    }

    @Test
    fun profile_name_is_canonical_and_survives_recreation_until_cleared() = runTest {
        val backing = FakeBacking()
        val store = PersistentSettingsStore(backing)
        store.setPreferredDisplayName("  ليلى  ")
        assertEquals("ليلى", PersistentSettingsStore(backing).preferredDisplayName.first())
        assertFailsWith<IllegalArgumentException> { store.setPreferredDisplayName(" ") }
        assertEquals("ليلى", store.preferredDisplayName.first())
        store.setPreferredDisplayName(null)
        assertNull(PersistentSettingsStore(backing).preferredDisplayName.first())
    }

    @Test
    fun corrupt_persisted_tags_fall_back_without_blocking_startup() = runTest {
        val backing = FakeBacking().apply {
            strings["language_override"] = "not-installed"
            strings["theme_mode"] = "neon"
        }

        val store = PersistentSettingsStore(backing)

        assertNull(store.languageOverride.first())
        assertEquals("system", store.themeMode.first())
    }

    @Test
    fun invalid_setter_values_are_rejected_without_overwriting_last_value() = runTest {
        val backing = FakeBacking()
        val store = PersistentSettingsStore(backing)
        store.setLanguageOverride("en")
        store.setThemeMode("light")

        assertFailsWith<IllegalArgumentException> {
            store.setLanguageOverride("fr")
        }
        assertFailsWith<IllegalArgumentException> {
            store.setThemeMode("sepia")
        }

        assertEquals("en", store.languageOverride.first())
        assertEquals("light", store.themeMode.first())
        assertEquals("en", backing.strings["language_override"])
        assertEquals("light", backing.strings["theme_mode"])
    }

    @Test
    fun cancelled_or_failed_persistence_never_changes_the_published_flow() = runTest {
        val cancelled = PersistentSettingsStore(
            FakeBacking(writeFailure = CancellationException("stopped")),
        )
        assertFailsWith<CancellationException> {
            cancelled.setReducedMotion(true)
        }
        assertFalse(cancelled.reducedMotion.first())

        val failed = PersistentSettingsStore(
            FakeBacking(writeFailure = SettingsPersistenceException("disk unavailable")),
        )
        assertFailsWith<SettingsPersistenceException> {
            failed.setReducedMotion(true)
        }
        assertFalse(failed.reducedMotion.first())
    }

    @Test
    fun playlist_presets_keep_order_and_repeats_through_store_recreation() = runTest {
        val backing = FakeBacking()
        val hostGames = mutableListOf(GameId("last-light"), GameId("egyptian-dominoes"), GameId("last-light"))
        val expected = PartyPlaylistPreset(hostGames.toList())
        val store = PersistentSettingsStore(backing)
        store.savePartyPlaylistPreset(PartyPlaylistPreset(hostGames))
        hostGames.clear()
        store.savePartyPlaylistPreset(expected)
        assertEquals(1, backing.stringWrites)

        val restored = PersistentSettingsStore(backing)
        assertEquals(listOf(expected), restored.partyPlaylistPresets.first())
        restored.removePartyPlaylistPreset(expected)
        assertTrue(PersistentSettingsStore(backing).partyPlaylistPresets.first().isEmpty())
    }

    @Test
    fun profile_and_party_presets_persist_and_clear_independently() = runTest {
        val backing = FakeBacking()
        val preset = PartyPlaylistPreset(listOf(GameId("last-light"), GameId("egyptian-dominoes")))
        PersistentSettingsStore(backing).apply {
            setPreferredDisplayName("ليلى")
            savePartyPlaylistPreset(preset)
        }

        PersistentSettingsStore(backing).apply {
            assertEquals("ليلى", preferredDisplayName.first())
            assertEquals(listOf(preset), partyPlaylistPresets.first())
            setPreferredDisplayName(null)
        }

        PersistentSettingsStore(backing).apply {
            assertNull(preferredDisplayName.first())
            assertEquals(listOf(preset), partyPlaylistPresets.first())
            setPreferredDisplayName("Nour")
            removePartyPlaylistPreset(preset)
        }

        val restored = PersistentSettingsStore(backing)
        assertEquals("Nour", restored.preferredDisplayName.first())
        assertTrue(restored.partyPlaylistPresets.first().isEmpty())
    }

    @Test
    fun preset_capacity_and_failed_removal_keep_the_last_successful_value() = runTest {
        val backing = FakeBacking()
        val store = PersistentSettingsStore(backing)
        val presets = List(PartyPlaylistPreset.MAX_SAVED) { PartyPlaylistPreset(listOf(GameId("game-$it"))) }
        presets.forEach { store.savePartyPlaylistPreset(it) }
        assertFailsWith<SettingsPersistenceException> {
            store.savePartyPlaylistPreset(PartyPlaylistPreset(listOf(GameId("extra-game"))))
        }
        assertEquals(presets, PersistentSettingsStore(backing).partyPlaylistPresets.first())

        val failing = FakeBacking(SettingsPersistenceException("disk unavailable")).apply {
            strings.putAll(backing.strings)
        }
        val restored = PersistentSettingsStore(failing)
        assertFailsWith<SettingsPersistenceException> { restored.removePartyPlaylistPreset(presets.first()) }
        assertEquals(presets, restored.partyPlaylistPresets.first())
    }

    @Test
    fun corrupt_and_oversized_preset_preferences_do_not_affect_other_settings() = runTest {
        val backing = FakeBacking().apply { strings["language_override"] = "ar" }
        listOf("[{\"games\":[\"Bad-ID\"]}]", "[" + " ".repeat(12_001) + "]").forEach { raw ->
            backing.strings["party_playlist_presets_v1"] = raw
            val store = PersistentSettingsStore(backing)
            assertEquals("ar", store.languageOverride.first())
            assertTrue(store.partyPlaylistPresets.first().isEmpty())
        }
    }

    private class FakeBacking(
        private val writeFailure: Throwable? = null,
    ) : SettingsKeyValueBacking {
        val booleans = mutableMapOf<String, Boolean>()
        val strings = mutableMapOf<String, String>()
        var stringWrites = 0
            private set

        override fun readBoolean(key: String): Boolean? = booleans[key]
        override fun readString(key: String): String? = strings[key]

        override suspend fun writeBoolean(key: String, value: Boolean) {
            writeFailure?.let { throw it }
            booleans[key] = value
        }

        override suspend fun writeString(key: String, value: String?) {
            writeFailure?.let { throw it }
            stringWrites++
            if (value == null) strings.remove(key) else strings[key] = value
        }
    }
}
