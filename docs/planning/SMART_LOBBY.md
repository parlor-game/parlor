# Smart Lobby implementation plan

Document status: **implementation plan.** The accepted
[boundary decisions](../adr/0003-game-room-and-party-boundaries.md) govern
the implementation. Production source, executable tests and release evidence
determine which phases are available.

| Field | Value |
|---|---|
| Baseline | `origin/main` = `d9f78320ffdfa3f4b22342f975166b214dca9671` (Merge PR #349 `ux/mobile-party-hub`), fetched 2026-11-22 |
| Runtime protocol at baseline | `4.2`, exact major.minor compatibility (`Protocol.kt:29-46`) |
| Transport at baseline | P2pKit `0.7.0-rc3` (`p2p-core`, `p2p-transport-lan`) from Maven Central, strict verification |
| Shipping clients | Android API 26+ and iOS 16+; Desktop is development/test only |
| Scope | Discovery, admission, QR/link, party/match identity, lifecycle, wire, migration, test matrix |
| Out of scope | Public-internet play; raw IP/manual endpoints (ADR-0002); spectators; host migration; Party Night, profile/stats and outcomes (implications only, §17) |

Paths are relative to the repository root. `file:line` references point at the
baseline SHA above.

### Local implementation checkpoint

The game-room and party transport work is consolidated locally on
`wip/smart-lobby-party-runtime` for one PR after the remaining app work.
The protocol and user interface on protected `main` still follow the
baseline above until the complete change is reviewed and integrated.

| Area | Implementation in this branch | Remaining qualification |
|---|---|---|
| Admission | Exact game/version admission before approval, a monotonic host deadline shown in all six game lobbies, and an authenticated game/room preview before the approval wait. The party join form shows the authenticated host and a local estimate of the time remaining. Protected rejoin binds the exact room/game and discards obsolete v1 credentials. | Add the authenticated countdown to the six standalone join screens. Exercise approval, decline, timeout and cancellation on Android and iOS. |
| Discovery | Game-scoped advertisements and dial selection, bounded nearby room rows, public room tags on both host and peer UI, and exact candidate selection in all six game flows. | Native discovery and removal; the legacy-version banner, network-change refresh and quarantine acceptance rows are still outstanding. |
| Invitations | Local QR render, explicit OS sharing, strict v1 game links and v2 party links. Both pin the authenticated host and the exact room. Party links route to a separate guarded entry and do not encode the current game. | Verify the system camera, share sheet, native deep linking and join/reject behavior on devices. |
| Party and rematch | The app catalog opens a process-owned party with its own authorization and resume entry. Party readiness and game selection use host snapshots. All six bindings attach their existing game coordinators to a frozen match. Match completion returns to the party, where the host can start a rematch or explicitly select another game. The Linux two-process probes on `86252cb0` and `41b28c03` preserved the same authenticated seat, applied a game switch, and exchanged scoped traffic on the original connection after committing the first match end. | Finish full gameplay qualification across consecutive party matches for all six games. Qualify Android↔Android, Android↔iOS, iOS↔Android and iOS↔iOS on real same-LAN hardware. |

---

## 1. Summary

Smart Lobby replaces "pick a game, then type a code and hope it is the right
room" with game-aware discovery, explicit room identity, scan-or-tap joining,
and a persistent party that can play several matches. The plan is built
around one constraint found in the pinned P2pKit artifacts: **an
advertisement can carry only the P2pKit device name, and that name cannot
change while the kit runs** (§3.2). Everything that can change during a room's
lifetime (open/started, seat counts, the selected game in a party) therefore
moves into the encrypted channel. The advertisement only carries facts that
are fixed for that room's lifetime.

Decisions adopted in ADR-0003 (see §18):

1. **Identity is layered and typed.** A canonical `GameId` (kebab-case,
   strictly validated) is separate from a new random, non-secret `RoomId`, the
   secret six-character admission `RoomCode`, a public four-character
   `RoomTag`, a `PartyId`, and a `MatchId` (today's per-start `SessionId`).
   None is derived from another; in particular `RoomTag` is never derived from
   the room code.
2. **The advertisement becomes versioned and game-specific, but stays a
   hint.** It uses a new prefix `parlor-lobby|1|…` carrying protocol, game,
   game version, room tag and room mode. It is unauthenticated pre-connection
   data, so the host re-checks every claim inside the encrypted admission
   exchange.
3. **The host rejects cross-game, stale-room and expired-approval context
   before any human approval prompt.** It does this with explicit rejection
   codes (`WrongGame`, `StaleRoom`, `ApprovalExpired`) in protocol `4.3`.
   Today the host cannot see the joiner's game at all (§3.3, G-1).
4. **QR codes and links are a versioned `parlor://join` capability.** They
   carry the code, room ID, game and the host's full P2pKit fingerprint. The
   joiner uses the fingerprint-pinned `connect(peer, fingerprint)` overload
   Parlor already uses for resume. The link never carries an IP address or
   port, so ADR-0002 still holds.
5. **`PartySession` and `Match` are separate.** A persistent party (one host,
   one P2pKit kit, one advertisement, one membership credential per peer) runs
   zero or more matches. Each match is today's `SessionStarting` →
   `SessionStartCommitted` barrier with its own `MatchId`/`SessionId`.
   Switching games is an explicit host action in the party lobby, never an
   implicit side effect. This requires protocol `5.0`.
6. **Authority does not move.** The host remains the only reducer and party
   owner. When the host leaves, the party ends. There is no host migration.

---

## 2. Supported boundaries

| Boundary | Supported by Smart Lobby | Not supported (unchanged) |
|---|---|---|
| Platforms | Android 8.0/API 26+, iOS 16+ phones/tablets; Desktop for development and deterministic tests | watchOS/tvOS/macOS Catalyst; Desktop as a shipping client |
| Topology | Devices on one reachable local IP network where Bonjour/NSD multicast **and** peer TCP both work; hotspots only after their physical rows pass (`docs/P2P_MANUAL_TEST.md`) | Public internet, NAT traversal, relay, rendezvous, Wi-Fi Direct/Nearby provisioning |
| Discovery | P2pKit LAN discovery (`_p2pkit2._tcp`, AuthenticatedV2) | Raw IP scan, manual host:port, BLE beacons |
| Joining | Typed code; tapping a discovered room plus the code; QR/link (code + fingerprint pin, still via LAN discovery) | Joining without the room code; joining without host approval |
| Authority | Single host per party; host approval for every new membership | Host migration, co-hosts, spectators |
| Mixed versions | Fail closed with a truthful "update both devices" message | Cross-version play (protocol compatibility stays exact) |
| iOS permissions | Existing `NSLocalNetworkUsageDescription` + `NSBonjourServices = _p2pkit2._tcp` (`iosApp/iosApp/Info.plist:54-59`); a custom URL scheme for links | Universal Links (they need a public associated domain, i.e. internet) |
| Android permissions | Existing `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE` (`composeApp/src/androidMain/AndroidManifest.xml:3-6`); a custom-scheme intent filter | Verified App Links (they need a public domain); background discovery |
| Camera | Phase 4 default: the system camera app opens the `parlor://` link, so Parlor needs no new permission | In-app scanner, unless decision D-6 approves camera permission, a scanner dependency and Store privacy-label changes |

Physical-device, hotspot, permission, accessibility and Store evidence stay
external release gates (`docs/RELEASE_GATES.md`). No simulator or emulator run
in §16 counts as a physical row.

---

## 3. Evidence at the baseline

### 3.1 Current Parlor code

| Area | Current behavior | Evidence |
|---|---|---|
| Wire protocol | `RoomMessage` sealed CBOR over `P2pMessage.Binary`; strict decoding (`ignoreUnknownKeys = false`); exact version equality | `shared/networking/src/commonMain/kotlin/com/parlor/networking/protocol/Protocol.kt:17-47`, `RoomMessageCodec.kt:11-35` |
| Frame limits | command 32 KiB, snapshot 256 KiB, control 8 KiB, frame 272 KiB | `Protocol.kt:31-34` |
| Admission request | `AdmissionRequest(protocol, actor, roomCode, displayName, rejoinToken?)`, with **no game identity** | `Protocol.kt:255-262` |
| Pending response | `AdmissionPending(playerId)`, with no expiry or lobby facts | `Protocol.kt:144-146` |
| Credential offer | `ResumableCredentialOffer(..., gameId: String? = null, gameVersion: Int? = null)` | `Protocol.kt:81-94` |
| Rejections | `WrongCode, HostDeclined, RoomFull, SessionStarted, IncompatibleProtocol, InvalidRequest, RateLimited, InvalidCredential, ExpiredCredential, AlreadyConnected, DisplayNameInUse` | `Protocol.kt:66-79` |
| Session header | `SessionEnvelopeHeader(protocol, sessionId, gameId, gameVersion, messageId, sequence, connectionEpoch)`; host validation maps `WrongGame`/`WrongSession` to `CommandStatus.IncompatibleVersion` | `Protocol.kt:49-64`; `shared/session/.../AuthoritativeSessionCoordinator.kt:1133-1150` |
| `GameId` type | Only `require(raw.isNotBlank())` | `shared/core/src/commonMain/kotlin/com/parlor/core/ids/Ids.kt:11-15` |
| Wire ID check | `[A-Za-z0-9_-]{1,128}`, which is looser than kebab-case | `ProtocolValidation.kt:225-242` |
| Shipping game IDs / versions | `whodunit` v6, `mafia` v2, `last-light` v1, `egyptian-dominoes` v2, `ghamza` v2, `word-impostor` v2 | `game-modes/*/…/*Ids.kt`; `WhodunitHostRoomBridge.kt:561`, `MafiaHostRoomBridge.kt:486`, `LastLightHostRoomBridge.kt:461`, `DominoIds.kt:9`, `GhamzaIds.kt:9`, `WordImpostorIds.kt:9` |
| Registry | `DefaultGameShellRegistry` rejects duplicate IDs and contract mismatch, not non-canonical IDs | `composeApp/.../shell/game/GameShellRegistry.kt:170-218` |
| Room code | 6 characters from a 32-character alphabet (30 bits); generated by `SecureIds.randomCharacters` | `shared/networking/.../room/RoomInputPolicy.kt:7-21`; `P2pKitRoomTransport.kt:1530-1533` |
| Advertisement | Host kit `deviceName = "parlor-room|" + "parlor-" + 6 base-36 chars`; the tag comes from `kotlin.random.Random` and is created once per DI singleton, so it lasts the whole process | `P2pKitRoomTransport.kt:272-279,1537`; `P2pTransportModule.kt:19-25`; `P2pTransportModule.android.kt:86-90`, `.ios.kt:65` |
| Discovery API | `RoomTransport.discoverRooms()` defaults to `flowOf(emptyList())`; P2pKit adapter reports `supportsDiscovery = false` | `RoomTransport.kt:98-107`; `P2pKitRoomTransport.kt:251-262` |
| Join | Discover every `parlor-room|` peer fresh at discovery start (≤ 5 s cached), dial each candidate in turn (5 s per attempt, 30 s total), and send the code inside the encrypted channel; `WrongCode`/`IncompatibleProtocol` are candidate-local | `P2pKitRoomTransport.kt:349-585,1501-1520`; `DiscoveryCandidateScheduler.kt` |
| Game check | Peer-side only, **after** host approval: the encrypted offer's `gameId`/`gameVersion` are compared with `JoinConfig.expectedGameProtocol`, then the peer fails `IncompatibleProtocol` | `P2pKitRoomTransport.kt:1075-1084` |
| Host admission order | rate limit → protocol → code → reject token → name → pending (bounded by `maxRemotePlayers + 4`) → `AdmissionPending` | `P2pKitRoomTransport.kt:2331-2424`; `P2pTrafficPolicy.kt:45` |
| Approval expiry | Only the **peer** waits 60 s (`HOST_APPROVAL_TIMEOUT_MS`); the host keeps a pending row until the session closes | `P2pKitRoomTransport.kt:1037,1545`; host pending removal on close `:2287-2294` |
| Capacity | `admit()` reserves under `stateMutex`: `members + reservations ≥ maxRemotePlayers` → `RoomFull` | `P2pKitRoomTransport.kt:2985-3045` |
| `HostConfig.maxRemotePlayers` | `1..17`; callers pass `supportedPlayerCounts.last - 1` | `RoomTransport.kt:156-168`; `HostedGameLobby.kt:67`; `MafiaHostLobbyFlow.kt:168`; `LastLightHostLobbyFlow.kt:166`; `WhodunitHostSessionFlow.kt:209` |
| Freeze | `closeAdmissions()` is one-way (`admissionsClosed = true`) and retires disconnected lobby seats; there is no reopen | `P2pKitRoomTransport.kt:2845-2900` |
| Advertising during a match | Continues after freeze (resume needs discovery of the recorded host peer ID); stops on background/retention/leave with a 150 ms Bonjour goodbye flush | `P2pKitRoomTransport.kt:3336-3343,3365,3450,3714-3723,1556-1560` |
| Process ownership | One `ProcessMultiplayerSessionOwner` room per process; `MultiplayerSessionRoute` is bound to one `gameId`; `frozenRoster` is set once | `shared/session/.../ProcessMultiplayerSessionOwner.kt:44-154,227-297,416-485` |
| Match identity | Each start creates `SessionId(SecureIds.id128())`; `sessionNonce` is derived from the room code's `hashCode()` and is public by design | `composeApp/.../multiplayer/GameRuntime.kt:45`; `HostedGameBridge.kt:113`; `Protocol.kt:209-235` |
| Rematch today | In-match, reducer-level (`GhamzaAction.Rematch(token)`, Whodunit "Play Again — New Killer" with a host-only seed, Last Light rematch, Dominoes next hand) | `game-modes/ghamza/.../GhamzaReducer.kt:38-64`; `WhodunitReducer.kt:1012`; `GameplayResultsTest.kt:99-101` |
| Resume credential | Single slot `p2p-resumable-session-v1`, schema 1, fingerprint `p2f1-…` (57 chars), `gameId` nullable, 24 h expiry, host seat grace 120 s | `ResumableCredentialStore.kt:24-60,426,459-462`; `P2pKitRoomTransport.kt:1546-1554` |
| Pinned connect | Resume already calls `kit.connect(hostPeer, expectedFingerprint)` | `P2pKitRoomTransport.kt:1227` |
| Background policy | `BackgroundPolicy.CloseActiveSessions`, `ReconnectPolicy.Enabled(10, 3 s)` on every platform | `P2pTransportModule.android.kt:66`, `.ios.kt:40`, `.desktop.kt:48` |
| Shell dispatch guard | `verifyGameShellDispatch` forbids game names in `App.kt`, `AppBackPolicy.kt`, `LocalResumeRouter.kt`, `HomeScreen.kt`, `shell/multiplayer/**` | `composeApp/build.gradle.kts:735`; `AGENTS.md` |
| Existing `party` package | `com.parlor.session.party` holds **readiness gates** (`PartyAwareSession`, `PartyReadinessGate`) for local acks, not a party concept | `shared/session/.../party/*.kt` |
| Profile / stats | None. `SettingsStore` has only reduced motion, language and theme | `shared/storage/.../settings/Settings.kt:19-33` |
| Deep links / camera | None: no `CFBundleURLTypes`, no Android `VIEW` intent filter, no camera permission | `Info.plist`; `AndroidManifest.xml` |

### 3.2 Published, pinned P2pKit 0.7.0-rc3 evidence

These are the cached Maven Central artifacts Parlor resolves. Their SHA-256
digests match `gradle/verification-metadata.xml` exactly:

| Artifact | SHA-256 |
|---|---|
| `p2p-core-jvm-0.7.0-rc3.jar` | `4ffb18b77cf55900ab8210c0e382bef9a25421de9196caa326d79c2da1cb5593` |
| `p2p-transport-lan-jvm-0.7.0-rc3.jar` | `571d8464b069f42e073244194fc338d3199ea5e9e63b0ba600a19e17058fcb1d` |
| `p2p-transport-lan.aar` (Android 0.7.0-rc3) | `4b1363f54c35db6749909e92b1bf9bdd6126218f79f1edefb8809ef85d9020d4` |
| `p2p-transport-lan-iosArm64Main-0.7.0-rc3.klib` | `66dd89cf562c7729307b77fda2d9e584d6100b78d5a5868b2a726794000e8b4a` |

Findings from `javap` on the JVM and Android class files and from the iOS KLIB
IR string table (reproduction commands in Appendix A):

| ID | Finding | Consequence for Smart Lobby |
|---|---|---|
| PK-1 | `dev.p2pkit.core.Peer` exposes only `id`, `name`, `platform`, `supportedTransports`. | Discovery has no app metadata channel other than `name`. |
| PK-2 | `P2pKit.startAdvertising()` takes no arguments; `deviceName` is set once on `P2pKitBuilder` and `localDeviceName` is read-only. | **The advertisement cannot change while the kit runs.** Changing it means a new kit, which drops every session. |
| PK-3 | LAN TXT keys are a closed set: `pid, app, name, plat, caps, pv, fp` (`LanConstants.DISCOVERY_TXT_KEYS`). Service types are `_p2pkit2._tcp` (secure) / `_p2pkit._tcp` (legacy). | Parlor cannot add TXT keys; the game identity must live inside `name`. |
| PK-4 | `buildLanTxtProperties` validates `name` with `isWellFormedLanText` and **silently truncates** it with `truncateLanTxtValue` so that `"name=" + value ≤ 255` UTF-8 bytes (`lanTxtEntryFits`). The iOS `IosBonjour.mapToTxtRecord` additionally rejects oversized entries. | Parlor must cap its encoded name well below 250 bytes and fail host creation itself rather than rely on truncation. |
| PK-5 | `P2pKitBuilder` validates `deviceName` with `validateWireText(…, 512, 2048, …)` and forbids control and bidi formatting characters. | The ASCII-only advertisement grammar in §5 is valid input. |
| PK-6 | iOS advertises with `nw_advertise_descriptor_create_bonjour_service` plus `set_no_auto_rename`; the Bonjour service instance name is the peer ID, and browse results whose instance name doesn't match the TXT `pid` are dropped. | Instance-name collisions don't affect Parlor; peer ID is the stable key. |
| PK-7 | TXT includes `pid` (stable per-install peer ID) and `fp` (authenticated fingerprint) and is visible to every LAN observer. | It is already a device-linkable identifier at the baseline; Smart Lobby must not add more (§15). |
| PK-8 | `P2pKit.connect(peer, expectedFingerprint)` is part of the stable interface (no `ExperimentalP2pApi` marker on the interface method) and is already used by resume. `PeerPairingQr` exists in canonical `p2pkit:v2:<p2a1 app binding>:<p2f1 fingerprint>` form. | QR/link can pin the host fingerprint without the manual-IP provisioning sidecars ADR-0002 excludes. |
| PK-9 | `P2pKit.lastSeen(peerId)` exists; Parlor's own comment records that rc3 `lastSeen` is not a heartbeat and native `Lost` owns lifetime (`P2pKitRoomTransport.kt:119-127`). | Cache expiry cannot be "age since last seen" during a discovery session (§5.4). |
| PK-10 | `networkPathStatus: StateFlow<NetworkPathStatus>` (`Satisfied/Unsatisfied/Unknown`) is public. | Path loss can clear the room list. |

The local Gradle cache also contains unpinned `0.8.0-rc8…rc13` artifacts from
other work. A spot check of `0.8.0-rc13` `Peer`/`P2pKit` shows the same
metadata surface (no advertisement-metadata API). They are **not** Parlor
inputs and are cited only to say that a P2pKit upgrade does not currently
remove constraint PK-2. Upgrading stays a separately reviewed change
(`docs/P2PKIT_MAVEN_PROVENANCE.md`).

### 3.3 Gaps Smart Lobby must close

| Gap | Description | Effect today |
|---|---|---|
| G-1 | Host cannot see which game a joiner expects. | A Mafia joiner with a Whodunit room's code gets a host approval prompt. After approval the peer fails `IncompatibleProtocol`, leaving a reserved seat until the confirm timeout (60 s) or session close. |
| G-2 | Advertisement carries no game, version or tag. | Joiners dial every Parlor host on the LAN until the code matches (O(n) authenticated handshakes); there is no room list. |
| G-3 | No host-side approval expiry. | The host can approve a request the joiner abandoned; the stale row stays visible until TCP close is observed. |
| G-4 | `GameId` is not canonicalized. | Protocol and persistence keys rely on convention; the wire validator accepts `_` and upper case. |
| G-5 | One room = one game = one frozen roster. | No party, no game switching, no "same group, next game" without everyone rejoining. |
| G-6 | Discovery tag uses a non-CSPRNG process-lifetime value. | Linkable across rooms within one process, and weaker than needed for a user-visible room tag. |
| G-7 | No QR/link path. | Typing is the only entry. |

---

## 4. Identity model

| Identifier | Type (proposed) | Generated by | Lifetime | Secrecy | Where it may appear |
|---|---|---|---|---|---|
| `GameId` | `GameId` + `CanonicalGameId` validation | Game module constant | Forever (renaming is a migration) | Public | Advert, link, headers, credentials, logs (it is not personal data) |
| `gameVersion` | `Int > 0` | Game binding | Per app release | Public | Advert, link, headers, credentials |
| `RoomId` | `RoomId(raw: 32 lowercase hex)` from `SecureIds.id128()` | Host at `host()` | One kit/advertisement lifetime | Non-secret but **not advertised** | Encrypted admission, QR/link, credentials. Never in logs |
| `RoomCode` | `String` (existing policy) | Host, CSPRNG | Room lifetime | **Admission secret** (low entropy) | Host screen, QR/link, encrypted `AdmissionRequest`. Never in adverts, logs, a11y labels beyond the host's own screen |
| `RoomTag` | `RoomTag(raw: 4 chars of ROOM_CODE_ALPHABET)` from `SecureIds.randomCharacters` | Host, independent of code | Room lifetime | Public | Advert, host lobby screen, room list. Never derived from code; never logged |
| `PartyId` | `PartyId(raw: 32 hex)` | Host at party creation | Party lifetime (= room lifetime in this plan) | Non-secret, not advertised | Encrypted party frames, credentials |
| `MatchId` | Existing `SessionId` (id128) | Host per start | One match | Non-secret | Headers, `SessionStarting`, outcomes |
| `PlayerId` | Existing; equals authenticated P2pKit peer ID | P2pKit identity | Per install | Device-linkable | Wire only; never logs or UI labels |
| `MembershipId` | Existing credential field | Peer at first offer | Membership lifetime across rotations | Local only | Secure storage only |

### 4.1 Canonical `GameId`

Grammar: `^[a-z][a-z0-9]*(-[a-z0-9]+)*$`, 1–32 ASCII bytes. All six shipping
IDs conform. The literal `party` and anything starting with `parlor` are
reserved (they could be confused with advertisement tokens).

- Add `GameId.isCanonical(raw)` / `GameId.requireCanonical()` in `:shared:core`
  (`Ids.kt`). The constructor stays lenient in Phase 1 so that test fixtures
  (for example `:shared:engine-testing`) can be audited first. Phase 1
  acceptance requires either making the constructor strict or recording why
  not.
- `DefaultGameShellRegistry.init` adds `require(GameId.isCanonical(id.raw))`
  for every binding and every `GameShellMultiplayerContract`.
- `ProtocolValidation.validateFor` checks the header game ID with the
  canonical grammar instead of `isValidWireEntityId` (sessionId keeps the
  opaque grammar). This is wire-compatible with 4.2 peers, because every value
  a 4.2 build sends is already canonical.
- Advertisement, link and credential parsers use the same function. No parser
  lowercases or trims a game ID; a non-canonical value is rejected.

### 4.2 `RoomId` versus room code versus `RoomTag`

The room code proves the joiner can see the host's screen (or received the
QR/link). `RoomId` identifies one physical room instance, so a QR, a cached
room-list entry or a resume credential from an earlier room is rejected
definitively (`StaleRoom`) instead of looking like a wrong code. `RoomTag`
lets a human match a room-list entry to a host screen without exposing the
code. Two rooms with the same tag are legal; the list shows both and the
encrypted exchange settles which is which.

---

## 5. Discovery

### 5.1 Advertisement schema v1

The host kit's `deviceName` (the only advertisement field Parlor controls,
PK-1/PK-3) becomes:

