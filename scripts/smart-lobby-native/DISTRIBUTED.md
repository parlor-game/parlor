# Distributed Smart Lobby device lab

The distributed mode uses one disposable device per GitHub-hosted job. The
first job also hosts a short-lived relay over the same carrier as the proven
two-runner lab. An Android guest joins through its dedicated emulator TAP.
The iOS app uses a Simulator on a disposable macOS VM and reaches the lab
through its paired fake Ethernet interfaces.

Each job provisions a unique IPv4 address and presents its own ephemeral TLS
certificate with a unique subject. The relay authenticates the certificates from the current
workflow attempt and binds each connection to one seat. It forwards allowed
ARP, ICMP, peer TCP and mDNS Ethernet frames. It distributes mDNS multicast
within the roster and preserves the native DNS answers and TCP endpoints.
Ethernet and ARP addresses are mapped to per-seat MAC aliases so factory MAC
reuse across runner images cannot collapse independently provisioned seats.
DHCP, default routes, runner interfaces, and unrelated traffic stay outside
the overlay. TLS keys and controller bearers remain private.

## Run a qualification

1. Set the candidate app SHA and exact build producer. Use one Android APK and
   one Apple Silicon Simulator bundle from that source. The first distributed
   experiment uses the retained products from run 45121464371. Each runner
   verifies its installed files against its platform's original build.
2. On the dedicated test branch, set the dispatch payload to
   `pairing: distributed`, `reuse_candidate: true`, and a two-to-six-entry
   `platforms` roster. Generate a fresh 32-byte bearer as 64 lowercase hex characters per seat. Validate that format locally and put only the
   SHA-256 digests in the dispatch file, in roster order. A push modifying that
   file schedules the targeted lab. The controller pin is recorded in proof.
3. Wait for each seat's controller artifact. The hub starts before provisioning. The overlay waits for every seat
   certificate and TLS connection, then each guest probes every other guest
   before its controller starts. A queued or failed seat leaves the run
   unqualified. Download controller documents privately. Use the seat
   number in the artifact/filename and its matching, distinct bearer.
4. Drive installed Parlor apps through the normal game UI, starting with host
   creation, native discovery and admission. Capture readiness and start,
   actual gameplay, and each final public screen. Store safe screenshots and
   the action timeline. Never publish room codes, keys, control documents,
   secrets, or images of hidden roles.
5. Check the proof artifacts for exact source and device installs, directed
   LAN probes, and redacted native app events. The hub's frame totals should
   show the expected native P2pKit service advertising/browsing and TCP
   directions between host and each peer. The final winner or score must
   agree on every device. Connectivity by itself is only infrastructure
   evidence. Reconnect evidence needs the same match and seats before and
   after the disruption plus legal actions after recovery.
   Pass one `--scorecard` per seat to `verify_distributed_proof.py` when
   validating a specific game from a directory with several completed
   routes. The verifier requires an advertisement from the match host to
   each peer in addition to its bidirectional TCP frames.
6. Use one controlled roster for compatible standalone and Party matches
   where the device lease permits. Retire the owned workflow when finished and
   verify its native artifacts are published before updating the matrix.

## Scaling bounds

- Start with Android, Android, iOS for an open three-seat Ghamza route. Scale
  to a six-job roster after that run proves discovery and gameplay. Reuse a
  six-seat lab only where the game's roster matches the support matrix.
- GitHub's published Free, Pro and Team allowance is five concurrent macOS
  jobs. Keep mixed rosters at or below five Macs, accounting for other running
  jobs. Six iOS seats need a proven two-Simulator Mac allocation plus four
  one-Simulator jobs, a higher allowance, or an authorized private lab.
- Two guests on each runner is a separate capacity experiment. Android's
  supported same-host emulator Wi-Fi uses a different backend from the
  existing single-guest cross-runner TAP. It must be proven with its own
  directed and discovery preflights before relying on it. More than six
  devices do not unlock a current roster row by themselves.
- A runner VPN addresses IP connectivity. It does not demonstrate guest
  multicast discovery. An alternative self-hosted lab needs an approved
  isolated L2 segment with guest-to-guest mDNS and TCP and independent
  device controls, such as coordinated Mac and KVM capacity on a private
  test VLAN.
- Simulator qualification does not cover physical camera pairing, local
  network privacy prompts, actual hotspots, or signed Store acceptance.

[GitHub Actions limits](https://docs.github.com/en/actions/reference/limits)
· [GitHub private networking](https://docs.github.com/en/actions/how-tos/manage-runners/github-hosted-runners/connect-to-a-private-network)
· [Pinned two-runner proof](https://github.com/ui-test-actions/ui-test-actions/blob/0c818c5d72e8c30c59bd1287c330a40392bf8e89/docs/mobile-lan.md)
· [Android emulator networking](https://developer.android.com/studio/run/emulator-networking-interconnect)
· [Apple local-network privacy](https://developer.apple.com/documentation/technotes/tn3179-understanding-local-network-privacy)
