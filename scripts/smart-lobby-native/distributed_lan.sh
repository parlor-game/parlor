#!/usr/bin/env bash
set -euo pipefail

root="$RUNNER_TEMP/parlor-distributed"
proof="$RUNNER_TEMP/parlor-proof"
driver="$GITHUB_WORKSPACE/scripts/smart-lobby-native/distributed_lan.py"
upstream="$LAN_UI_DIR/verification/mobile-lan"
seat="${LAB_SEAT:?}"
layout="${LAB_LAYOUT:?}"
[[ "$seat" =~ ^[1-6]$ ]]
cd "$GITHUB_WORKSPACE"
peer_wait_seconds=415
if jq -e 'index("ios") != null' <<< "$layout" >/dev/null; then
  # macOS assignment can take longer than six Android guests provisioning.
  peer_wait_seconds=900
fi

probe_peer() {
  local peer="$1" packets="$2" platform octet address
  platform=$(jq -r ".[$((peer - 1))]" <<< "$layout")
  if [[ "$platform" == android ]]; then octet=$((9 + peer)); else octet=$((19 + peer)); fi
  address="192.168.137.$octet"
  if [[ "$TARGET" == android ]]; then
    adb -s emulator-5554 shell ping -I wlan0 -c "$packets" -W 2 "$address"
  else
    ping -S "192.168.137.$((19 + seat))" -c "$packets" -W 2000 "$address"
  fi
}

case "${1:?}" in
  identity)
    umask 077
    mkdir -p "$root/public" "$proof"
    python3 -m venv "$root/venv"
    "$root/venv/bin/pip" install --disable-pip-version-check 'websockets==15.0.1'
    cc -O2 -Wall -Wextra -Werror "$upstream/frame-port.c" -o "$root/frame-port"
    # Each runner generates its own identity. Only public certificates and
    # the hub's short-lived relay address enter rendezvous artifacts.
    openssl req -x509 -newkey ed25519 -nodes -days 1 -subj "/CN=parlor-lab-seat-$seat" \
      -keyout "$root/key.pem" -out "$root/public/cert.pem"
    if [[ "$seat" == 1 ]]; then
      openssl req -x509 -newkey ed25519 -nodes -days 1 -subj /CN=ui-mobile-lan \
        -addext subjectAltName=DNS:ui-mobile-lan -keyout "$root/relay-key.pem" -out "$root/public/relay-cert.pem"
      "$root/venv/bin/python" "$upstream/open-tunnel.py" "$root/tunnel" "$root/public/origin" \
        > "$root/tunnel.log" 2>&1 &
      printf '%s\n' "$!" > "$root/tunnel.pid"
      for _ in {1..110}; do
        [[ ! -s "$root/public/origin" ]] || exit 0
        kill -0 "$(cat "$root/tunnel.pid")"
        sleep 1
      done
      exit 1
    fi
    ;;
  hub|attach)
    count=$(jq 'length' <<< "$layout")
    mkdir -p "$root/identities"
    if [[ "$1" == attach ]]; then count=1; fi
    rendezvous_deadline=$((SECONDS + peer_wait_seconds))
    rendezvous_receipt="$proof/distributed-$1-rendezvous.json"
    while true; do
      missing=""
      for ((peer=1; peer<=count; peer++)); do
        identity="$root/identities/$peer"
        if [[ ! -s "$identity/cert.pem" ]]; then
          if [[ "$peer" == "$seat" ]]; then
            mkdir -p "$identity"
            cp "$root/public/"* "$identity/"
          else
            gh run download "$GITHUB_RUN_ID" --repo "$GITHUB_REPOSITORY" \
              --name "parlor-distributed-lan-$peer-$GITHUB_RUN_ATTEMPT" --dir "$identity" >/dev/null 2>&1 || true
          fi
        fi
        [[ -s "$identity/cert.pem" ]] || missing="${missing}${missing:+,}$peer"
      done
      if [[ -z "$missing" ]]; then
        jq -n --arg stage "$1" --argjson planned "$count" \
          --argjson elapsed "$((peer_wait_seconds - rendezvous_deadline + SECONDS))" \
          '{stage:$stage,expected_identities:$planned,elapsed_seconds:$elapsed,missing_seats:[]}' > "$rendezvous_receipt"
        break
      fi
      if (( SECONDS >= rendezvous_deadline )); then
        jq -n --arg stage "$1" --argjson planned "$count" --argjson missing "[$missing]" \
          --argjson elapsed "$((peer_wait_seconds - rendezvous_deadline + SECONDS))" \
          '{stage:$stage,expected_identities:$planned,elapsed_seconds:$elapsed,missing_seats:$missing}' > "$rendezvous_receipt"
        printf 'Seat certificate rendezvous timed out. Missing seat numbers: %s\n' "$missing" >&2
        exit 1
      fi
      sleep 3
    done
    hub="$root/identities/1"
    if [[ "$1" == hub ]]; then
      [[ "$seat" == 1 ]]
      # Write a newline after every PEM so the bundle is unambiguous.
      for ((peer=1; peer<=count; peer++)); do
        cat "$root/identities/$peer/cert.pem"
        printf '\n'
      done > "$root/clients.pem"
      for ((peer=1; peer<=count; peer++)); do
        openssl verify -purpose sslclient -CAfile "$root/clients.pem" "$root/identities/$peer/cert.pem" >/dev/null
      done
      LAN_UI_DIR="$LAN_UI_DIR" "$root/venv/bin/python" "$driver" relay --layout "$layout" --seat 1 \
        --identities "$root/identities" --cert "$hub/relay-cert.pem" --key "$root/relay-key.pem" \
        --trust "$root/clients.pem" --stats "$proof/distributed-relay.json" >"$root/relay.log" 2>&1 &
      printf '%s\n' "$!" > "$root/relay.pid"
      for _ in {1..20}; do
        [[ ! -s "$proof/distributed-relay.json" ]] || break
        kill -0 "$(cat "$root/relay.pid")"
        sleep 1
      done
      if [[ ! -s "$proof/distributed-relay.json" ]]; then
        printf 'The authenticated relay did not start