```text
parlor-lobby|1|<pmaj>.<pmin>|<mode>|<gameId>|<gameVersion>|<roomTag>
```

| Field | Grammar | Example | Notes |
|---|---|---|---|
| prefix | literal `parlor-lobby` | `parlor-lobby` | New prefix. 4.2 builds filter on `parlor-room|` and will ignore v1 adverts, so a new host never receives 4.2 dial noise |
| schema | `1` | `1` | Unknown schema → entry shown as "Newer Parlor room" (disabled) |
| protocol | `<int>.<int>` | `4.3` | Must equal the joiner's `ProtocolVersion` to be joinable |
| mode | `g` (single-game room) or `p` (party lobby, protocol 5.0+) | `g` | |
| gameId | canonical `GameId`, or `-` in mode `p` | `mafia` | In `p` mode the selected game can change, and PK-2 means the advert could not follow it |
| gameVersion | `1..9999`, or `-` in mode `p` | `2` | |
| roomTag | 4 × `ROOM_CODE_ALPHABET` | `K7QX` | CSPRNG, independent of code |

Rules:

- `LobbyAdvertisement.encode()` produces ASCII only and must be ≤ 64 bytes.
  Host creation fails with `NetError.InvalidInput` if not (PK-4). The worst
  case is `parlor-lobby|1|99.99|g|<32-byte id>|9999|XXXX` = 65 bytes. A syntactically valid ID can exceed the
  64-byte advert budget with long version fields; check the full encoded
  length and fail before starting the host.
