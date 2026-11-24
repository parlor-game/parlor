package com.parlor.networking.room

import com.parlor.core.ids.PlayerId

/**
 * A room a peer has discovered on the local network (or via the in-memory
 * test bus). The fields are deliberately minimal — anything richer needs
 * to come from a join handshake, not advertisement.
 *
 * Emitted by [com.parlor.networking.transport.RoomTransport.discoverRooms];
 * consumed by `JoinRoomController` and the eventual Join UI in 9H-8.
 */
data class DiscoveredRoom(
    /** The room code players type / scan / pick — same string a host shows on screen. */
    val code: String,
    /** The display name the host advertised ("Adam's Room"). */
    val displayName: String,
    /** The host's own player id, if the transport surfaces it. */
    val hostPlayerId: PlayerId? = null,
    /** Public visual tag, independent of the private code. */
    val roomTag: String? = null,
    /** Opaque transport-owned candidate for a subsequent code-based join. */
    val selection: DiscoveredRoomSelection? = null,
)

interface DiscoveredRoomSelection
