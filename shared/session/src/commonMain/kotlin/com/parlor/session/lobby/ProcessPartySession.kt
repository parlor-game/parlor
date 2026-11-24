package com.parlor.session.lobby

import com.parlor.core.ids.GameId
import com.parlor.core.ids.PartyId
import com.parlor.core.ids.PlayerId
import com.parlor.core.ids.SessionId
import com.parlor.core.result.Result
import com.parlor.networking.invitation.PartyJoinInvitation
import com.parlor.networking.protocol.CommandStatus
import com.parlor.networking.protocol.GameProtocolRef
import com.parlor.networking.protocol.PartyAdmissionContext
import com.parlor.networking.protocol.PublicPartyLobby
import com.parlor.networking.protocol.SessionEndReason
import com.parlor.networking.room.LocalRoom
import com.parlor.networking.room.NetError
import com.parlor.networking.room.RoomInputPolicy
import com.parlor.networking.security.SecureIds
import com.parlor.networking.transport.ApprovalWait
import com.parlor.networking.transport.HostConfig
import com.parlor.networking.transport.HostedGameProtocol
import com.parlor.networking.transport.HostedPartyProtocol
import com.parlor.networking.transport.JoinConfig
import com.parlor.networking.transport.ResumablePartySessionInfo
import com.parlor.networking.transport.RoomTransport
import com.parlor.session.multidevice.MultiplayerSessionRoute
import com.parlor.session.multidevice.ProcessMultiplayerSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Process-owned party entry. Only game runtimes receive a room, and that room is match-scoped. */
class ProcessPartySessionOwner(private val transport: RoomTransport, private val scope: CoroutineScope) {
    private val entry = Mutex()
    private val current = MutableStateFlow<ProcessPartySession?>(null)
    val session = current.asStateFlow()

    suspend fun host(
        displayName: String,
        games: Set<PartyGame>,
        initial: PartyGame,
        roomName: String? = null,
    ): Result<ProcessPartySession, NetError> =
        open {
            if (!games.hasValidCatalog() || initial !in games || !displayName.isCanonicalDisplayName() ||
                (roomName != null && !roomName.isCanonicalDisplayName())
            ) {
                return@open Result.Failure(NetError.InvalidInput)
            }
            val identity = PartyId(SecureIds.id128())
            val config = HostConfig(
                hostDisplayName = displayName,
                maxRemotePlayers = games.maxOf { it.playerCounts.last } - 1,
                partyProtocol = HostedPartyProtocol(
                    identity,
                    HostedGameProtocol(initial.protocol.gameId, initial.protocol.gameVersion),
                    initial.playerCounts.last,
                ),
            )
            when (val result = transport.host(config)) {
                is Result.Failure -> result
                is Result.Success -> Result.Success(ProcessPartySession.Host(result.data, initial, games, scope, roomName))
            }
        }

    suspend fun join(
        displayName: String,
        code: String,
        games: Set<PartyGame>,
        invitation: PartyJoinInvitation? = null,
        onApprovalPending: ((ApprovalWait) -> Unit)? = null,
    ): Result<ProcessPartySession, NetError> = open {
        if (!games.hasValidCatalog() || !displayName.isCanonicalDisplayName() || !RoomInputPolicy.isValidRoomCode(code)) {
            return@open Result.Failure(NetError.InvalidInput)
        }
        val target = PartyAdmissionContext(invitation?.partyId, games.protocols().toList())
        val config = JoinConfig(code, displayName, party = target, partyInvitation = invitation, onApprovalPending = onApprovalPending)
        installPeer(transport.join(config), displayName, games.protocols())
    }

    suspend fun resume(saved: ResumablePartySessionInfo, games: Set<PartyGame>): Result<ProcessPartySession, NetError> =
        open {
            if (!games.hasValidCatalog() || !saved.displayName.isCanonicalDisplayName()) {
                return@open Result.Failure(NetError.InvalidInput)
            }
            installPeer(transport.resumeParty(saved.partyId, saved.selection), saved.displayName, games.protocols())
        }

    suspend fun leave(session: ProcessPartySession): Result<Unit, NetError> = entry.withLock {
        if (current.value !== session) return@withLock Result.Failure(NetError.StaleRoom)
        withContext(NonCancellable) {
            session.leave().also { if (it is Result.Success) current.value = null }
        }
    }

    private suspend fun open(
        openRoom: suspend () -> Result<ProcessPartySession, NetError>,
    ): Result<ProcessPartySession, NetError> = entry.withLock {
        if (current.value != null) return@withLock Result.Failure(NetError.AlreadyConnected)
        openRoom().also { if (it is Result.Success) current.value = it.data }
    }

    private fun installPeer(
        result: Result<LocalRoom, NetError>, name: String, games: Set<GameProtocolRef>,
    ): Result<ProcessPartySession, NetError> = when (result) {
        is Result.Failure -> result
        is Result.Success -> Result.Success(ProcessPartySession.Peer(result.data, name, games, scope))
    }
}

sealed class ProcessPartySession private constructor(room: LocalRoom) {
    val roomInfo = room.info
    val lifecycle = room.lifecycle
    val foregroundReady = room.foregroundReady
    val selfPlayerId = room.selfPlayerId
    abstract val lobby: StateFlow<PublicPartyLobby?>
    abstract val match: StateFlow<ProcessMultiplayerSession?>
    internal abstract suspend fun leave(): Result<Unit, NetError>