- `LobbyAdvertisement.parse()` is total and strict: exactly 7 fields, no
  empty fields, no whitespace, no extra separators, no lowercase in the tag.
  It returns a sealed result: `Joinable`, `IncompatibleProtocol`,
  `UnknownGame`, `IncompatibleGameVersion`, `NewerSchema`, `Legacy42` (a name
  starting with `parlor-room|`) or `NotParlor`.
- The host display name, member counts, open/started status, room code and
  `RoomId` are **never** advertised (§15). Status can't be advertised anyway
  (PK-2).
- The process-wide `parlor-<randomDeviceTag()>` suffix is removed from host
  adverts. The peer kit's device name (today the display name,
  `P2pKitRoomTransport.kt:367`, resume `:803`) is not advertised, because peers only browse.
  Phase 3 still replaces it with a fixed `parlor-peer` to keep the display
  name out of the P2pKit Hello. That needs a P2pKit source review of what
  Hello carries before the change (task T3.9).

### 5.2 Filtering

Every filter runs on `kit.peers`, which already reflects native Lost:

1. Prefix and schema parse (`parse()` above).
2. Exact protocol match.
3. In mode `g`, the game must be in this build's registry with a
   multiplayer contract. A version mismatch shows the entry as "Needs the same
   Parlor version". An unknown game is hidden.
4. A game card opens a browser fixed to that game's canonical ID and
   exact supported version. Other games and parties are excluded from its
   visible rows and dial candidates. A Home discovery overview may list
   compatible games by section, but selecting a group opens a separate
   game-scoped browser before dialing. Protocol 5.0 can expose an explicit
   Party browser for mode `p`; its rows are separate from every game browser.
