"""Restricted CI LAN joining disposable TAP/BPF ports through the pinned relay."""
import argparse
import asyncio
from collections import Counter
from contextlib import suppress
import hashlib
from ipaddress import IPv4Address
import json
import os
from pathlib import Path
import signal
import ssl
import sys
import time

# Reuse the proven two-runner frame allowlist, WSS carrier and native port.
sys.path.insert(0, str(Path(os.environ["LAN_UI_DIR"]) / "verification/mobile-lan"))
from bridge import (FRAME_MAX, MDNS, P2PKIT_SERVICE, WS_OPTIONS, duplex, endpoint, permitted,
                    websocket_proxy)
from websockets.asyncio.client import connect
from websockets.asyncio.server import serve
from websockets.exceptions import InvalidStatus

PREFIX = "192.168.137."
ETH_ARP = b"\x08\x06"
LOOPBACK = "127.0.0.1"
BROADCAST = b"\xff" * 6
MDNS_MAC = bytes.fromhex("01005e0000fb")


def address(seat, platform):
    if not 1 <= seat <= 6 or platform not in ("android", "ios"):
        raise ValueError("Invalid distributed seat")
    return IPv4Address(PREFIX + str((9 if platform == "android" else 19) + seat))


def aliases(seat):
    # Separate identical factory MACs on otherwise independent runner images.
    return b"\x02PARL" + bytes([seat])


def candidates(frame, source, roster):
    if not 14 <= len(frame) <= FRAME_MAX:
        return []
    if frame[12:14] == ETH_ARP:
        destination = frame[38:42]
        broadcast = False
    else:
        destination = frame[30:34]
        broadcast = destination == MDNS
    return [seat for seat, peer in roster.items() if seat != source
            and (broadcast or destination == peer) and permitted(frame, roster[source], peer)]


def destination_mac(frame, seat):
    return frame[:6] in (aliases(seat), BROADCAST, MDNS_MAC)


def rewrite(frame, old, new, *, outgoing):
    """Map the local MAC at the edge, including ARP payloads."""
    changed = bytearray(frame)
    field = slice(6, 12) if outgoing else slice(0, 6)
    if changed[field] == old:
        changed[field] = new
    if frame[12:14] == ETH_ARP:
        field = slice(22, 28) if outgoing else slice(32, 38)
        if changed[field] == old:
            changed[field] = new
    return bytes(changed)


def protocol(frame):
    if frame[12:14] == ETH_ARP:
        return "arp"
    if frame[23] != 17:
        return "tcp" if frame[23] == 6 else "icmp"
    if len(frame) > 46 and P2PKIT_SERVICE in frame[42:].lower():
        return "p2pkit_mdns_response" if frame[44] & 0x80 else "p2pkit_mdns_query"
    return "mdns"


async def records(reader):
    while True:
        header = await reader.readexactly(2)
        size = int.from_bytes(header, "big")
        if not 14 <= size <= FRAME_MAX:
            raise ValueError("Invalid frame size")
        yield await reader.readexactly(size)


async def write_frame(writer, frame):
    writer.write(len(frame).to_bytes(2, "big") + frame)
    await asyncio.wait_for(writer.drain(), 3)


def stats_write(path, data):
    tmp = path.with_suffix(".tmp")
    tmp.write_text(json.dumps(data, sort_keys=True) + "\n")
    tmp.replace(path)


async def sample(path, counts, active):
    try:
        while True:
            stats_write(path, {"active_seats": sorted(active), "frames": counts})
            await asyncio.sleep(2)
    finally:
        stats_write(path, {"active_seats": sorted(active), "frames": counts})


def tls(args, *, server):
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER if server else ssl.PROTOCOL_TLS_CLIENT)
    context.minimum_version = ssl.TLSVersion.TLSv1_3
    context.verify_mode = ssl.CERT_REQUIRED
    context.load_cert_chain(args.cert, args.key)
    context.load_verify_locations(args.trust)
    return context


async def relay(args):
    pinned = {hashlib.sha256(ssl.PEM_cert_to_DER_cert(
        (args.identities / str(seat) / "cert.pem").read_text())).digest(): seat
        for seat in args.roster}
    if len(pinned) != len(args.roster):
        raise ValueError("Duplicate seat certificate")
    writers = {}
    counts = Counter()
    proxy_budget = asyncio.Semaphore(2 * len(args.roster))

    async def peer(reader, writer):
        cert = writer.get_extra_info("ssl_object").getpeercert(binary_form=True)
        seat = pinned.get(hashlib.sha256(cert).digest())
        if seat is None or seat in writers:
            writer.close()
            return
        writers[seat] = writer
        counts[f"authenticated_{seat}"] += 1
        try:
            await writer.drain()
            while len(writers) < len(args.roster):
                await asyncio.sleep(.15)
                if reader.at_eof():
                    return
            writer.write(b"\0\0")
            await asyncio.wait_for(writer.drain(), 3)
            async for frame in records(reader):
                if frame[6:12] != aliases(seat):
                    counts[f"rejected_source_{seat}"] += 1
                    continue
                for target in candidates(frame, seat, args.roster):
                    remote = writers.get(target)
                    if remote is not None and destination_mac(frame, target):
                        try:
                            await write_frame(remote, frame)
                        except (OSError, TimeoutError):
                            remote.close()
                        else:
                            counts[f"{seat}->{target}:{protocol(frame)}"] += 1
        except (ValueError, OSError, asyncio.IncompleteReadError, TimeoutError):
            counts[f"closed_{seat}"] += 1
        finally:
            counts[f"last_departure_epoch_{seat}"] = int(time.time())
            writers.pop(seat, None)
            writer.close()

    async with await asyncio.start_server(peer, LOOPBACK, 0, ssl=tls(args, server=True),
                                          ssl_handshake_timeout=8) as inner:
        inner_port = inner.sockets[0].getsockname()[1]

        async def proxy(websocket):
            if proxy_budget.locked() or websocket.request.path != "/lan":
                await websocket.close(code=1008)
                return
            async with proxy_budget:
                reader, writer = await asyncio.open_connection(LOOPBACK, inner_port)
                await websocket_proxy(websocket, reader, writer)

        async with serve(proxy, LOOPBACK, 8788, **WS_OPTIONS):
            await sample(args.stats, counts, writers)


