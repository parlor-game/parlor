package com.parlor.core.ids

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

/**
 * Typed identifier value classes. Prevents accidental mixing — passing a PlayerId
 * where a CaseId is expected fails at compile time.
 */

@JvmInline
@Serializable
value class GameId(val raw: String) {
    init { require(raw.isNotBlank()) { "GameId must not be blank" } }

    fun requireCanonical() {
        require(isCanonical(raw)) { "Expected a canonical game identifier" }
    }

    companion object {
        private const val MAX_CANONICAL_LENGTH = 32
        private val canonicalGrammar = Regex("[a-z][a-z0-9]*(-[a-z0-9]+)*")

        /** Game IDs are case-sensitive and never normalized at a boundary. */
        fun isCanonical(raw: String): Boolean =
            raw.length in 1..MAX_CANONICAL_LENGTH &&
                raw != "party" && !raw.startsWith("parlor") &&
                canonicalGrammar.matches(raw)
    }
}

/** Identity of one physical room. Distinct from its human code and each match SessionId. */
@JvmInline
@Serializable
value class RoomId(val raw: String) {
    init {
        require(raw.length == HEX_LENGTH && raw.all { it in '0'..'9' || it in 'a'..'f' }) {
            "Invalid room identifier"
        }
    }

    override fun toString(): String = "RoomId(redacted)"

    private companion object {
        const val HEX_LENGTH = 32
    }
}

@JvmInline
@Serializable
value class ModeId(val raw: String) {
    init { require(raw.isNotBlank()) { "ModeId must not be blank" } }
}

@JvmInline
@Serializable
value class PlayerId(val raw: String) {
    init { require(raw.isNotBlank()) { "PlayerId must not be blank" } }
}

@JvmInline
@Serializable
value class SessionId(val raw: String) {
    init { require(raw.isNotBlank()) { "SessionId must not be blank" } }
}

@JvmInline
@Serializable
value class CaseId(val raw: String) {
    init { require(raw.isNotBlank()) { "CaseId must not be blank" } }
}

@JvmInline
@Serializable
value class CharacterId(val raw: String) {
    init { require(raw.isNotBlank()) { "CharacterId must not be blank" } }
}

@JvmInline
@Serializable
value class ClueId(val raw: String) {
    init { require(raw.isNotBlank()) { "ClueId must not be blank" } }
}