5. `Legacy42` entries are counted and shown as a single banner ("A nearby
   room uses an older Parlor. Update both devices"), never as joinable rows.

The advertisement is a **hint**. After connecting, the encrypted
`AdmissionPending.lobby` (§6) is authoritative. If it contradicts the advert
(different game or version), the entry is quarantined as `Mismatch` for the
rest of the discovery session and the user gets a neutral error. The joiner
never routes to a binding based on advert text alone.

### 5.3 Room list model

`DiscoveredRoom` (`shared/networking/.../room/DiscoveredRoom.kt`) gains
defaulted fields, so existing constructors stay source-compatible:

```kotlin
data class DiscoveredRoom(
    val code: String,                 // retained; always "" for LAN adverts (never advertised)
    val displayName: String,          // retained; RoomTag-based label, never a person's name by default
    val hostPlayerId: PlayerId? = null,
    val entryKey: String = "",        // opaque: transport-owned (peerId|endpointVersion), never shown
    val roomTag: String? = null,
    val mode: LobbyMode? = null,      // Game | Party
    val game: GameProtocolRef? = null,
    val compatibility: RoomCompatibility = RoomCompatibility.Joinable,
    val availability: RoomAvailability = RoomAvailability.Unknown, // Unknown | Full | Started | Unavailable | Mismatch
)
```

`P2pKitRoomTransport.discoverRooms()` returns a cold flow that owns its own
kit for its collection lifetime (the same factory as `join`). It emits a
bounded, deterministically ordered list (first-seen order, max 32 rows), and
stops discovery and the kit on cancellation. `TransportCapability.supportsDiscovery`
becomes `true` only in the commit that ships the UI, which keeps the existing
contract comment truthful (`P2pKitRoomTransport.kt:251-254`).

Joining from the list goes through `join(JoinConfig(..., target = JoinTarget.Discovered(entryKey)))`
(§12.4). That dials **only** that candidate, so the O(n) dial loop is
avoided. Typed-code entry without a selection keeps the existing
all-candidates scheduler, filtered by game.

### 5.4 Cache expiry

| Event | Effect on list |
|---|---|
| Peer absent from `kit.peers` (native Lost) | Remove the row immediately |
| Discovery start | Ignore observations whose `lastSeen` is older than 5 s at start (existing `DEFAULT_PEER_FRESHNESS_WINDOW_MS`) |
| `networkPathStatus` turns `Unsatisfied`, or the interface fingerprint changes | Clear all rows; show "Searching…" |
| Connect/handshake transport failure on a row | Mark `Unavailable` for 10 s (hidden), then show it again if still advertised (covers iOS Bonjour eviction after a host goodbye, 5–30 s) |
| Encrypted response `RoomFull` / `SessionStarted` | Mark `Full`/`Started` for the rest of the session. Advisory: the row stays tappable after the user refreshes |
| Encrypted response contradicts the advert, or `StaleRoom` | Mark `Mismatch` (hidden) for the rest of the session |
| User pull-to-refresh | Stop + start discovery (a fresh browser; this flushes the Bonjour cache on iOS), clear quarantine |
| List not visible / app backgrounded | Cancel the flow (stops discovery and the kit) |
| Visible for 120 s without user action | Pause discovery; show "Search again" (battery bound, §14) |

No row is ever expired by `lastSeen` age while discovery runs (PK-9). Native
Lost plus the refresh/path rules are the only time-based removals. This keeps
a still-advertised host joinable after a slow dial, preserving the reasoning
at `P2pKitRoomTransport.kt:123-127`.

### 5.5 Optional encrypted lobby probe (decision D-4)

Open/started status and seat counts cannot be advertised (PK-2). An optional
`LobbyProbe` (connect, send `PeerMessage.LobbyProbe`, receive
`HostMessage.LobbyProbeResult(phase, seatsTaken, seatLimit)`, then close)
would fill `availability` before the user taps. Cost: one authenticated
handshake per row per refresh. The host must budget probes separately from
admissions (max 2 concurrent, a 1 s response bound, a per-peer rate limit,
no pending row) so probes cannot starve real joins. The probe must not return
the host display name, because any same-AppId device can send it.
**Recommendation: do not ship in the first Smart Lobby release.** Revisit
with physical evidence of dial cost (§14).

---

## 6. Admission (protocol 4.3)

The initial wire slice sends and checks the selected game and version before
approval. It reports a reason-only `WrongGame` and the peer keeps its selected
game. The admission preview, credential handoff, and resume verify the same
room/game context. Release qualification remains subject to ADR-0003.

### 6.1 Wire changes

```kotlin
const val PARLOR_PROTOCOL_MINOR: Int = 3   // 4.3; exact compatibility unchanged

@Serializable data class GameProtocolRef(val gameId: GameId, val gameVersion: Int)

// PeerMessage
data class AdmissionRequest(
    val protocol: ProtocolVersion,
    val actor: PlayerId,
    val roomCode: String,
    val displayName: String,
    val rejoinToken: String? = null,          // still always rejected; retained for schema stability
    val expectedGame: GameProtocolRef,        // exactly the chosen game and version; required in 4.3
    val expectedRoomId: String? = null,       // from QR/link/credential; null for typed or discovered entry
) : PeerMessage

// HostMessage
data class AdmissionPending(
    val playerId: PlayerId,
    val lobby: LobbyInfo,
    val approvalWindowMillis: Long,           // host-chosen, 10_000..120_000; relative, no clock sync
) : HostMessage

@Serializable data class LobbyInfo(
    val roomId: String,
    val game: GameProtocolRef,                // the game this admission would join
    val hostDisplayName: String,
    val seatsTaken: Int,                      // host + members + reservations
    val seatLimit: Int,
)

data class AdmissionRejected(
    val reason: AdmissionRejection,
    val hostGame: GameProtocolRef? = null,    // present only for WrongGame (sent only after a correct code)
) : HostMessage

enum class AdmissionRejection { /* 4.2 values …, */ WrongGame, StaleRoom, ApprovalExpired }

data class ResumableCredentialOffer(
    /* existing fields … */
    val gameId: String,                       // now required (was nullable)
    val gameVersion: Int,                     // now required
    val roomId: String,                       // new, required
)

data class ResumeRequested(/* existing fields … */ val roomId: String, val game: GameProtocolRef)
```

All size bounds stay under `MAX_CONTROL_PAYLOAD_BYTES` (8 KiB): the bound game
ref is under 64 bytes, and `LobbyInfo` is under 300 bytes with a
maximum-length display name.

A game-scoped entry point binds `expectedGame` to the resolved game shell,
and uses that one value for candidate selection, request construction,
admission, credential verification, and snapshot routing. A device can support
all six games and still request entry to exactly one. Changes to discovery or
join selection cancel the previous operation and discard its late events.
A 5.0 encrypted party capability declaration is a separate bounded
`supportedGames` list. It can inform future transitions after membership has
been authorized, but it satisfies no game-room admission predicate. General
party invitations use an explicit party entry route and never appear in a
game-scoped discovery list while the party can change games.

### 6.2 Host-side check order

The order is chosen so that nothing secret leaks before the code is proved,
and so that definitive errors stop the joiner while candidate-local errors let
it try the next host:

| # | Check | Result | Joiner treatment |
|---|---|---|---|
| 1 | Per-peer attempt rate limit | `RateLimited` | Definitive |
| 2 | Exact protocol | `IncompatibleProtocol` | Candidate-local |
| 3 | `expectedGame` is canonical and registered, with a positive game version | `InvalidRequest` | Definitive |
| 4 | `expectedRoomId != null && != roomId` | `StaleRoom` | Definitive for pinned/selected targets; candidate-local in all-candidates mode |
| 5 | Room code | `WrongCode` | Candidate-local (unchanged) |
| 6 | Host game differs from `expectedGame` (`gameId` or exact `gameVersion`) | `WrongGame(hostGame)` | Definitive; the original attempt ends; the shell never retries or silently switches game |
| 7 | `admissionsClosed` | `SessionStarted` | Definitive |
| 8 | Rejoin token present | `InvalidCredential` | Definitive (unchanged) |
| 9 | Display name rules / duplicate | `InvalidRequest` / `DisplayNameInUse` | Definitive (unchanged) |
| 10 | Pending bound | `RateLimited` | Definitive (unchanged) |
| 11 | Record pending with `expiresAt = now(monotonic) + window` | `AdmissionPending(lobby, window)` | Show "Waiting for <host> · mm:ss" |

`WrongGame` is reported only after step 5 proves the code, so `hostGame` is
disclosed only to someone who can read the host screen. In mode `g` the game is
advertised anyway.

The peer-side check at `P2pKitRoomTransport.kt:1075-1084` stays as defense in
depth. A 4.3 host that passed step 6 can never trip it, so tripping it is a
host bug or tampering: fail with `IncompatibleProtocol` and keep the 4.2
behavior.

### 6.3 Expiring approvals

- The host stores `expiresAt` (monotonic `TimeSource`) per pending row. A
  bounded host-side reaper (one coroutine per room, not per row) removes
  expired rows, sends `AdmissionRejected(ApprovalExpired)`, closes the
  session and republishes `pendingAdmissions`.
- `approveAdmission(playerId)` re-checks expiry inside the same `stateMutex`
  section as the capacity check in `admit()`. An expired row fails with
  `NetError.ApprovalExpired` and is removed; it never half-admits.
- The peer waits `min(approvalWindowMillis, HOST_APPROVAL_TIMEOUT_MS)` after
  `AdmissionPending`, so a modified host cannot make a peer wait forever.
- `PendingAdmission` gains `expiresInMillis` for the host UI countdown. The
  accessibility label reads remaining whole seconds only. It never reads a
  player ID.
- Default window: 60 s (unchanged user-visible budget,
  `docs/P2P_MANUAL_TEST.md` time-budget table).

### 6.4 Atomic capacity

- `seatLimit = selected game's supportedPlayerCounts.last` (party: §8.4).
  `seatsTaken = 1 (host) + members + reservations not yet members`.
- Pending requests **do not** hold seats. Approval is the compare-and-reserve
  step (existing `admit()` transaction). The pending bound stays
  `remaining seats + SESSION_ADMISSION_HEADROOM (4)` so a crowd cannot evict
  real candidates, and the host UI disables "Approve" when `seatsTaken ≥ seatLimit`.
- Approving one request never implicitly rejects others. When the room fills,
  remaining pending rows stay (the host may remove a member) until they expire
  or the host declines them.
- `closeAdmissions()` keeps its "fail with `CommandInFlight` while a
  reservation/ready barrier is open" rule (`P2pKitRoomTransport.kt:2855-2866`).

### 6.5 Membership and gameplay rejection of stale or cross-game context

| Layer | Stale/cross-game input | Rejection |
|---|---|---|
| Admission | Wrong game, version, room, expired approval | §6.2 codes |
| Credential offer (peer) | Offer `gameId`/`gameVersion`/`roomId` ≠ `LobbyInfo` the peer accepted | Peer fails closed before staging the secret (extends the existing check) |
| Resume (host) | `ResumeRequested.roomId`/`game` ≠ the host credential record | `InvalidCredential` (definitive; peer discards only the matching membership) |
| Session start (peer) | `SessionStarting.header.gameId/gameVersion` ≠ the accepted lobby game | Existing `acceptsStart` → no Ready (`MultiplayerGameSpec.kt:49-57`) |
| Gameplay (host) | Header `gameId`/`gameVersion`/`sessionId` mismatch | Existing `CommandStatus.IncompatibleVersion` in 4.3; `StaleMatch` in 5.0 (§9.3) |
| Snapshots (peer) | Header mismatch | Existing `ProtocolValidation.WrongGame`/`WrongSession` → ignored, no install |

---

## 7. QR and share/link

### 7.1 Link schema v1

```text
parlor://join?v=1&g=<gameId>&gv=<gameVersion>&p=<pmaj>.<pmin>&c=<roomCode>&r=<roomId>&t=<roomTag>&f=<p2f1-fingerprint>
```

| Param | Required | Grammar | Purpose |
|---|---|---|---|
| `v` | yes | `1` | Schema version; unknown → "Update Parlor to open this room" |
| `g`, `gv` | yes | canonical `GameId`, `1..9999` | Route to the binding before any network work; fail closed if not registered or version differs |
| `p` | yes | `<int>.<int>` | Exact protocol; mismatch → update message without dialing |
| `c` | yes | `RoomInputPolicy.isValidRoomCode` | Admission secret |
| `r` | yes | 32 lowercase hex | `expectedRoomId` (§6.2 step 4) |
| `t` | yes | 4 × alphabet | Shown while connecting ("Joining room K7QX") |
| `f` | yes | `p2f1-` + 52 Base32 (57 chars, the existing credential grammar) | Pinned connect via `kit.connect(peer, fingerprint)` (PK-8) |

Validation (`JoinLinkCodec`, pure Kotlin in `:shared:networking`):

- Total length ≤ 256 characters; scheme exactly `parlor`, host exactly `join`;
  no path, fragment, user-info or port.
- Every parameter appears exactly once; unknown parameters are **rejected**
  (strict, like CBOR `ignoreUnknownKeys = false`). A future optional field
  needs a new schema. Schema 2 is a separate Party link and replaces `g` and
  `gv` with the exact `party` ID, using protocol 5.0. The selected game is
  authorized by the host after connection and remains changeable.
- No percent-decoding: every allowed character is unreserved ASCII, so any `%`
  is a rejection. Parameter order is not significant.
- The decoded link is a value object with a redacting `toString()` (like
  `MultiplayerSessionRoute.toString`). It is never logged and never placed in
  navigation state that is serialized or restored across process death
  (§11).

### 7.2 Pinned join flow

1. The link opens the app. `JoinLinkRouter` resolves `g` through
   `GameShellRegistry` (no game names in `shell/multiplayer/**`). If the
   game is unknown or the version differs, it shows "This room needs a
   different Parlor version" and stops. Nothing touches the LAN.
2. Name entry (prefilled from the last used name if decision D-9 allows).
3. `join(JoinConfig(code, name, expectedGame = g@gv, target = JoinTarget.Pinned(fingerprint, roomId)))`.
4. The transport discovers `parlor-lobby|` adverts with the exact protocol,
   game, game version and room tag. P2pKit rc3's public `Peer` does not expose
   an advertisement fingerprint, so tag collisions can produce more than
   one candidate. `connect(peer, fingerprint)` enforces the pin for each
   candidate; the transport must send no Parlor admission frame until it
   confirms the session identity. The join still has a 30 s bound. **No IP address or port is ever read
   from the link** (ADR-0002 invariant).
5. `StaleRoom` or a pin failure is definitive: "This QR code is for a room that
   has closed."

### 7.3 Rendering, scanning and sharing

- **Host screen:** the host lobby shows the room code, `RoomTag` and a QR of
  the link. The QR follows the same concealment rules as the code: hidden
  when the app is inactive or in the switcher snapshot (existing private-UI
  concealment), and excluded from accessibility tree text. Its label is
  "Room QR code", not its content.
- **QR encoding:** this needs a QR encoder. Options (decision D-7): a reviewed
  KMP dependency (DEPENDENCY_REVIEW + verification metadata + notices), or a
  small vendored, tested encoder for byte-mode version ≤ 6 / ECC-M (the link
  is under 180 bytes). Rendering stays local; there are no network calls.
- **Scanning (default):** the system camera recognizes `parlor://` and opens
  the app. Android: an `intent-filter` with `VIEW`/`BROWSABLE`/`DEFAULT` and
  `scheme="parlor" host="join"` on the launcher activity, handling
  `onCreate`/`onNewIntent`. iOS: `CFBundleURLTypes` with scheme `parlor`,
  forwarded from `iOSApp.swift` (`onOpenURL`) into the Kotlin root
  controller. No camera permission.
- **Scanning (optional, D-6):** an in-app scanner needs Android `CAMERA` and
  iOS `NSCameraUsageDescription`, a scanner dependency (ML Kit pulls Google
  Play services; CameraX + ZXing; AVFoundation metadata on iOS),
  `PrivacyInfo.xcprivacy` review and Store privacy labels. Not recommended for
  the first release.
- **Share sheet (D-8):** "Share invite" sends the link through the OS share
  sheet. That moves the room code and host fingerprint **off the LAN to a
  third-party service**. The code alone doesn't admit anyone (LAN presence and
  host approval are still required), but the host must confirm a one-time
  explanation. Default: offer QR only and make sharing an explicit secondary
  action.
- **Custom-scheme hijacking:** any Android app can register `parlor://`;
  verified App Links need a public domain (out of scope). Impact: another app
  could receive the code and fingerprint. Mitigation: same as sharing (no
  admission without LAN presence and approval). Document it in
  `PRIVACY_AND_COMPLIANCE.md`.
