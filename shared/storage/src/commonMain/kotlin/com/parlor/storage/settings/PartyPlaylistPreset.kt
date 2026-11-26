package com.parlor.storage.settings

import com.parlor.core.ids.GameId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A reusable local game order. Live room state and match results are never written here. */
@Serializable
data class PartyPlaylistPreset(val games: List<GameId>) {
    init {
        require(games.size in 1..MAX_GAMES && games.all { GameId.isCanonical(it.raw) }) {
            "Invalid saved party playlist"
        }
    }

    companion object {
        const val MAX_SAVED = 8
        private const val MAX_GAMES = 32
    }
}

internal object PartyPlaylistPresetPolicy {
    private const val MAX_STORED_CHARS = 12_000

    fun add(saved: List<PartyPlaylistPreset>, preset: PartyPlaylistPreset): List<PartyPlaylistPreset> {
        val snapshot = PartyPlaylistPreset(preset.games.toList())
        if (snapshot in saved) return saved
        if (saved.size >= PartyPlaylistPreset.MAX_SAVED) throw SettingsPersistenceException("Playlist presets are full")
        return saved + snapshot
    }

    fun decode(raw: String?): List<PartyPlaylistPreset> {
        if (raw == null || raw.length > MAX_STORED_CHARS) return emptyList()
        return try {
            val saved = Json.decodeFromString<List<PartyPlaylistPreset>>(raw)
            if (saved.size <= PartyPlaylistPreset.MAX_SAVED && saved.size == saved.toSet().size) saved else emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }
}
