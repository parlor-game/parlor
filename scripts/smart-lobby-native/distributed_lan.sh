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

case "${1:?}" in
  identity)
    umask 077
    mkdir -p "$root/public" "$proof"
    python3 -m venv "$root/venv"
    "$root/venv/bin/pip" install --disable-pip-version-check 'websockets==15.0.1'
    cc -O2 -Wall -Wextra -Werror "$upstream/frame-port.c" -o "$root/frame-port"
    # Each runner generates its own identity. Only public certificates and
    # the hub's short-lived relay address enter rendezvous artifacts.
    openssl req -x509 -newkey ed25519 -nodes -days 1 -subj /CN=ui-mobile-lan \
      -addext subjectAltName=DNS:ui-mobile-lan -keyout "$root/key.pem" -out "$root/public/cert.pem"
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
  attach)
    count=$(jq 'length' <<< "$layout")
    mkdir -p "$root/identities"
    for attempt in {1..90}; do
      missing=0
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
        [[ -s "$identity/cert.pem" ]] || missing=1
      done
      (( missing )) || break
      (( attempt != 90 )) || exit 1
      sleep 3
    done
    hub="$root/identities/1"
    if [[ "$seat" == 1 ]]; then
      # Write a newline after every PEM so the bundle is unambiguous.
      for ((peer=1; peer<=count; peer++)); do
        cat "$root/identities/$peer/cert.pem"
        printf '\n'
      done > "$root/clients.pem"
      LAN_UI_DIR="$LAN_UI_DIR" "$root/venv/bin/python" "$driver" relay --layout "$layout" --seat 1 \
        --identities "$root/identities" --cert "$hub/relay-cert.pem" --key "$root/relay-key.pem" \
        --trust "$root/clients.pem" --stats "$proof/distributed-relay.json" >"$root/relay.log" 2>&1 &
      printf '%s\n' "$!" > "$root/relay.pid"
      for _ in {1..20}; do
        [[ ! -s "$proof/distributed-relay.json" ]] || break
        kill -0 "$(cat "$root/relay.pid")"
        sleep 1
      done
      [[ -s "$proof/distributed-relay.json" ]]
    fi
    if [[ "$TARGET" == android ]]; then
      interface=uilan0
      mac=$(adb -s emulator-5554 shell ip link show wlan0 | awk '/link\/ether/ {print $2}')
    else
      interface=feth81
      mac=$(ifconfig feth80 | awk '/ether / {print $2}')
    fi
    [[ "$mac" =~ ^([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}$ ]]
    sudo -n env LAN_UI_DIR="$LAN_UI_DIR" "$root/venv/bin/python" "$driver" edge --layout "$layout" --seat "$seat" \
      --interface "$interface" --mac "$mac" --frame-port "$root/frame-port" --origin "$(cat "$hub/origin")" \
      --cert "$root/public/cert.pem" --key "$root/key.pem" --trust "$hub/relay-cert.pem" \
      --stats "$proof/distributed-edge.json" --ready "$root/ready" > "$root/edge.log" 2>&1 &
    printf '%s\n' "$!" > "$root/edge.pid"
    for _ in {1..220}; do
      [[ ! -f "$root/ready" ]] || exit 0
      if ! sudo -n kill -0 "$(cat "$root/edge.pid")"; then
        grep -E '^[a-zA-Z.]+(Error|ReadError|Exception):' "$root/edge.log" | cut -d: -f1 > "$proof/distributed-edge-error.txt" || true
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
      platform=$(jq -r ".[$((peer-1))]" <<< "$layout")
      if [[ "$platform" == android ]]; then octet=$((9 + peer)); else octet=$((19 + peer)); fi
      address="192.168.137.$octet"
      if [[ "$TARGET" == android ]]; then
        probe=(adb -s emulator-5554 shell ping -I wlan0 -c 3 -W 2 "$address")
      else
        probe=(ping -S "192.168.137.$((19 + seat))" -c 3 -W 2000 "$address")
      fi
      record="$proof/distributed-icmp-to-$peer.txt"
      for attempt in {1..5}; do
        if "${probe[@]}" > "$record" 2>&1 &&
           grep -Eq '3 (packets )?received' "$record"; then break; fi
        (( attempt != 5 )) || exit 1
        sleep 2
      done
    done
    ;;
  cleanup)
    for process in edge relay tunnel; do
      if [[ -s "$root/$process.pid" ]]; then
        sudo -n kill -TERM "$(cat "$root/$process.pid")" 2>/dev/null || true
      fi
    done
    ;;
  *) exit 2 ;;
esac