- **Link freshness:** the link has no wall-clock expiry field (devices'
  clocks are not trusted). It dies with the room, via `RoomId` (`StaleRoom`)
  and the host fingerprint.

---

## 8. Authority, host departure and capacity in a party

### 8.1 Authority

- The host is the only owner of party state, admission, match start, game
  selection and reducers. Peer party commands (`SetReady`, `Leave`) go through
  the same authenticated-actor stamping, protocol/session checks, sequence
  dedup and expected-revision checks as game commands. There are no
  optimistic peer mutations.
- Host-only actions (select game, change settings, start, end match, remove
  member, close party) are local to the host. They are never accepted from
  the wire.

### 8.2 Host departure

| Host event | Party result | Peer result |
|---|---|---|
| Explicit Leave/Close | Broadcast `PartyEnded(HostLeft)` (best effort) after `SessionEnded(HostLeft)` for an active match; stop advertising and flush the goodbye (existing 150 ms) | Commit the end through `commitValidatedSessionEnd`; delete the credential; return Home |
| Backgrounded, party lobby (no match) | Existing rule: unstarted rooms suspend immediately; admission and advertising stop; 120 s lifecycle expiry | Peers see `SessionSuspended`/`HostLost`; resume when the host returns within 120 s |
| Backgrounded, match running | Existing healthy-match retention (15 s best effort), then suspend; 120 s expiry | Unchanged (`docs/P2P_MANUAL_TEST.md` lifecycle section) |
| Process death / OS kill | Party destroyed; no persistence or migration | Resume attempts reach a new host process with no credential record → `InvalidCredential` → discard the matching membership → "Host left" |
| Host network change | Existing kit path handling; peers resume via the pinned fingerprint | Unchanged |

### 8.3 Peer departure

- **Lobby Leave:** final; the membership is removed and the name is released.
  Rejoining is a fresh admission with approval.
- **Lobby disconnect:** the seat is reserved for `REJOIN_GRACE_MS` (120 s), as
  today. If the host starts a match during the grace period, that member is
  excluded from the match roster (existing freeze semantics) but **keeps the
  party membership** (change from today, where freeze retires disconnected
  lobby seats, `P2pKitRoomTransport.kt:2871-2880`). They come back to the
  party lobby view when they resume.
- **Match disconnect:** unchanged game-level disconnect/reconnect actions;
  party membership persists.

### 8.4 Capacity across games

- The party seat limit is the **selected game's** `supportedPlayerCounts.last`,
  capped by the transport maximum (`maxRemotePlayers ≤ 17`, i.e. 18 seats; the
  start validator caps a match at 16 players, `ProtocolValidation.kt:257-258`).
- Selecting a game whose maximum is below the current member count is
  **rejected** with a host-visible reason ("Egyptian Dominoes seats 4; 6 are
  here"). The host may remove members explicitly (`retireMember`, final, with
  credential revocation) and then retry. There is no automatic benching or
  spectating.
- Too few members for the selected game is allowed in the lobby; Start stays
  disabled with the existing start-requirement explanation (PR #349,
  `dc91ee19`).
- In 5.0 the host tracks each party member's separate `supportedGames` capability list. Selecting a
  game that a member does not support (same protocol, different game version)
  is rejected and names the incompatible member to the host only.

---

## 9. PartySession, GameRoom and Match (protocol 5.0)

### 9.1 Definitions

- **GameRoom (mode `g`):** today's model, with Smart Lobby admission and
  discovery: one game, one start, then the room ends with the match (in-match
  reducer rematches still work). Shipped in Phases 1–4 with protocol 4.3.
- **PartySession (mode `p`):** one host kit, one advertisement, one
  `PartyId`, one membership credential per peer. It holds a party roster and
  a `PartyLobby` state, and runs **matches** one at a time.
- **Match:** one `SessionId`, one frozen match roster ⊆ party roster, one
  `SessionStarting`/`SessionStartCommitted` barrier, one
  `AuthoritativeSessionCoordinator`, ended by `SessionEnded`.

Because of PK-2, a party advertises `mode=p` without a game. The joiner learns
the selected game from `AdmissionPending.lobby.game` and, after admission,
from `PartySnapshot`.

### 9.2 Party wire messages (5.0)

```kotlin
const val PARLOR_PROTOCOL_MAJOR: Int = 5; const val PARLOR_PROTOCOL_MINOR: Int = 0

@Serializable data class PartyHeader(
    val protocol: ProtocolVersion,
    val partyId: String,          // 32 hex
    val messageId: String,        // opaque 16..128
    val sequence: Long,           // host sequence; peer = 0
)

// HostMessage
data class PartySnapshot(          // public only; same to every member
    val header: PartyHeader,
    val revision: Long,
    val nextExpectedClientSequence: Long,
    val lobby: PartyLobbyView,
) : HostMessage

@Serializable data class PartyLobbyView(
    val phase: PartyPhase,                     // Lobby | MatchStarting | InMatch | Closing
    val selectedGame: GameProtocolRef,
    val settingsDigest: String?,               // 64 hex of host-chosen public settings (case/mode); content stays host-side
    val members: List<PartyMemberView>,        // ≤ 17
    val seatLimit: Int,
    val activeMatchId: SessionId?,
    val matchOrdinal: Long,                    // increments per committed start
)
@Serializable data class PartyMemberView(val playerId: PlayerId, val displayName: String,
    val connected: Boolean, val ready: Boolean, val inActiveMatch: Boolean)

data class PartyCommandResult(val header: PartyHeader, val commandId: String,
    val status: CommandStatus, val authoritativeRevision: Long, val nextExpectedClientSequence: Long) : HostMessage
data class MatchEnded(val header: PartyHeader, val matchId: SessionId, val reason: SessionEndReason) : HostMessage
data class PartyEnded(val header: PartyHeader, val reason: PartyEndReason) : HostMessage   // HostLeft | Expired | Closed

// PeerMessage
data class PartyCommand(val header: PartyHeader, val actor: PlayerId, val commandId: String,
    val clientSequence: Long, val expectedRevision: Long, val command: PartyCommandPayload) : PeerMessage
@Serializable sealed interface PartyCommandPayload {
    @Serializable data class SetReady(val ready: Boolean) : PartyCommandPayload
}
// Leave uses the existing LeaveNotice + finalLeave transaction.

// Changed in 5.0
data class SessionEnvelopeHeader(/* 4.x fields … */ val partyId: String?)  // non-null in party mode
data class SessionStarting(/* 4.x fields … */ val partyId: String?, val matchOrdinal: Long?)
enum class CommandStatus { /* 4.x … */ StaleMatch }
enum class AdmissionRejection { /* 4.3 … */ PartyClosed }
```

`PartySnapshot` is public data. Per-player private game data still travels
only in match `PlayerSnapshot` frames. A party snapshot never contains roles,
seeds, case solutions, credentials or another player's private slice.
Maximum size: 17 × (≤ 64 B name + 48 B ID + flags) ≈ 2 KiB, under the 8 KiB
control limit.

### 9.3 Stale-frame routing

The host's party router dispatches by message type: party frames go to the
`PartySessionCoordinator`, and match frames go to the coordinator whose
`SessionId` equals `activeMatchId`. A match frame with any other `SessionId`
gets `CommandResult(StaleMatch)` (commands) or is dropped (heartbeats,
snapshot requests), and is never forwarded. The peer drops match frames whose
`SessionId` is not its accepted match, and drops party frames whose `partyId`
differs. Revisions are per-stream (party revision vs match revision) and are
never compared across streams.

### 9.4 Explicit switching

Host action "Change game", allowed only in `Lobby`:

1. Validate: game registered with a multiplayer contract, canonical ID,
   capacity (§8.4), every member supports it.
2. Atomically set `selectedGame`, reset every `ready` flag, clear the
   settings digest (game defaults), increment the party revision.
3. Broadcast `PartySnapshot`. Peers swap the lobby panel to the new game's
   binding `Settings` read-only view through the registry.
4. In-flight `SetReady` commands against the old revision fail with
   `StaleRevision` (existing semantics); the peer resubmits only by explicit
   user action.

Switching never happens during `MatchStarting`/`InMatch`. The host must end
the match first (§9.5).

### 9.5 Match lifecycle inside a party

```text
Lobby --host Start (all ready, count ok)--> MatchStarting
  MatchStarting: freeze match roster (party admissions stay OPEN for lobby joins in 5.0? see D-3),
                 SessionStarting(partyId, matchOrdinal=n+1) / Ready quorum (20 s) / Commit
MatchStarting --quorum fail/cancel--> Lobby (no ordinal change, start attempt discarded)
MatchStarting --commit--> InMatch(matchId)
InMatch --game terminal + host "Back to lobby"--> Lobby   (MatchEnded(Completed))
InMatch --host "End match"--> Lobby                        (SessionEnded(Cancelled) + MatchEnded)
any --host Leave / expiry--> Closing --> Ended             (PartyEnded)
```

`LocalRoom` needs two new transactional operations (5.0):

- `freezeMatchRoster(): Result<List<RoomMember>, NetError>`: like
  `closeAdmissions()` but scoped to one match. It doesn't retire lobby
  memberships.
- `reopenAfterMatch(matchId)`: idempotent. It clears the match roster and
  returns admission to the lobby rules. It is ownership-checked so a stale
  call for an earlier match is a no-op.

`ProcessMultiplayerSession` gains a `PartySession` owner. Its route is keyed by
`PartyId` + role (not `gameId`). Checkpoints and runtimes become per-match:
the existing `getOrCreateCheckpoint(kind)` kinds are prefixed with
`matchOrdinal`. The previous match's runtime is `close()`d before the next
match's is created. Existing GameRoom routes stay keyed by `gameId`.

### 9.6 Rematch

| Kind | Where | Identity | Notes |
|---|---|---|---|
| In-match rematch | Game reducer (Ghamza `Rematch(token)`, Whodunit Play Again, Last Light rematch, Dominoes next hand) | Same `SessionId` | Unchanged. Token/seed rules stay game-owned and host-only |
| Party "Play again" | Party lobby, host | New `SessionId`, `matchOrdinal + 1`, new host-only seed (`SecureIds.randomLong()`) | Same `selectedGame` and settings unless the host changes them; roster = currently connected, ready members |
| GameRoom "New room, same game" | Shell | New `RoomId`, code and tag | Phase 1–4 fallback; everyone rejoins |

A party rematch reuses the full start barrier. It never "resets" a finished
reducer state in place, so old-match commands can't cross the new match
(compare `GhamzaRecoveryJourneyTest.kt:60-64`).

---

## 10. Lifecycle state machines

### 10.1 Discovery list (joiner)

```text
Idle --open list--> Browsing --path unsatisfied--> NoNetwork --path ok--> Browsing
Browsing --120 s no action--> Paused --"Search again"--> Browsing
Browsing --tap row--> Joining(entryKey) --success--> (leave list; flow cancelled)
Joining --definitive failure--> Browsing (row quarantined per §5.4) + error banner
any --background / leave screen--> Idle (discovery and kit stopped)
```

### 10.2 Join attempt (joiner)

