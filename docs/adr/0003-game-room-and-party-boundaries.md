# ADR-0003: Game room and party boundaries

Status: Accepted for implementation

Related: [Smart Lobby specification](../planning/SMART_LOBBY.md)

## Decisions

1. Land canonical identifiers and protocol 4.3 admission in separate reviewable
   changes. Qualify 4.3 game-room browsing and admission together before
   shipping. A game-scoped flow must have a compatible game-scoped advert or
   a validated, fingerprint-pinned invitation before dialing. Legacy or
   unknown adverts never become dial candidates. The authenticated admission
   check remains authoritative because an advert can lie.
2. Audit all shipped and fixture identifiers before making the GameId
   constructor strict. Reject unknown wire identifiers at the game registry.
   Preserve existing valid IDs and treat rename as a storage migration. P1
   keeps the value-class constructor lenient so wire and persistence boundaries
   can classify untrusted values using their existing domain errors. Enforce
   the canonical grammar explicitly at registration and protocol boundaries.
3. Keep parties on their own protocol, introduced at 5.0. Party 5.2 carries
   the ordered playlist, completed match IDs, and host-confirmed score receipts
   in the lobby snapshot. Peers must use a compatible Party protocol. Standalone
   game-room admission remains on 4.3. A party admission and capability
   declaration never substitutes for an exact game-room admission. Close party
   admissions during active matches. Reject a game switch if the current
   membership cannot play it. Host departure ends the party.
4. Keep v1 adverts immutable and public-data-only. Exclude display names,
   capacity and availability probes from the first version. Refresh or expire
   rows using discovery lifecycle signals.
5. Use system QR scanning first and a strictly validated versioned invite.
   A user-initiated share sheet explains the same-LAN requirement. Resolve
   encoder selection through dependency review before adding the dependency.
   If no library qualifies, vendor a small encoder with golden tests. Share
   stays a secondary action with a same-LAN explanation on first use.
6. Profile prefill is local and clearable. A cosmetic name grants no access
   and supplies no recovery authority. Treat durable outcomes and scoring as
   separate consumers of the qualified match lifecycle.

## Verification

Reject wrong-game typing, discovery and QR before host prompt. Cover the six
canonical game IDs as both expected and competing room identities. Prove that
late events from a canceled join, stale credentials and old match traffic
cannot enter a replacement operation. Qualify Android and iOS LAN behavior
across all four host/client combinations before enabling the corresponding
release behavior.
