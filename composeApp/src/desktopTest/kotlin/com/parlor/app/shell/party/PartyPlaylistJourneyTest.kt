package com.parlor.app.shell.party

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.parlor.app.shell.game.DefaultGameShellRegistry
import com.parlor.app.shell.game.DominoGameShellBinding
import com.parlor.app.shell.game.LastLightGameShellBinding
import com.parlor.core.ids.PartyId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.result.Result
import com.parlor.designsystem.localization.AppLanguage
import com.parlor.designsystem.localization.ProvideAppLanguage
import com.parlor.designsystem.theme.ParlorTheme
import com.parlor.games.dominoes.DominoDefinition
import com.parlor.games.lastlight.LastLightDefinition
import com.parlor.networking.protocol.GameProtocolRef
import com.parlor.networking.protocol.HostMessage
import com.parlor.networking.protocol.PARTY_WIRE_VERSION
import com.parlor.networking.protocol.PartyProtocolHeader
import com.parlor.networking.protocol.PeerMessage
import com.parlor.networking.protocol.RoomMessage
import com.parlor.networking.protocol.RoomMessageCodec
import com.parlor.networking.protocol.SessionEndReason
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.room.PartyMatchAdmission
import com.parlor.networking.room.PeerEvent
import com.parlor.networking.room.RoomInfo
import com.parlor.networking.room.RoomMember
import com.parlor.networking.room.SendTarget
import com.parlor.networking.transport.HostConfig
import com.parlor.networking.transport.ResumablePartySessionInfo
import com.parlor.networking.transport.ResumableSessionSelection
import com.parlor.networking.transport.RoomTransport
import com.parlor.networking.transport.TransportCapability
import com.parlor.session.lobby.PartyGame
import com.parlor.session.lobby.ProcessPartySession
import com.parlor.session.lobby.ProcessPartySessionOwner
import com.parlor.session.multidevice.ProcessMultiplayerSession
import com.parlor.session.multidevice.RetainedMultiplayerRuntime
import com.parlor.storage.settings.InMemorySettingsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class PartyPlaylistJourneyTest {
    private val hostSettings = InMemorySettingsStore()
    private val guestSettings = InMemorySettingsStore()
    private val lastLight = LastLightGameShellBinding(LastLightDefinition())
    private val dominoes = DominoGameShellBinding(DominoDefinition())
    private val registry = DefaultGameShellRegistry(listOf(lastLight, dominoes))
    private val games = registry.all.map { binding ->
        val contract = checkNotNull(binding.multiplayerContract)
        PartyGame(GameProtocolRef(contract.gameId, contract.gameVersion), contract.supportedPlayerCounts)
    }

    @Test
    fun host_builds_a_playlist_and_the_rejoined_guest_sees_the_same_result_once() = runTest {
        val wire = PlaylistWire(games.map(PartyGame::protocol))
        val hostOwner = ProcessPartySessionOwner(wire, backgroundScope)
        val opened = assertIs<Result.Success<ProcessPartySession>>(hostOwner.host("Host", games.toSet(), games.first()))
        val host = assertIs<ProcessPartySession.Host>(opened.data)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { host.lobby.collect {} }
        runCurrent()
        editThroughHostLobby(hostOwner, host, wire)
        runCurrent()

        wire.markGuestReady()
        runCurrent()
        val match = assertIs<Result.Success<ProcessMultiplayerSession>>(host.start()).data
        match.installTerminal()
        val id = checkNotNull(match.room.matchProtocol).sessionId
        assertEquals(Result.Success(Unit), host.confirmStarted(id))
        wire.memberConnected(false)
        runCurrent()
        assertEquals(Result.Success(Unit), host.returnToLobby(id))
        assertEquals(Result.Success(Unit), host.returnToLobby(id))

        val rejoiningOwner = ProcessPartySessionOwner(wire, backgroundScope)
        val resumed = assertIs<Result.Success<ProcessPartySession>>(rejoiningOwner.resume(wire.savedMembership, games.toSet()))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { resumed.data.lobby.collect {} }
        wire.memberConnected(true)
        runCurrent()
        wire.announceRejoin()
        runCurrent()
        checkNotNull(wire.latest).let { wire.deliver(it) }
        runCurrent()
        verifyBothLobbies(hostOwner, rejoiningOwner, wire)
    }

    private fun editThroughHostLobby(owner: ProcessPartySessionOwner, host: ProcessPartySession.Host, wire: PlaylistWire) =
        runComposeUiTest {
            setContent { RenderLobby(owner, wire, hostSettings) }
            onNodeWithText("Make a playlist").performScrollTo().performClick()
            onNodeWithText("Save playlist").performScrollTo().assertIsNotEnabled()
            onNodeWithText("Add Egyptian Dominoes").performScrollTo().performClick()
            onNodeWithText("Remove 1 · Egyptian Dominoes").performScrollTo().performClick()
            onNodeWithText("Add Last Light").performScrollTo().performClick()
            onNodeWithText("Add Egyptian Dominoes").performScrollTo().performClick()
            onNodeWithText("Save as preset").performScrollTo().performClick()
            onNodeWithText("Load preset 1").assertExists()
            onNodeWithText("Save as preset").assertIsNotEnabled()
            onNodeWithText("Remove 1 · Last Light").performScrollTo().performClick()
            onNodeWithText("Load preset 1").performScrollTo().performClick()
            onNodeWithText("Remove 1 · Last Light").assertExists()
            onNodeWithText("Remove 2 · Egyptian Dominoes").assertExists()
            onNodeWithText("Remove preset 1").performScrollTo().performClick()
            onNodeWithText("Load preset 1").assertDoesNotExist()
            onNodeWithText("Save playlist").performScrollTo().performClick()
            waitUntil { host.playlist?.games == games }
        }

    private suspend fun ProcessMultiplayerSession.installTerminal() {
        getOrCreateRuntime("terminal-test") {
            object : RetainedMultiplayerRuntime {
                override val runtimeKind = "terminal-test"
                override val isMatchCompleted = true
                override suspend fun terminate(reason: SessionEndReason) = Unit
                override suspend fun close() = Unit
            }
        }
    }

    private fun verifyBothLobbies(host: ProcessPartySessionOwner, guest: ProcessPartySessionOwner, wire: PlaylistWire) =
        runComposeUiTest {
            val owner = mutableStateOf(host)
            setContent {
                key(owner.value) { RenderLobby(owner.value, wire, if (owner.value === host) hostSettings else guestSettings) }
            }
            for (session in listOf(host, guest)) {
                runOnIdle { owner.value = session }
                onNodeWithText("Next game · Egyptian Dominoes").performScrollTo().assertIsDisplayed()
                onNodeWithText("1 of 2 matches completed").assertExists()
                onAllNodesWithText("Match 1 · Last Light completed").assertCountEquals(1)
            }
            onNodeWithText("Select next game").assertDoesNotExist()
            onNodeWithText("Ready").performScrollTo().assertIsNotEnabled()
            runOnIdle { owner.value = host }
            onNodeWithText("Select next game").performScrollTo().assertIsDisplayed()
        }

    @Composable
    private fun RenderLobby(owner: ProcessPartySessionOwner, transport: RoomTransport, settings: InMemorySettingsStore) {
        ProvideAppLanguage(AppLanguage.English) {
            ParlorTheme(reducedMotion = true) {
                Box(Modifier.size(320.dp, 640.dp)) {
                    PartyScreen(owner, transport, registry, settings, invitation = null, privateContentCovered = false,
                        backRequest = 0, onExit = {})
                }
            }
        }
    }
}