```text
Start(target: AllCandidates | Discovered(entryKey) | Pinned(fp, roomId))
 -> Dialing (5 s/attempt, 30 s total)
 -> SecureConnected -> RequestSent
 -> Pending(lobby, window)   [UI countdown; Cancel = close session]
 -> Offered -> Staged -> Confirmed -> Committed -> Ready -> Member
Rejections:
  WrongCode/IncompatibleProtocol (all-candidates) -> next candidate
  WrongGame(hostGame) -> Failed(WrongGame); clear the attempt and return to the selected game
                         (a new attempt bound to hostGame; never automatic)
  StaleRoom / pin mismatch -> Failed(RoomClosed)
  ApprovalExpired / HostDeclined / RoomFull / SessionStarted / DisplayNameInUse -> Failed(reason)
Timeout (30 s before Pending; window after Pending) -> Failed(Timeout)
```

### 10.3 Host admission row

```text
Requested -> Pending(expiresAt) --approve (atomic seat)--> Reserved -> Offered -> Committed -> Member
Pending --decline--> Rejected(HostDeclined)
Pending --expiresAt--> Rejected(ApprovalExpired)
Pending --session closed--> Removed
Reserved/Offered --confirm timeout or close--> Rolled back (seat released)
```

### 10.4 PartySession (host)

```text
Creating -> Lobby <-> (MatchStarting -> InMatch -> Lobby)*
Lobby/InMatch --background--> Suspended(deadline) --foreground ok--> previous
Suspended --deadline--> Expired -> Ended
any --Leave--> Closing -> Ended
```

### 10.5 Peer membership

```text
Member(Lobby) <-> Member(InMatch(matchId))
Member --transport loss--> Resuming(≤120 s, pinned fingerprint) --ResumeCommitted--> Member(...)
Resuming --InvalidCredential/expired--> Ended (credential discarded, ownership-checked)
Member --Leave--> Ended (finalLeave; credential deleted)
Member --PartyEnded/SessionEnded(HostLeft)--> Ended
```

---

## 11. Reconnect, background and persistence

- **Credential schema v2** (Phase 2, protocol 4.3). Storage key
  `p2p-resumable-session-v2`, `CREDENTIAL_SCHEMA_VERSION = 2`. `gameId`,
  `gameVersion` and `roomId` are required. Schema 3 (Phase 5) adds `partyId`
  and makes `gameId`/`gameVersion` describe the selected game at the last
  commit.
- **Migration:** a 4.2 credential can never resume against a 4.3 host
  (exact protocol, and updating the app ends the host process). On first
  read after upgrade, a schema-1 record is reported as expired, not resumable,
  and removed with the existing ownership-checked discard. It is never
  rewritten as a v2 record with invented `roomId`, because the rule is not to
  normalize state into a different identity.
- **Resume targeting:** resume dials only the credential's host peer ID with
  the pinned fingerprint (existing behavior). It sends `roomId` and `game`
  (4.3) or `partyId` (5.0); the host verifies them against its credential
  record (§6.5).
- **Resume into a party:** after `ResumeCommitted` the host sends
  `PartySnapshot` first. If `activeMatchId` is set **and** the peer is in that
  match roster, the existing stable start-offer replay and initial snapshot
  request follow. Otherwise the peer stays in the lobby view.
- **Host persistence:** none. Host process death ends the party (§8.2),
  consistent with "no host migration".
- **Local snapshots:** multiplayer matches don't use local protected
  snapshots (unchanged). Party state is never written to `SnapshotStore`.
- **Navigation restore:** link-derived values (code, fingerprint) are not
  saved in restorable navigation state. After process death a peer recovers
  only through the protected credential and the Home resume tile
  (`StartupMultiplayerRecovery.kt`, `HomeRecoveryAvailability.kt`).
- **Backgrounding during discovery or pending approval:** the list flow is
  cancelled. A pending join attempt is cancelled and its session closed. The
  host's row disappears on close or expiry. Nothing auto-resumes a pending
  admission.

---

## 12. Exact code, file and module changes

Every change keeps the dependency direction (`AGENTS.md`). P2pKit imports stay
in `:shared:transport-p2p`. Game modules stay free of transport code. The
shell resolves games only through `GameShellRegistry`.

### 12.1 `:shared:core`

| File | Change | Phase |
|---|---|---|
| `src/commonMain/kotlin/com/parlor/core/ids/Ids.kt` | `GameId.isCanonical`, `requireCanonical`; new `RoomId`, `PartyId` value classes with grammar validation | 1 |
| `src/commonTest/...` | Grammar table tests (valid six IDs, reserved, uppercase, `_`, length 33, leading digit, double hyphen) | 1 |

### 12.2 `:shared:networking`

| File | Change | Phase |
|---|---|---|
| `protocol/Protocol.kt` | 4.3: `GameProtocolRef`, `AdmissionRequest.expectedGame/expectedRoomId`, `AdmissionPending.lobby/approvalWindowMillis`, `LobbyInfo`, `AdmissionRejected.hostGame`, new rejections, required offer `gameId/gameVersion/roomId`, `ResumeRequested.roomId/game`. 5.0: party messages, header `partyId`, `StaleMatch`, `PartyClosed` | 2, 5 |
| `protocol/ProtocolValidation.kt` | Canonical game ID check; validators for new messages (bounds, distinctness, window range, hex IDs) | 1, 2, 5 |
| new `lobby/LobbyAdvertisement.kt` | Pure encode/parse, size cap, sealed parse result | 3 |
| new `lobby/JoinLink.kt` | Pure strict link codec, redacting `toString` | 4 |
| `room/DiscoveredRoom.kt` | Defaulted fields (§5.3), `RoomCompatibility`, `RoomAvailability`, `LobbyMode` | 3 |
| `room/LocalRoom.kt` | `PendingAdmission.expiresInMillis`; `NetError.ApprovalExpired`, `WrongGame(hostGame)`, `StaleRoom`; 5.0 `freezeMatchRoster`, `reopenAfterMatch`, `retireMember` | 2, 5 |
| `transport/RoomTransport.kt` | `JoinConfig.expectedGame`, `target: JoinTarget` (`AllCandidates`/`Discovered`/`Pinned`); `HostConfig.mode`; doc update for `discoverRooms()` | 2, 3, 4 |
| `src/commonTest` | Codec round-trip and strict-reject tests for every new message; frame-size worst cases; advert and link fuzz tables | 2–5 |

### 12.3 `:shared:networking-testing`

| File | Change | Phase |
|---|---|---|
| In-memory room bus/transport | Advert list emulation, new rejections, approval expiry with a virtual clock, pinned targets, party frames | 2–5 |

### 12.4 `:shared:transport-p2p`

| File | Change | Phase |
|---|---|---|
| `P2pKitRoomTransport.kt` `host()` (`:263-347`) | Generate `RoomId`, `RoomTag` (CSPRNG); `deviceName = LobbyAdvertisement.encode(...)`; fail if > 64 B | 3 (RoomId in 2) |
| `HostP2pRoom.handleAdmissionRequest` (`:2331-2424`) | §6.2 order; `LobbyInfo`; pending `expiresAt`; reaper | 2 |
| `HostP2pRoom.admit` (`:2985-3045`) | Expiry re-check inside the same lock; offer includes `roomId`, required game | 2 |
| `awaitAdmission` (`:974-1180`) | Handle `WrongGame`/`StaleRoom`/`ApprovalExpired`; window from `Pending`; verify offer against `LobbyInfo` | 2 |
| `join()` (`:349-585`) | Filter by advert parse + exact `expectedGame`; `JoinTarget.Discovered` dials one candidate; `Pinned` uses `connect(peer, fp)` | 3, 4 |
| `isCurrentParlorHost` (`:1501-1520`), `P2P_ROOM_PREFIX` (`:1537`) | Replace with advert parser; keep legacy detection for the update banner | 3 |
| new `LobbyDiscoveryCache.kt` | Pure, bounded (32), quarantine timers, path-change clear; unit-tested with a fake clock | 3 |
| `discoverRooms()` override | Cold flow owning a kit; cancellation stops discovery/kit | 3 |
| `capability` (`:251-262`) | `supportsDiscovery = true` in the UI commit | 3 |
| `DiscoveryCandidateScheduler.kt` | Results `StaleRoom`, `Mismatch`; single-candidate mode | 3 |
| `ResumableCredentialStore.kt` | Schema 2 (+3), key v2, obsolete-v1 discard | 2, 5 |
| `P2pDiagnostics.kt` | Fixed names: `lobby_discovery_started`, `lobby_rows` (coarse count), `admission_wrong_game`, `admission_stale_room`, `admission_approval_expired`, `party_match_started`, `party_game_selected`. No IDs, tags, codes or names | 2–5 |
| `P2pTransportModule.kt`, `.android.kt:90`, `.ios.kt:65`, `.desktop.kt:87` | Drop `randomDeviceTag()` from adverts; peer kit uses a fixed non-personal name (after the T3.9 review) | 3 |
| New party host/peer room support (5.0) | Party router (§9.3), match-scoped freeze/reopen, `retireMember` keeps credential revocation atomic | 5 |

### 12.5 `:shared:session`

| File | Change | Phase |
|---|---|---|
| `multidevice/ProcessMultiplayerSessionOwner.kt` | Route accepts `JoinTarget`; 5.0 party route keyed by `PartyId`; match-scoped checkpoints/runtimes | 4, 5 |
| new `lobby/PartySessionCoordinator.kt` (package `com.parlor.session.lobby`, avoiding the existing readiness-gate `party` package) | Party reducer (pure), command validation, snapshot publishing, stale-match routing | 5 |
| `multidevice/AuthoritativeSessionCoordinator.kt` (`:1133-1150`) | Map `WrongSession` to `StaleMatch` in 5.0 | 5 |

### 12.6 `:composeApp`

| File | Change | Phase |
|---|---|---|
| `shell/game/GameShellRegistry.kt` | Canonical ID requirement; resolved game binding for `expectedGame`; 5.0 exposes a separate capability list | 1, 3 |
| `shell/multiplayer/JoinPromptScreen.kt` | Room list + code entry; tag shown; QR hint | 3 |
| new `shell/multiplayer/NearbyRoomsScreen.kt` | Game-scoped browser and Home overview; resolve a game contract before dialing | 3 |
| new `shell/multiplayer/JoinLinkRouter.kt` | Link → registry → binding launch (`GameShellLaunch.JoinLink`) | 4 |
| `shell/game/GameShellRegistry.kt` (`GameShellLaunch`) | New `JoinDiscovered`/`JoinLink` launches with redacting `toString` | 3, 4 |
| `shell/game/multiplayer/HostedGameLobby.kt`, `RoomGameLobbyUi.kt`, `LobbyComponents.kt` | Show tag + QR; approval countdown; disable Approve when full | 2–4 |
| `shell/game/multiplayer/PeerGameLobby.kt` | exact `expectedGame`; `WrongGame` ends the attempt | 2 |
| `AppNavigationHost.kt` / Home | "Join nearby" entry; deep-link entry | 3, 4 |
| `src/androidMain/AndroidManifest.xml` + `MainActivity` | `parlor://join` intent filter; `onNewIntent` forwarding | 4 |
| `build.gradle.kts` (`verifyGameShellDispatch`, `:735`) | No change expected; new shell files must pass it | 3, 4 |
| Party lobby UI (new `shell/party/**`) | Lobby, game picker, ready, play again, end match | 5 |

