package com.parlor.networking.room

import com.parlor.core.ids.GameId
import com.parlor.networking.protocol.ProtocolVersion
import com.parlor.networking.transport.HostedGameProtocol

/**
 * The immutable, public part of a room's P2pKit discovery name. This is a hint
 * for selecting candidates, never proof of room identity or admission.
 */
object LobbyAdvertisement {
    const val ROOM_TAG_LENGTH: Int = 4
    const val LEGACY_PREFIX: String = "parlor-room|"

    private const val PREFIX = "parlor-lobby"
    private const val SCHEMA = "1"
    private const val MAX_LENGTH = 64
    private const val FIELD_COUNT = 7
    private const val MODE_INDEX = 3
    private const val GAME_INDEX = 4
    private const val GAME_VERSION_INDEX = 5
    private const val ROOM_TAG_INDEX = 6
    private const val MAX_PROTOCOL_COMPONENT = 99
    private const val MAX_GAME_VERSION = 9_999
    private const val PARTY_PROTOCOL_MAJOR = 5

    sealed interface Entry {
        data object Unrecognized : Entry
        data object Legacy : Entry
        data object UnsupportedSchema : Entry

        data class GameRoom(
            val protocol: ProtocolVersion,
            val game: HostedGameProtocol,
            val roomTag: String,
        ) : Entry

        data class PartyRoom(val protocol: ProtocolVersion, val roomTag: String) : Entry
    }

    /** Returns null when the game tuple or tag cannot fit in a discovery name. */
    fun encodeGame(
        game: HostedGameProtocol,
        roomTag: String,
        protocol: ProtocolVersion = ProtocolVersion(),
    ): String? {
        val name = "$PREFIX|$SCHEMA|${protocol.major}.${protocol.minor}|g|${game.gameId.raw}|${game.gameVersion}|$roomTag"
        return name.takeIf { parse(it) is Entry.GameRoom }
    }

    fun parse(name: String): Entry {
        if (name.startsWith(LEGACY_PREFIX)) return Entry.Legacy
        if (name.length > MAX_LENGTH || !name.startsWith("$PREFIX|")) return Entry.Unrecognized

        val fields = name.split('|')
        val schema = fields[1].canonicalNumber(Int.MAX_VALUE)
        if (schema == null || schema == 0) return Entry.Unrecognized
        if (fields[1] != SCHEMA) return Entry.UnsupportedSchema
        if (fields.size != FIELD_COUNT) return Entry.Unrecognized

        val protocol = parseProtocol(fields[2]) ?: return Entry.Unrecognized
        val tag = fields[ROOM_TAG_INDEX]
        if (tag.length != ROOM_TAG_LENGTH || tag.any { it !in RoomInputPolicy.ROOM_CODE_ALPHABET }) {
            return Entry.Unrecognized
        }
        return when (fields[MODE_INDEX]) {
            "g" -> parseGame(fields[GAME_INDEX], fields[GAME_VERSION_INDEX], protocol, tag)
            "p" -> parseParty(fields[GAME_INDEX], fields[GAME_VERSION_INDEX], protocol, tag)
            else -> Entry.Unrecognized
        }
    }

    private fun parseGame(id: String, version: String, protocol: ProtocolVersion, tag: String): Entry {
        if (!GameId.isCanonical(id)) return Entry.Unrecognized
        val gameVersion = version.canonicalNumber(MAX_GAME_VERSION)
        if (gameVersion == null || gameVersion == 0) return Entry.Unrecognized
        return Entry.GameRoom(protocol, HostedGameProtocol(GameId(id), gameVersion), tag)
    }

    private fun parseParty(id: String, version: String, protocol: ProtocolVersion, tag: String): Entry =
        if (id == "-" && version == "-" && protocol.major >= PARTY_PROTOCOL_MAJOR) {
            Entry.PartyRoom(protocol, tag)
        } else {
            Entry.Unrecognized
        }

    private fun parseProtocol(raw: String): ProtocolVersion? {
        val parts = raw.split('.')
        if (parts.size != 2) return null
        val major = parts[0].canonicalNumber(MAX_PROTOCOL_COMPONENT)
        val minor = parts[1].canonicalNumber(MAX_PROTOCOL_COMPONENT)
        return if (major != null && major > 0 && minor != null) ProtocolVersion(major, minor) else null
    }

    private fun String.canonicalNumber(max: Int): Int? =
        toIntOrNull()?.takeIf { it in 0..max && it.toString() == this }
}