    class Host internal constructor(
        private val physical: LocalRoom,
        initial: PartyGame,
        private val games: Set<PartyGame>,
        parentScope: CoroutineScope,
        roomName: String?,
    ) : ProcessPartySession(physical) {
        private val scope = CoroutineScope(parentScope.coroutineContext + Job(parentScope.coroutineContext[Job]))
        private val owner = PartyHostRoomBinding(physical, initial, games.protocols(), scope, roomName = roomName)
        private val transition = Mutex()
        private val playing = MutableStateFlow<ProcessMultiplayerSession?>(null)
        private val completedGame = MutableStateFlow<GameId?>(null)
        override val lobby = owner.state.map(PartyLobby::publicLobby)
            .stateIn(scope, SharingStarted.WhileSubscribed(0), owner.state.value.publicLobby())
        override val match = playing.asStateFlow()
        val approvals = owner.pending
        val lastCompletedGame = completedGame.asStateFlow()

        suspend fun createInvitation(): String? = physical.createJoinLink()
        suspend fun approve(player: PlayerId): Result<Unit, NetError> = owner.approve(player)
        suspend fun reject(player: PlayerId): Result<Unit, NetError> = owner.reject(player)

        suspend fun selectGame(game: PartyGame): Result<Unit, PartyRejection> =
            if (game in games) owner.selectGame(game).toUnit() else Result.Failure(PartyRejection.IncompatibleGame)

        suspend fun start(): Result<ProcessMultiplayerSession, PartyRejection> = transition.withLock {
            withContext(NonCancellable) {
                when (val prepared = owner.prepareMatch()) {
                    is Result.Failure -> prepared
                    is Result.Success -> {
                        val room = prepared.data
                        val route = MultiplayerSessionRoute.host(room.matchProtocol.gameId, room.info.value.hostDisplayName)
                        val session = ProcessMultiplayerSession(
                            route, room, SecureIds.randomLong(), scope, room.frozenMembers,
                        )
                        playing.value = session
                        Result.Success(session)
                    }
                }
            }
        }

        suspend fun confirmStarted(id: SessionId): Result<Unit, PartyRejection> = owner.commitMatch(id).toUnit()

        suspend fun returnToLobby(
            id: SessionId,
            reason: SessionEndReason = SessionEndReason.Completed,
        ): Result<Unit, PartyRejection> = transition.withLock {
            val session = playing.value?.takeIf { it.room.matchProtocol?.sessionId == id }
                ?: return@withLock Result.Failure(PartyRejection.StaleMatch)
            val runtime = session.runtime.value
            if (reason == SessionEndReason.Completed &&
                (owner.state.value.phase !is PartyPhase.Playing || runtime?.isMatchCompleted != true)
            ) {
                return@withLock Result.Failure(PartyRejection.InvalidPhase)
            }
            withContext(NonCancellable) {
                runtime?.terminate(reason)
                owner.returnToLobby(id).toUnit().also {
                    if (it is Result.Success) {
                        if (reason == SessionEndReason.Completed) completedGame.value = session.route.gameId
                        session.closeRuntimeAndScope()
                        playing.value = null
                    }
                }
            }
        }

        internal override suspend fun leave(): Result<Unit, NetError> = transition.withLock {
            owner.leave().also {
                if (it is Result.Success) {
                    playing.value?.closeRuntimeAndScope()
                    playing.value = null
                    scope.cancel()
                }
            }
        }
    }

    class Peer internal constructor(
        physical: LocalRoom,
        private val displayName: String,
        supported: Set<GameProtocolRef>,
        parentScope: CoroutineScope,
    ) : ProcessPartySession(physical) {
        private val scope = CoroutineScope(parentScope.coroutineContext + Job(parentScope.coroutineContext[Job]))
        private val owner = PartyPeerRoomBinding(physical, supported, scope)
        private val transition = Mutex()
        private val installed = MutableStateFlow<ProcessMultiplayerSession?>(null)
        override val match = installed.asStateFlow()
        override val lobby = owner.state.map { it?.lobby }.stateIn(scope, SharingStarted.WhileSubscribed(0), null)

        init {
            scope.launch {
                owner.match.collect { room -> transition.withLock { attachMatchRoom(room) } }
            }
        }

        private suspend fun attachMatchRoom(room: LocalRoom?) {
            val current = installed.value
            if (current?.room === room) return
            current?.closeRuntimeAndScope()
            val protocol = room?.matchProtocol
            installed.value = if (room != null && protocol != null) {
                val route = MultiplayerSessionRoute.peer(protocol.gameId, displayName, room.info.value.code)
                ProcessMultiplayerSession(route, room, null, scope)
            } else {
                null
            }
        }

        suspend fun setReady(ready: Boolean): Result<CommandStatus, NetError> = owner.setReady(ready)

        internal override suspend fun leave(): Result<Unit, NetError> = transition.withLock {
            owner.leave().also {
                if (it is Result.Success) {
                    attachMatchRoom(null)
                    scope.cancel()
                }
            }
        }
    }
}

private fun Set<PartyGame>.protocols(): Set<GameProtocolRef> = map(PartyGame::protocol).toSet()

private fun Set<PartyGame>.hasValidCatalog(): Boolean =
    protocols().size == size && PartyAdmissionContext(supportedGames = protocols().toList()).isValid()

private fun String.isCanonicalDisplayName(): Boolean =
    this == RoomInputPolicy.normalizeDisplayName(this) && RoomInputPolicy.isValidDisplayName(this)

private fun <T, E> Result<T, E>.toUnit(): Result<Unit, E> = when (this) {
    is Result.Success -> Result.Success(Unit)
    is Result.Failure -> this
}
