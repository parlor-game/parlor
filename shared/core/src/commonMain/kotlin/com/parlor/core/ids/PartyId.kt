package com.parlor.core.ids

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/** Logical group lifetime, independent of the LAN room and each game's match. */
@JvmInline
@Serializable
value class PartyId(val raw: String) {
    init {
        require(raw.length == ID_LENGTH && raw.all { it in '0'..'9' || it in 'a'..'f' }) {
            "Invalid party identifier"
        }
    }

    override fun toString(): String = "PartyId(redacted)"

    private companion object {
        const val ID_LENGTH = 32
    }
}