### 12.7 Game modules

`WhodunitHostSessionFlow.kt:206-209`, `WhodunitPeerSessionFlow.kt:203-210`,
`MafiaHostLobbyFlow.kt:165-168`, `MafiaPeerLobbyFlow.kt:172-179`,
`LastLightHostLobbyFlow.kt:163-166` and `LastLightPeerLobbyFlow.kt:158-165`
call `transport.host/join/resumeLastSession` directly. Phase 2 passes
exact `expectedGame` and handles the new errors there, or (preferred, Phase 3)
moves those three games onto the shared `HostedGameLobby`/`PeerGameLobby`
path. Game reducers, codecs and content don't change for Phases 1–4. Phase 5
needs each binding to expose a read-only `Settings` preview and a
"back to party" exit from its terminal screen.

### 12.8 iOS app

| File | Change | Phase |
|---|---|---|
| `iosApp/iosApp/Info.plist` | `CFBundleURLTypes` (`parlor`); no camera key unless D-6 | 4 |
| `iosApp/iosApp/iOSApp.swift` / `ContentView.swift` | `onOpenURL` → Kotlin root controller entry | 4 |
| `iosApp/iosApp/PrivacyInfo.xcprivacy` | Review only (no new tracking/required-reason APIs expected) | 4 |

### 12.9 Documentation (with the corresponding implementation phases)

ADR-0003 (identity, advert, link, party); updates to
`PRODUCTION_ARCHITECTURE.md`, `P2P_MANUAL_TEST.md` (new rows, §16.4),
`PRIVACY_AND_COMPLIANCE.md` (advert contents, share/link, scheme hijack),
`HOW_TO_ADD_A_GAME.md` (canonical ID), `RELEASE_GATES.md` (new physical rows),
and `docs/review/INDEPENDENT_REVIEW_INVENTORY.csv` regeneration per change.

---

## 13. Phases, dependencies and acceptance

```text
P0 Decisions/ADR-0003
 ├─> P1 Canonical IDs (no wire change)
 │    └─> P2 Protocol 4.3 game-aware admission + approval expiry + credential v2
 │          ├─> P3 Advert v1 + discovery list + Smart Join UI
 │          │     └─> P4 QR / link (pinned)
 │          └────────> P5 Protocol 5.0 PartySession (needs P3 for mode p advert)
 └─> P6 Physical matrix + docs (runs after each of P2–P5 ships to devices)
```

| Phase | Tasks | Depends on | Acceptance (all must hold) |
|---|---|---|---|
| **P0** | T0.1 Write ADR-0003 with D-1…D-10 resolved. T0.2 Product copy for every new error (EN/AR). T0.3 Threat-model review of advert/link/share. | — | ADR accepted; copy reviewed; this plan updated with the decisions |
| **P1** | T1.1 `GameId` canonical API + tests. T1.2 Registry `require`. T1.3 Header validator uses the canonical grammar. T1.4 Audit test fixtures; strict constructor or recorded exception. | P0 | `:shared:core:desktopTest`, `:shared:networking:desktopTest`, `:composeApp:desktopTest` pass; a non-canonical ID fails registry init and header validation in tests; no wire bump |
| **P2** | T2.1 4.3 schema + validators. T2.2 Host check order (§6.2) and `LobbyInfo`. T2.3 Host approval expiry + reaper. T2.4 Peer handling of new rejections. T2.5 Credential schema 2 + v1 obsolete discard. T2.6 In-memory transport parity. T2.7 Shell/game flows pass exact `expectedGame`; `WrongGame` ends the attempt. T2.8 Diagnostics names. | P1 | Regression tests: (a) cross-game code → `WrongGame` **before** any `PendingAdmission` is published; (b) expired approval cannot reserve a seat (virtual clock); (c) concurrent approvals never exceed `seatLimit`; (d) `StaleRoom` for a mismatched `expectedRoomId`; (e) v1 credential is discarded, not resumed; (f) 4.2↔4.3 pairs fail closed; (g) logs contain no code/tag/ID/name. `:shared:transport-p2p:desktopTest`, `:shared:session:desktopTest`, `productionDesktopCheck` pass |
| **P3** | T3.1 Advert codec. T3.2 Host advert + `RoomTag`. T3.3 `LobbyDiscoveryCache`. T3.4 `discoverRooms()`. T3.5 Single-candidate join. T3.6 Room list UI (game card + Home entry) via registry. T3.7 Legacy banner. T3.8 `supportsDiscovery = true`. T3.9 P2pKit Hello review → peer device-name change. T3.10 Move Whodunit/Mafia/Last Light onto shared lobby or wire them individually. | P2 | Cache table (§5.4) covered by fake-clock tests; advert ≤ 64 B for every registered game; wrong-game rooms are never dialed in filtered mode (test counts `connect` calls); `verifyGameShellDispatch` passes; EN/AR a11y labels contain no names/codes; PHY rows in §16.4 recorded UNVERIFIED until executed |
| **P4** | T4.1 Link codec + strict tests. T4.2 QR encoder (D-7). T4.3 Host QR with concealment. T4.4 Android intent filter + `onNewIntent`. T4.5 iOS URL type + `onOpenURL`. T4.6 Pinned join target. T4.7 Share action (D-8). | P3 | Malformed/unknown/duplicate/percent-encoded links rejected (table tests); pinned join never connects to a non-matching fingerprint (fake kit test); `simctl openurl` / `adb am start -d` launch tests open the correct binding or fail closed; no IP/port anywhere in the link grammar (contract test) |
| **P5** | T5.1 5.0 schema. T5.2 Pure party reducer + tests. T5.3 Transport match-scoped freeze/reopen, `retireMember`. T5.4 Party router + `StaleMatch`. T5.5 Session owner party route; per-match runtimes. T5.6 Credential schema 3. T5.7 Party lobby UI, game switching, Play again. T5.8 Per-binding read-only settings preview + "back to party". | P3 (and P2) | Old-match command after a switch → `StaleMatch`, no reducer call; switching to an under-capacity game rejected; a peer disconnected at match start keeps party membership and returns to the lobby on resume; host Leave → every peer ends and deletes its credential; `PartySnapshot` serialization test proves no private/host-only fields; all six games complete two consecutive party matches in the desktop deterministic harness |
| **P6** | T6.1 New P2P_MANUAL_TEST rows. T6.2 Execute the four-combination matrix per phase. T6.3 Privacy/compliance and Store label review. | each phase | Evidence records per `P2P_MANUAL_TEST.md`; any unexecuted row stays UNVERIFIED; no release claim from simulator rows |

Each phase is one or more coherent Conventional Commits with its regression
tests. Do not combine a protocol bump with unrelated changes.

---

## 14. Performance

| Concern | Budget / approach |
|---|---|
| Dial cost | Today the joiner may complete up to n authenticated handshakes (5 s each, 30 s total). Filtered and single-candidate joins cut that to 1 dial for list/QR entry; typed-code joins dial only same-game, same-protocol rooms |
| Discovery battery | Browse only while the list is visible; auto-pause at 120 s; no background browsing |
| Host CPU / memory | Pending rows bounded (existing); one reaper coroutine per room; probe (if D-4) on a separate bounded budget |
| Frame sizes | `AdmissionRequest` < 1 KiB, `AdmissionPending` < 0.5 KiB, `PartySnapshot` < 2.5 KiB, all well under 8 KiB control |
| Party snapshot fan-out | ≤ 17 recipients × ≤ 2.5 KiB per revision; coalesce `ready` toggles into one snapshot per 100 ms on the host |
| UI | Room list `StateFlow` with `distinctUntilChanged`; stable keys = `entryKey`; no recomposition per `lastSeen` tick |
| Advert churn | One advert per room lifetime (PK-2), so there is no advert churn |
| Switch latency | Game switch = one party revision; no kit restart and no reconnect |

Measurements belong in the physical rows (time from list open to first row,
from tap to `Pending`, and from host Start to all-peer commit), recorded per
device pair. No numbers are promised before measurement.

---

## 15. Privacy

| Data | Advert (LAN-visible) | Link/QR | Encrypted admission | Logs/diagnostics | A11y labels |
|---|---|---|---|---|---|
| Game ID + version | yes | yes | yes | yes (fixed field only if added deliberately; default no) | yes |
| Room tag | yes | yes | — | **no** | yes (host screen + list) |
| Room code | **never** | yes | yes | **never** | host screen only (existing) |
| Room ID / Party ID / Match ID | no | Room ID only | yes | **never** | never |
| Host fingerprint | P2pKit `fp` (existing PK-7) | yes | yes | never | never |
| Host display name | **no** (D-5 opt-in only) | no | yes (`LobbyInfo`, after correct code) | never | after admission only |
| Member names / counts | no | no | yes | coarse count bucket only (existing) | lobby, members only |
| Private game state | no | no | match `PlayerSnapshot` only, own slice | never | never |

Additional rules:

- The P2pKit stable `pid` and `fp` in TXT already let LAN observers recognize
  a device across sessions (PK-7). Smart Lobby adds no stable identifier.
  `RoomTag` is per-room and CSPRNG, replacing the process-lifetime device tag
  (G-6).
- QR and code are concealed with the private-UI concealment rules when
  inactive or backgrounded. Screenshots in automated tests must use synthetic
  rooms, and test artifacts that capture QR/code are treated as sensitive
  (§16.3).
- The share sheet is the only path that moves the code off the LAN. It must
  be explicit (D-8).
- `toString()` of every new route/launch/link/config redacts code,
  fingerprint and IDs (pattern: `MultiplayerSessionRoute.toString`,
  `ProcessMultiplayerSessionOwner.kt:103-110`).
- `PRIVACY_AND_COMPLIANCE.md` must list the advert contents, link contents,
  custom-scheme hijack risk and share behavior before Phase 3/4 ship.

---

## 16. Test plan

### 16.1 Deterministic tests (CI, every phase)

