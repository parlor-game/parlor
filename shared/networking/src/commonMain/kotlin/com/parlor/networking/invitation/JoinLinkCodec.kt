package com.parlor.networking.invitation

import com.parlor.core.ids.GameId
import com.parlor.core.ids.PartyId
import com.parlor.core.ids.RoomId
import com.parlor.networking.protocol.PARTY_WIRE_VERSION
import com.parlor.networking.protocol.ProtocolVersion
import com.parlor.networking.room.LobbyAdvertisement
import com.parlor.networking.room.RoomInputPolicy
import com.parlor.networking.transport.HostedGameProtocol

/** A same-LAN invitation. The code is private and the fingerprint is a connection pin. */
@ConsistentCopyVisibility
data class JoinInvitation internal constructor(
    val game: HostedGameProtocol,
    val roomId: RoomId,
    val roomTag: String,
    val code: String,
    val hostFingerprint: String,
) {
    override fun toString(): String = "JoinInvitation(redacted)"
}

/** A party link identifies the group, even if the host later changes the selected game. */
@ConsistentCopyVisibility
data class PartyJoinInvitation internal constructor(
    val partyId: PartyId,
    val roomId: RoomId,
    val roomTag: String,
    val code: String,
    val hostFingerprint: String,
) {
    override fun toString(): String = "PartyJoinInvitation(redacted)"
}

object JoinLinkCodec {
    private const val PREFIX = "parlor://join?"
    private const val MAX_LENGTH = 256
    private const val MAX_GAME_VERSION = 9_999
    private const val FINGERPRINT_PREFIX = "p2f1-"
    private const val BASE32_LENGTH = 52
    private const val SCHEMA = "1"
    private const val PARTY_SCHEMA = "2"
    private const val BASE32_LOWER = "abcdefghijklmnopqrstuvwxyz234567"
    private val parameters = setOf("v", "g", "gv", "p", "c", "r", "t", "f")
    private val partyParameters = setOf("v", "party", "p", "c", "r", "t", "f")
    private val roomIdPattern = Regex("[0-9a-f]{32}")

    sealed interface Decoded {
        data object Invalid : Decoded
        data object IncompatibleVersion : Decoded
        data class Valid(val invitation: JoinInvitation) : Decoded
    }

    fun encode(
        game: HostedGameProtocol,
        roomId: RoomId,
        roomTag: String,
        code: String,
        hostFingerprint: String,
    ): String? {
        val protocol = ProtocolVersion()
        val link = "${PREFIX}v=$SCHEMA&g=${game.gameId.raw}&gv=${game.gameVersion}" +
            "&p=${protocol.major}.${protocol.minor}&c=$code&r=${roomId.raw}&t=$roomTag&f=$hostFingerprint"
        return link.takeIf { decode(it) is Decoded.Valid }
    }

    fun encodeParty(
        partyId: PartyId,
        roomId: RoomId,
        roomTag: String,
        code: String,
        hostFingerprint: String,
    ): String? {
        val protocol = PARTY_WIRE_VERSION
        val link = "${PREFIX}v=$PARTY_SCHEMA&party=${partyId.raw}" +
            "&p=${protocol.major}.${protocol.minor}&c=$code&r=${roomId.raw}&t=$roomTag&f=$hostFingerprint"
        return link.takeIf { decodeParty(it) != null }
    }

    fun decodeParty(raw: String): PartyJoinInvitation? {
        if (!raw.startsWith(PREFIX) || raw.length > MAX_LENGTH) return null
        val fields = parseFields(raw.removePrefix(PREFIX)) ?: return null
        val protocol = PARTY_WIRE_VERSION
        if (fields.keys != partyParameters || fields["v"] != PARTY_SCHEMA ||
            fields["p"] != "${protocol.major}.${protocol.minor}"
        ) return null
        val id = fields.getValue("party")
        if (!roomIdPattern.matches(id)) return null
        val target = parseTarget(fields) ?: return null
        return PartyJoinInvitation(PartyId(id), target.roomId, target.tag, target.code, target.fingerprint)
    }

    fun decode(raw: String): Decoded {
        if (!raw.startsWith(PREFIX) || raw.length > MAX_LENGTH) return Decoded.Invalid
        val fields = parseFields(raw.removePrefix(PREFIX)) ?: return Decoded.Invalid
        val schema = fields["v"] ?: return Decoded.Invalid
        if (schema != SCHEMA) {
            val number = schema.toIntOrNull()
            return if (number != null && number > 1 && schema == number.toString()) {
                Decoded.IncompatibleVersion
            } else {
                Decoded.Invalid
            }
        }
        if (fields.keys != parameters) return Decoded.Invalid
        val protocol = ProtocolVersion()
        if (fields["p"] != "${protocol.major}.${protocol.minor}") {
            return if (isCanonicalProtocol(fields.getValue("p"))) Decoded.IncompatibleVersion else Decoded.Invalid
        }
        return parseInvitation(fields)?.let { Decoded.Valid(it) } ?: Decoded.Invalid
    }

    private fun parseFields(query: String): Map<String, String>? {
        val fields = mutableMapOf<String, String>()
        for (field in query.split('&')) {
            val pair = field.split('=')
            if (pair.size != 2) return null
            val (key, value) = pair
            if (!key.isUnreserved() || !value.isUnreserved() || fields.put(key, value) != null) return null
        }
        return fields
    }

    private fun parseInvitation(fields: Map<String, String>): JoinInvitation? {
        val game = fields.getValue("g")
        val version = fields.getValue("gv").toIntOrNull() ?: return null
        if (!GameId.isCanonical(game) || version !in 1..MAX_GAME_VERSION || version.toString() != fields["gv"]) return null
        val target = parseTarget(fields) ?: return null
        return JoinInvitation(HostedGameProtocol(GameId(game), version), target.roomId, target.tag, target.code, target.fingerprint)
    }

    private data class LinkTarget(val roomId: RoomId, val tag: String, val code: String, val fingerprint: String)

    private fun parseTarget(fields: Map<String, String>): LinkTarget? {
        val code = fields.getValue("c")
        val roomId = fields.getValue("r")
        val tag = fields.getValue("t")
        val fingerprint = fields.getValue("f")
        if (!RoomInputPolicy.isValidRoomCode(code) || !roomIdPattern.matches(roomId) ||
            !isValidTag(tag) || !isValidFingerprint(fingerprint)
        ) {
            return null
        }
        return LinkTarget(RoomId(roomId), tag, code, fingerprint)
    }

    private fun isCanonicalProtocol(raw: String): Boolean {
        val parts = raw.split('.')
        if (parts.size != 2) return false
        val major = parts[0].toIntOrNull() ?: return false
        val minor = parts[1].toIntOrNull() ?: return false
        return major > 0 && minor >= 0 && "$major.$minor" == raw
    }

    private fun isValidTag(tag: String): Boolean =
        tag.length == LobbyAdvertisement.ROOM_TAG_LENGTH && tag.all { it in RoomInputPolicy.ROOM_CODE_ALPHABET }

    private fun isValidFingerprint(raw: String): Boolean {
        if (!raw.startsWith(FINGERPRINT_PREFIX)) return false
        val body = raw.removePrefix(FINGERPRINT_PREFIX)
        return body.length == BASE32_LENGTH && body.all { it in BASE32_LOWER } && body.last() in "aq"
    }

    private fun String.isUnreserved(): Boolean = isNotEmpty() &&
        all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it in "-._~" }
}
