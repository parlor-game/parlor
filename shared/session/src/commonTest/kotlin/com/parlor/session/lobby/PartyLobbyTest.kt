package com.parlor.session.lobby

import com.parlor.core.ids.GameId
import com.parlor.core.ids.PartyId
import com.parlor.core.ids.PlayerId
import com.parlor.core.result.Result
import com.parlor.networking.protocol.CommandStatus
import com.parlor.networking.protocol.GameProtocolRef
import com.parlor.networking.room.RoomMember
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PartyLobbyTest {
    private val host = PlayerId("host")
    private val peer = PlayerId("peer")
    private val other = PlayerId("other")
    private val mafia = PartyGame(GameProtocolRef(GameId("mafia"), 2), 2..7)
    private val word = PartyGame(GameProtocolRef(GameId("word-impostor"), 1), 2..6)
    private val small = PartyGame(GameProtocolRef(GameId("egyptian-dominoes"), 1), 2..2)
    private val games = setOf(mafia.protocol, word.protocol, small.protocol)

    @Test
    fun switching_games_and_rematching_keep_the_party_and_reject_old_match_traffic() {
        val party = partyWithPeer()
        val ready = party.setReady(peer, true, party.revision).value()
        val first = ready.prepareMatch().value()
        val firstId = assertIs<PartyPhase.Starting>(first.phase).match.id
        val playing = first.commitMatch(firstId).value()
        assertEquals(1L, playing.matchOrdinal)
        assertTrue(playing.acceptsGameplay(firstId, mafia.protocol, peer))
        assertEquals(Result.Failure(PartyRejection.InvalidPhase), playing.selectGame(word))

        val returned = playing.returnToLobby(firstId).value()
        val switched = returned.selectGame(word).value()
        assertEquals(party.id, switched.id)
        assertEquals(party.seats.keys, switched.seats.keys)
        assertEquals(Result.Failure(PartyRejection.StaleRevision), switched.setReady(peer, true, ready.revision))
        assertEquals(Result.Failure(PartyRejection.WaitingForReadiness), switched.prepareMatch())

        val wordStart = switched.setReady(peer, true, switched.revision).value().prepareMatch().value()
        val wordId = assertIs<PartyPhase.Starting>(wordStart.phase).match.id
        val second = wordStart.commitMatch(wordId).value()
        assertEquals(2L, second.matchOrdinal)
        assertNotEquals(firstId, wordId)
        assertFalse(second.acceptsGameplay(firstId, mafia.protocol, peer))
        assertFalse(second.acceptsGameplay(wordId, mafia.protocol, peer))
        assertTrue(second.acceptsGameplay(wordId, word.protocol, peer))
        assertEquals(Result.Failure(PartyRejection.StaleMatch), second.returnToLobby(firstId))

        val between = second.returnToLobby(wordId).value()
        val rematch = between.setReady(peer, true, between.revision).value().prepareMatch().value()
        val rematchId = assertIs<PartyPhase.Starting>(rematch.phase).match.id
        val third = rematch.commitMatch(rematchId).value()
        assertEquals(3L, third.matchOrdinal)
        assertNotEquals(wordId, rematchId)
        assertFalse(third.acceptsGameplay(wordId, word.protocol, peer))
        assertEquals(party.seats.keys, third.seats.keys)
    }

    @Test
    fun capacity_and_exact_version_are_checked_before_a_game_change() {
        val party = partyWithPeer(capabilities = setOf(mafia.protocol, word.protocol.copy(gameVersion = 2)))
        val full = party.admit(seat(other)).value()
        assertEquals(Result.Failure(PartyRejection.IneligibleCount), full.selectGame(small))
        assertEquals(Result.Failure(PartyRejection.IncompatibleGame), full.selectGame(word))
        assertEquals(Result.Failure(PartyRejection.DuplicatePlayer), full.admit(seat(peer)))
        assertEquals(word, full.removeSeat(peer).value().selectGame(word).value().game)
    }

    @Test
    fun canceled_attempt_never_reuses_its_match_id_and_reconnect_keeps_identity() {
        val party = partyWithPeer()
        val ready = party.setReady(peer, true, party.revision).value()
        val first = ready.prepareMatch().value()
        val expired = assertIs<PartyPhase.Starting>(first.phase).match
        val disconnected = first.updatePresence(peer, false).value()
        assertEquals(Result.Failure(PartyRejection.WaitingForReadiness), disconnected.commitMatch(expired.id))

        val reset = disconnected.returnToLobby(expired.id).value()
        assertEquals(0L, reset.matchOrdinal)
        assertEquals(Result.Failure(PartyRejection.IneligibleCount), reset.prepareMatch())
        val rejoined = reset.updatePresence(peer, true).value()
        assertFalse(rejoined.seats.getValue(peer).ready)
        val next = rejoined.setReady(peer, true, rejoined.revision).value().prepareMatch().value()
        val retry = assertIs<PartyPhase.Starting>(next.phase).match
        assertNotEquals(expired.id, retry.id)
        assertEquals(expired.ordinal, retry.ordinal)
        assertEquals(Result.Failure(PartyRejection.StaleMatch), next.commitMatch(expired.id))
        assertTrue(next.commitMatch(retry.id).value().acceptsGameplay(retry.id, mafia.protocol, peer))
    }

    @Test
    fun authenticated_readiness_is_ordered_and_survives_a_presence_change() = runTest {
        val party = partyWithPeer()
        val hostAuthority = PartyHostCoordinator(party)
        val command = PartyReadyCommand(party.id, 1L, party.revision, true)
        assertEquals(CommandStatus.Unauthorized, hostAuthority.ready(other, command).status)
        assertEquals(CommandStatus.SequenceGap, hostAuthority.ready(peer, command.copy(sequence = 2)).status)
        val accepted = hostAuthority.ready(peer, command)
        assertEquals(CommandStatus.Applied, accepted.status)
        assertEquals(2L, accepted.nextSequence)
        assertTrue(hostAuthority.state.value.seats.getValue(peer).ready)

        hostAuthority.presence(peer, false).value()
        hostAuthority.presence(peer, true).value()
        assertFalse(hostAuthority.state.value.seats.getValue(peer).ready)
        assertEquals(CommandStatus.Duplicate, hostAuthority.ready(peer, command).status)
        val stale = hostAuthority.ready(peer, command.copy(sequence = 2))
        assertEquals(CommandStatus.StaleRevision, stale.status)
        assertEquals(3L, stale.nextSequence)
        assertFalse(hostAuthority.state.value.seats.getValue(peer).ready)
        val fresh = command.copy(sequence = 3, expectedRevision = hostAuthority.state.value.revision)
        assertEquals(CommandStatus.Applied, hostAuthority.ready(peer, fresh).status)

        hostAuthority.close()
        assertEquals(CommandStatus.SessionEnded, hostAuthority.ready(peer, fresh).status)
    }

    private fun partyWithPeer(capabilities: Set<GameProtocolRef> = games): PartyLobby =
        PartyLobby.open(PartyId("1".repeat(32)), seat(host), mafia).admit(seat(peer, capabilities)).value()

    private fun seat(id: PlayerId, capabilities: Set<GameProtocolRef> = games) =
        PartySeat(RoomMember(id, id.raw, connected = true), capabilities)

    private fun Result<PartyLobby, PartyRejection>.value(): PartyLobby = assertIs<Result.Success<PartyLobby>>(this).data
}