async def ready_link(port, context, counts):
    """Wait for a live relay and its full-roster barrier."""
    loop = asyncio.get_running_loop()
    deadline = loop.time() + 415
    while loop.time() < deadline:
        writer = None
        try:
            reader, writer = await asyncio.open_connection(
                LOOPBACK, port, ssl=context, server_hostname="ui-mobile-lan", ssl_handshake_timeout=20)
            if await asyncio.wait_for(reader.readexactly(2), deadline - loop.time()) != b"\0\0":
                raise ValueError("Invalid roster signal")
            return reader, writer
        except ssl.SSLCertVerificationError:
            raise
        except (OSError, asyncio.IncompleteReadError, TimeoutError) as error:
            if writer is not None:
                writer.close()
            counts[f"attach_retry:{type(error).__name__}"] += 1
            if time_left := deadline - loop.time():
                await asyncio.sleep(max(0, min(3, time_left)))
    raise TimeoutError("Relay or all-seat barrier deadline elapsed")


async def attached_edge(args, port, context, mac, counts):
    reader, writer = await ready_link(port, context, counts)
    process = None
    try:
        process = await asyncio.create_subprocess_exec(
            args.frame_port, args.interface, stdin=asyncio.subprocess.PIPE, stdout=asyncio.subprocess.PIPE)
        if await asyncio.wait_for(process.stdout.readexactly(2), 8) != b"\0\0":
            raise RuntimeError("Native frame port not ready")
        counts["edge_sessions"] += 1
        args.ready.touch()
        own = aliases(args.seat)

        async def outbound():
            async for frame in records(process.stdout):
                if frame[6:12] == mac and candidates(frame, args.seat, args.roster):
                    await write_frame(writer, rewrite(frame, mac, own, outgoing=True))
                    counts[f"sent:{protocol(frame)}"] += 1

        async def inbound():
            async for frame in records(reader):
                source = frame[6:12][-1]
                if (source in args.roster and frame[6:12] == aliases(source)
                        and args.seat in candidates(frame, source, args.roster)
                        and destination_mac(frame, args.seat)):
                    await write_frame(process.stdin, rewrite(frame, own, mac, outgoing=False))
                    counts[f"received:{protocol(frame)}"] += 1

        await duplex(inbound(), outbound())
    finally:
        args.ready.unlink(missing_ok=True)
        if process and process.returncode is None:
            with suppress(ProcessLookupError):
                process.terminate()
            await process.wait()
        writer.close()
        with suppress(OSError, TimeoutError):
            await asyncio.wait_for(writer.wait_closed(), 2)


async def supervise_edge(args, port, context, mac, counts):
    for _ in range(12):
        try:
            await attached_edge(args, port, context, mac, counts)
        except ssl.SSLCertVerificationError:
            raise
        except (ConnectionError, asyncio.IncompleteReadError, TimeoutError) as error:
            counts[f"edge_disconnect:{type(error).__name__}"] += 1
            counts["last_disconnect_epoch"] = int(time.time())
            await asyncio.sleep(2)
    raise RuntimeError("The isolated LAN seat exceeded its bounded reconnect attempts")


async def edge(args):
    counts = Counter()
    mac = bytes.fromhex(args.mac.replace(":", ""))
    if len(mac) != 6 or mac[0] & 1:
        raise ValueError("Expected a real unicast interface MAC")

    async def proxy(reader, writer):
        try:
            async with connect(endpoint(args.origin), **WS_OPTIONS) as websocket:
                await websocket_proxy(websocket, reader, writer)
        except InvalidStatus as error:
            counts[f"relay_http:{error.response.status_code}"] += 1
        except OSError as error:
            counts[f"relay_proxy:{type(error).__name__}"] += 1
        finally:
            writer.close()

    async with await asyncio.start_server(proxy, LOOPBACK, 0) as forward:
        port = forward.sockets[0].getsockname()[1]
        await duplex(supervise_edge(args, port, tls(args, server=False), mac, counts),
                     sample(args.stats, counts, [args.seat]))


async def run(args):
    task = asyncio.current_task()
    asyncio.get_running_loop().add_signal_handler(signal.SIGTERM, task.cancel)
    with suppress(asyncio.CancelledError):
        if args.role == "relay":
            await relay(args)
        else:
            await edge(args)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("role", choices=("relay", "edge"))
    parser.add_argument("--layout", required=True)
    parser.add_argument("--seat", type=int, required=True)
    for name in ("cert", "key", "trust", "identities", "stats", "ready"):
        parser.add_argument(f"--{name}", type=Path)
    for name in ("origin", "mac", "frame-port", "interface"):
        parser.add_argument(f"--{name}")
    args = parser.parse_args()
    layout = json.loads(args.layout)
    if not 2 <= len(layout) <= 6 or not 1 <= args.seat <= len(layout):
        raise ValueError("Invalid runner roster")
    args.roster = {seat: address(seat, platform).packed for seat, platform in enumerate(layout, 1)}
    asyncio.run(run(args))


if __name__ == "__main__":
    main()