/** Crosses the real party codec while leaving native transport and gameplay to device acceptance. */
private class PlaylistWire(private val games: List<GameProtocolRef>) : RoomTransport {
    override val capability = TransportCapability(false, 0, 16_384)
    private val hostId = PlayerId("host")
    private val guestId = PlayerId("guest")
    private var hostedId: PartyId? = null
    private val party get() = checkNotNull(hostedId)
    private val selection = object : ResumableSessionSelection {}
    val savedMembership get() = ResumablePartySessionInfo(party, "Guest", Long.MAX_VALUE, selection)
    private val hostInput = Channel<RoomMessage>(32)
    private val guestInput = Channel<RoomMessage>(32)
    private val events = MutableSharedFlow<PeerEvent>(extraBufferCapacity = 8)
    private val codec = RoomMessageCodec()
    private val members = MutableStateFlow(listOf(RoomMember(guestId, "Guest", true, games)))
    var latest: HostMessage.PartySnapshot? = null
        private set
    private var guestAttached = false
    private var frozen: SessionId? = null

    override suspend fun host(config: HostConfig): Result<LocalRoom, NetError> {
        hostedId = checkNotNull(config.partyProtocol).id
        return Result.Success(Side(isHost = true))
    }

    override suspend fun join(code: String, displayName: String): Result<LocalRoom, NetError> = Result.Failure(NetError.NotConnected)