The user-specified [ui-test-actions project](https://github.com/ui-test-actions/ui-test-actions)
can drive and capture an Android emulator or iOS simulator within a
GitHub-hosted action session. Use its documented platform profiles for
observable UI steps when the repository's Actions allowlist permits a pinned
revision. This control channel does not bridge game LAN traffic between
runners. Existing XCUITest, Compose UI and UIAutomator remain usable without
changing the allowlist. Keep real discovery/transport acceptance on a
demonstrated shared LAN.

- Pure codecs and validators: advert, link, every 4.3/5.0 message (round trip,
  strict rejects, worst-case size), canonical IDs.
- `LobbyDiscoveryCache` and approval reaper with a virtual clock.
- Host admission order and atomic capacity against the in-memory transport
  (`:shared:networking-testing`) and against `P2pKitRoomTransport` with a fake
  `P2pKit` (existing pattern in `:shared:transport-p2p:desktopTest`).
- Party reducer property tests: revision monotonicity, `StaleMatch` routing,
  switching invariants, rematch identity.
- Privacy contract tests: serialized `PartySnapshot`, `LobbyInfo`, diagnostics
  and a11y labels contain no forbidden fields.
- Desktop deterministic harness: all six games, two consecutive party matches.

### 16.2 Four Android/iOS LAN combinations

| Combination | Real-topology evidence (required for release) | What a hosted emulator/simulator can and cannot show |
|---|---|---|
| **Android host → Android client** | Two physical Android phones on one normal Wi-Fi network (PHY-01 extended: list, filtered join, wrong-game rejection, approval expiry, QR via system camera, party switch, host leave). Repeat with roles swapped | Two emulators on one Linux runner each sit behind their own virtual router (10.0.2.0/24 per instance). NSD/mDNS multicast does not cross instances, and inbound TCP needs `adb` redirection, so **emulator↔emulator discovery is not real topology**. Hosted value: single-emulator launch, deep-link intent (`adb shell am start -a android.intent.action.VIEW -d parlor://join?...`), UI and fail-closed tests. The existing managed-device smoke's multicast check is single-device evidence only (`docs/RELEASE_GATES.md` Android device runtime row) |
| **iOS host → iOS client** | Two physical iPhones (PHY-02 extended), including Local Network denial → Settings recovery and AWDL/peer-to-peer behavior | Several Simulators on one Mac share the Mac's network stack, so Bonjour between them *may* work. It is **not established in this repository** and doesn't exercise the Local Network privacy prompt/denial path, AWDL, Wi-Fi changes or real radio loss. GitHub-hosted macOS runner multicast behavior is likewise unverified here. Hosted value: `xcrun simctl openurl <udid> parlor://join?...` routing, single-device UI, `productionIosSimulatorRuntimeTests` for pure logic |
| **Android host → iOS client** | Physical Android host + physical iPhone (PHY-03 extended) | Android emulator NAT blocks inbound connections and multicast from an iOS Simulator on the same Mac. **No hosted configuration counts as evidence** |
| **iOS host → Android client** | Physical iPhone host + physical Android (PHY-04 extended) | Same as above with roles reversed. **No hosted evidence** |

Desktop JVM instances use P2pKit's JmDNS LAN path (`JvmLanDiscoveryTransport`)
and can exercise the real P2pKit admission code paths on one development
machine. That is useful for protocol debugging but is neither Android nor
iOS evidence.

### 16.3 UI test action limitations

The available UI test actions (XCUITest, Compose UI tests, UIAutomator, and
agent-driven device tap/type/screenshot actions) drive **one device per
driver**. Known limits for Smart Lobby:

- There is no cross-device synchronization primitive. Host approve / joiner
  pending / countdown sequences need an external coordinator, and timing is
  inherently racy. Assert on observable states, never on sleeps.
- iOS Local Network permission: the Simulator doesn't present the real prompt,
  and the prompt can't be granted or denied by a scripted action in a
  supported way. Denial/recovery stays a physical row.
- Camera: the iOS Simulator has no camera. Android emulator camera scenes are
  not a reliable QR source. Test QR **content** by decoding the rendered
  bitmap in a unit test, and test **scan → open** with `simctl openurl` /
  `adb am start -d`. The system camera recognizing the code is a physical row.
- Network state: Wi-Fi toggling, interface changes and multicast loss can't be
  driven on the iOS Simulator and only partially on emulators. Path-change
  cache clearing is unit-tested with a fake `networkPathStatus`.
- Background and process death: `XCUIDevice` home/terminate and `adb`
  force-stop are available but don't reproduce OS memory-pressure kills (see
  the existing Android saved-session process-boundary gate for its limits).
- Privacy: UI actions can show that a label exists, not that a secret is
  absent everywhere. Absence is asserted by serialization and log contract
  tests. Screenshots and recordings that show a room code or QR are sensitive
  artifacts and must use synthetic rooms.
- Accessibility: automated label assertions don't replace physical
  TalkBack/VoiceOver rows.

### 16.4 New physical rows (to add to `docs/P2P_MANUAL_TEST.md` in P6)

| ID | Scenario | PASS criterion |
|---|---|---|
| SL-01 | Two hosts with different games on one LAN; joiner opens the list from Home | Both rows appear with the correct game/tag within the recorded time; tapping dials only that host |
| SL-02 | Joiner chose Mafia, types a Whodunit room's code | No host approval prompt appears; joiner sees a wrong-game error and remains in Mafia; no automatic retry or cross-game routing |
| SL-03 | Host leaves the approval pending 60 s | Host row and joiner both show expiry; a late Approve doesn't admit |
| SL-04 | Room at capacity; host approves two pending simultaneously | Exactly one admitted; the other stays pending or is rejected `RoomFull`; seat count is never exceeded |
| SL-05 | QR scanned with the system camera, then the host closes and reopens a room | First QR joins; reusing it after reopen shows "room has closed" |
| SL-06 | Host closes the room; joiner list on iOS | The row disappears or becomes unavailable on tap, and never joins a dead room |
| SL-07 | Party: two matches, switch game between them, one peer backgrounded at the second start | Switch is explicit; the backgrounded peer returns to the lobby, not the match; old-match actions are rejected |
| SL-08 | Party host leaves mid-match | Every peer ends, credentials are removed, no resume tile remains after expiry |
| SL-09 | Mixed 4.2 / 4.3 devices | 4.3 joiner shows the legacy-update banner; neither side enters a lobby |

Each row runs in all four §16.2 combinations, which gives 36 cells per
release candidate. Unexecuted cells are **UNVERIFIED**.

---

## 17. Implications (not implemented here)

### 17.1 Party Night

A "Party Night" (a curated sequence of games with a running scoreboard) is a
**client of PartySession (P5)**, not a separate transport mode. It needs:

- a host-only playlist state in the party reducer: ordered `GameProtocolRef`s
  plus settings, advanced only by explicit host action (the §9.4 switching
  rules apply to every step);
- a public, game-agnostic match outcome (§17.4) for the scoreboard;
- capacity planning: every playlist game must accept the party size, or the
  playlist editor must flag it before the night starts;
- content rotation (§17.2) so repeated Whodunit cases are avoided within one
  night.

It must not add host migration, persistence of host party state, or
auto-advance without host action.

### 17.2 Rules and content

- Game rules don't change. Each match uses the game's existing reducer,
  settings validation and start barrier. Switching games mid-party never
  alters a running match.
- Whodunit content stays bundled/offline. A party needs a **host-only**
  per-party history of played `caseId`s (never in `PartySnapshot`) to avoid
  repeats. The `caseVersion`/`caseDigest` start validation is unchanged.
- Shipping player-count limits (for example Whodunit 6..6,
  `WhodunitGameShellBinding.kt:42`) feed the §8.4 capacity checks directly.
  When a game expands its range, party capacity follows automatically.
- Rules/help screens should be reachable from the party lobby for the
  selected game. That is a binding-owned composable, keeping App.kt dispatch
  free of game names.

### 17.3 Profile and stats

- No profile or stats storage exists today (§3.1). A profile would be **local
  only** (display name, preferences) in `:shared:storage`, separate from
  rejoin credentials and game snapshots.
- `PlayerId` is the per-install P2pKit peer ID. Using it to recognize other
  people across sessions ("friends", head-to-head records) creates a
  cross-session tracking identifier and needs explicit consent and a privacy
  review. The default recommendation is to record stats only for the local
  user and to store opponents only as display names within one party.
- Stats must not be computed from private projections a player didn't see
  publicly (for example another player's role before the end-of-game
  reveal).

### 17.4 Outcomes

A future engine-level contract (pure, in `:shared:engine`, no UI or
transport):

```kotlin
data class MatchOutcome(
    val gameId: GameId, val gameVersion: Int, val matchId: SessionId,
    val reason: OutcomeReason,          // Completed | Abandoned | HostLeft
    val placements: List<Placement>,    // public, end-of-game-revealed data only
)
```

It would be emitted once, by the host, from a terminal canonical state, and
derived only from data already public at game end. Each game owns its
`outcomeOf(state)`. A party could broadcast it in `MatchEnded` (a 5.x minor
field). Peers display it; they never compute it. This plan deliberately leaves
`MatchEnded` without an outcome field so outcomes can be designed and reviewed
separately.

---

## 18. Design decisions resolved in ADR-0003

| ID | Question | Decision |
|---|---|---|
| D-1 | Ship 4.3 (GameRoom Smart Lobby) before 5.0 (Party), or one 5.0 break? | Two steps. 4.3 delivers most user value with smaller risk; 5.0 follows with physical evidence |
| D-2 | Make the `GameId` constructor strict? | Keep the constructor lenient in P1 to preserve boundary error classification; enforce the canonical grammar at entry boundaries |
| D-3 | Can new members join the party lobby while a match runs? | No in 5.0: admissions stay closed during `MatchStarting`/`InMatch` (simplest capacity story); reconsider later |
| D-4 | Encrypted lobby probe for open/full status? | Not in the first release (§5.5) |
| D-5 | Advertise the host display name (opt-in)? | No by default; at most a per-room opt-in toggle with clear copy |
| D-6 | In-app QR scanner? | No for the first release; use system camera + custom scheme |
| D-7 | QR encoder: dependency or vendored? | A reviewed KMP dependency if one passes DEPENDENCY_REVIEW; otherwise a small vendored encoder with golden tests |
| D-8 | Share-sheet invites? | Secondary, explicit action with a one-time explanation |
| D-9 | Persist the last display name locally for prefill? | Yes, local settings only, user-clearable |
| D-10 | Party benching when the next game seats fewer players? | No; reject the switch and require explicit member removal |

---

## Appendix A. Reproducing the evidence

```bash
git fetch origin main && git rev-parse origin/main   # d9f78320ffdfa3f4b22342f975166b214dca9671 at planning time
C=~/.gradle/caches/modules-2/files-2.1/io.github.apdelrahman1911
sha256sum $C/p2p-core-jvm/0.7.0-rc3/*/p2p-core-jvm-0.7.0-rc3.jar \
          $C/p2p-transport-lan-jvm/0.7.0-rc3/*/p2p-transport-lan-jvm-0.7.0-rc3.jar \
          $C/p2p-transport-lan-android/0.7.0-rc3/*/p2p-transport-lan.aar
grep -n -A6 '"p2p-core-jvm" version="0.7.0-rc3"' gradle/verification-metadata.xml
javap -cp <core jar> dev.p2pkit.core.Peer dev.p2pkit.core.P2pKit            # PK-1, PK-2, PK-8
javap -p -constants -cp <lan jar> dev.p2pkit.transport.lan.LanConstants      # PK-3
javap -p -c -constants -cp <lan jar> dev.p2pkit.transport.lan.LanKt          # PK-4 (truncateLanTxtValue, 255)
javap -p -c -constants -cp <core jar> dev.p2pkit.core.dsl.P2pKitBuilder      # PK-5 (512 / 2048)
unzip -p <ios lan klib> default/ir/strings.knt | strings | grep -i bonjour   # PK-6
```

The cached artifacts are only used if their digests match
`gradle/verification-metadata.xml`. Decompiled bytecode is review evidence, not
a public API contract. Any implementation must re-verify these facts against
the then-pinned P2pKit version.