' >&2
        exit 1
      fi
      exit 0
    fi
    if [[ "$TARGET" == android ]]; then
      interface=uilan0
      # Android 35 denies RTM_GETLINK to the unprivileged shell. Read this
      # owned guest's MAC from the dedicated TAP's DHCP acknowledgement.
      address="192.168.137.$((9 + seat))"
      mac=$(awk -v address="$address" '$0 ~ /DHCPACK\(uilan0\)/ {
        for (i = 1; i < NF; i++) if ($i == address) mac = $(i + 1)
      } END {print mac}' "$RUNNER_TEMP/mobile-lan-preflight/dhcp.log")
    else
      interface=feth81
      mac=$(ifconfig feth80 | awk '/ether / {print $2}')
    fi
    if [[ ! "$mac" =~ ^([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}$ ]]; then
      printf 'Owned interface did not expose a valid MAC
' > "$proof/distributed-edge-error.txt"
      exit 1
    fi
    sudo -n env LAN_UI_DIR="$LAN_UI_DIR" "$root/venv/bin/python" "$driver" edge --layout "$layout" --seat "$seat" \
      --interface "$interface" --mac "$mac" --frame-port "$root/frame-port" --origin "$(cat "$hub/origin")" \
      --cert "$root/public/cert.pem" --key "$root/key.pem" --trust "$hub/relay-cert.pem" \
      --stats "$proof/distributed-edge.json" --ready "$root/ready" > "$root/edge.log" 2>&1 &
    printf '%s\n' "$!" > "$root/edge.pid"
    edge_deadline=$((SECONDS + peer_wait_seconds + 30))
    while (( SECONDS < edge_deadline )); do
      [[ ! -f "$root/ready" ]] || exit 0
      if ! sudo -n kill -0 "$(cat "$root/edge.pid")"; then
        grep -E '^(Seat setup: |[a-zA-Z.]+[A-Z][A-Za-z]+:)' "$root/edge.log" |
          sed -nE '/^Seat setup: /p; s/^([a-zA-Z.]+[A-Z][A-Za-z]+):.*/\1/p' > "$proof/distributed-edge-error.txt" || true
        exit 1
      fi
      sleep 1
    done
    exit 1
    ;;
  verify)
    count=$(jq 'length' <<< "$layout")
    for ((peer=1; peer<=count; peer++)); do
      [[ "$peer" != "$seat" ]] || continue
      record="$proof/distributed-icmp-to-$peer.txt"
      for attempt in {1..5}; do
        if probe_peer "$peer" 3 > "$record" 2>&1 &&
           grep -Eq '3 (packets )?received' "$record"; then break; fi
        (( attempt != 5 )) || exit 1
        sleep 2
      done
    done
    ;;
  observe)
    # The hub probes each edge. Each edge probes back to the hub. Keep only
    # job-clock timing and the outcome, with no addresses or packet data.
    last_peer=$(jq 'length' <<< "$layout")
    if [[ "$seat" != 1 ]]; then last_peer=1; fi
    deadline=$((SECONDS + 85 * 60))
    while (( SECONDS < deadline )) && [[ -f "$root/ready" ]]; do
      for ((peer=1; peer<=last_peer; peer++)); do
        [[ "$peer" != "$seat" ]] || continue
        started=$(date -u +%s)
        delivered=false
        if probe_peer "$peer" 1 >/dev/null 2>&1; then delivered=true; fi
        printf '{"utc_epoch_seconds":%s,"from":%s,"to":%s,"icmp_reply":%s}\n' \
          "$started" "$seat" "$peer" "$delivered"
      done
      sleep 8
    done
    ;;
  cleanup)
    for process in observer edge relay tunnel; do
      if [[ -s "$root/$process.pid" ]]; then
        sudo -n kill -TERM "$(cat "$root/$process.pid")" 2>/dev/null || true
      fi
    done
    ;;
  *) exit 2 ;;
esac