    override suspend fun resumeParty(expectedPartyId: PartyId, selection: ResumableSessionSelection): Result<LocalRoom, NetError> {
        check(expectedPartyId == party && selection === this.selection)
        guestAttached = true
        return Result.Success(Side(isHost = false))
    }

    fun memberConnected(connected: Boolean) {
        members.value = listOf(members.value.single().copy(connected = connected))
    }

    suspend fun announceRejoin() = events.emit(PeerEvent.PeerReconnected(guestId, "Guest"))

    suspend fun markGuestReady() {
        val current = checkNotNull(latest)
        val frame = PeerMessage.PartySetReady(
            PartyProtocolHeader(PARTY_WIRE_VERSION, party, "playlist-ready-test", 0), guestId,
            current.nextExpectedClientSequence, current.revision, true,
        )
        hostInput.send(codec.decode(codec.encode(frame)))
    }

    suspend fun deliver(message: HostMessage) = guestInput.send(codec.decode(codec.encode(message)))

    private inner class Side(override val isHost: Boolean) : LocalRoom {
        override val info = MutableStateFlow(RoomInfo("ABCDEF", "Host", hostId,
            if (isHost) RoomInfo.Status.Hosting else RoomInfo.Status.Joined, partyId = party))
        override val selfPlayerId = if (isHost) hostId else guestId
        override val members = this@PlaylistWire.members
        override val incoming = (if (isHost) hostInput else guestInput).receiveAsFlow()
        override val peerEvents = events
        override val partyMatches = if (isHost) object : PartyMatchAdmission {
            override val partyId = party
            override suspend fun selectGame(expected: GameProtocolRef, next: GameProtocolRef, seatLimit: Int,
                expectedMembers: Set<PlayerId>): Result<Unit, NetError> = Result.Success(Unit)

            override suspend fun freezeMatch(id: SessionId, expectedPeers: Set<PlayerId>): Result<List<RoomMember>, NetError> {
                check(expectedPeers == setOf(guestId))
                frozen = id
                return Result.Success(members.value)
            }

            override suspend fun reopenMatch(id: SessionId): Result<Unit, NetError> {
                check(frozen == id)
                frozen = null
                return Result.Success(Unit)
            }
        } else null

        override suspend fun send(target: SendTarget, message: HostMessage): Result<Unit, NetError> {
            check(isHost && target == SendTarget.Direct(guestId))
            if (message is HostMessage.PartySnapshot) latest = message
            if (guestAttached) deliver(message)
            return Result.Success(Unit)
        }

        override suspend fun sendToHost(message: PeerMessage): Result<Unit, NetError> {
            hostInput.send(codec.decode(codec.encode(message)))
            return Result.Success(Unit)
        }

        override suspend fun leave() = Unit
    }
}
