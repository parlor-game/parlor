"""Verify exact installed-source and per-peer transport evidence from a completed lab."""
import argparse
import hashlib
import json
from pathlib import Path


def load_receipt(root, platform, run, app_sha, harness_sha, candidate_hash):
    source = (root / "source.txt").read_text().splitlines()
    if len(source) != 2 or source[0] != app_sha:
        raise ValueError("Installed source receipt does not match the candidate")
    if (root / "harness-sha.txt").read_text().strip() != harness_sha:
        raise ValueError("Unexpected harness receipt")
    if (root / "workflow-run.tsv").read_text().split("\t")[0] != str(run):
        raise ValueError("Unexpected workflow receipt")
    packages = (root / "SHA256SUMS").read_text().split()
    if len(packages) != 2 or packages[0] != candidate_hash:
        raise ValueError("Package hash differs from original build")
    if platform == "android":
        apk, = root.glob("*/installed-apk-sha256.txt")
        if apk.read_text().strip() != candidate_hash:
            raise ValueError("Installed Android APK differs from original build")
    else:
        manifest, = root.glob("*/installed-app-manifest.json")
        if not json.loads(manifest.read_text()):
            raise ValueError("Empty installed iOS manifest")
    return {"tree": source[1], "platform": platform, "package_sha256": candidate_hash}


def verify(args):
    if not 1 <= args.host <= len(args.roster):
        raise ValueError("Invalid host seat")
    readers = {
        seat: args.proof / f"{platform}{seat}" / "parlor-proof"
        for seat, platform in enumerate(args.roster, 1)
    }
    relay = json.loads((readers[1] / "distributed-relay.json").read_text())["frames"]
    for seat in readers:
        if not relay.get(f"authenticated_{seat}"):
            raise ValueError(f"Seat {seat} has no authenticated overlay stream")
    receipts = {}
    for seat, platform in enumerate(args.roster, 1):
        root = readers[seat]
        candidate_hash = args.android_hash if platform == "android" else args.ios_hash
        receipts[seat] = load_receipt(root, platform, args.run, args.app_sha, args.harness_sha, candidate_hash)
        for peer in readers.keys() - {seat}:
            probe = (root / f"distributed-icmp-to-{peer}.txt").read_text()
            if "3 received" not in probe and "3 packets received" not in probe:
                raise ValueError(f"Unverified directed ICMP path from {seat} to {peer}")
        if seat == args.host:
            continue
        directions = {f"{seat}->{args.host}", f"{args.host}->{seat}"}
        if not all(relay.get(f"{path}:tcp") for path in directions):
            raise ValueError(f"Missing bidirectional TCP between match host and seat {seat}")
        if not relay.get(f"{args.host}->{seat}:p2pkit_mdns_response"):
            raise ValueError(f"Missing native P2pKit advertisements from match host to seat {seat}")
    if len({value["tree"] for value in receipts.values()}) != 1:
        raise ValueError("The application source tree differs across devices")
    verdict = json.loads(args.result.read_text())
    winners = verdict.get("winners", verdict.get("results", []))
    if len(winners) != len(receipts) or len(set(winners)) != 1 or not winners[0]:
        raise ValueError("Each independent app must show the same winner")
    pictures = args.scorecard or sorted(args.result.parent.glob("*-public-result.png"))
    picture_seats = {f"{platform}-{seat}" for seat, platform in enumerate(args.roster, 1)}
    proofs = {}
    for picture in pictures:
        image = picture.read_bytes()
        seat = "-".join(picture.stem.split("-")[:2])
        if seat not in picture_seats or not image.startswith(b"\x89PNG\r\n\x1a\n"):
            raise ValueError("Expected one public PNG scorecard per independently controlled device")
        picture_seats.remove(seat)
        proofs[picture.name] = hashlib.sha256(image).hexdigest()
    if picture_seats:
        raise ValueError("Missing a public scorecard for an independently controlled device")
    return {"run": args.run, "app_sha": args.app_sha, "harness_sha": args.harness_sha,
            "host_seat": args.host, "installs": receipts, "native_relay_counts": relay,
            "matched_result": verdict, "scorecard_sha256": proofs}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--proof", type=Path, required=True)
    parser.add_argument("--result", type=Path, required=True)
    parser.add_argument("--run", type=int, required=True)
    parser.add_argument("--host", type=int, required=True)
    parser.add_argument("--roster", choices=("android", "ios"), nargs="+", required=True)
    parser.add_argument("--scorecard", type=Path, action="append",
                        help="Public device PNG for this route, one per seat. Omit for a single-route directory.")
    for name in ("app-sha", "harness-sha", "android-hash", "ios-hash"):
        parser.add_argument(f"--{name}", required=True)
    args = parser.parse_args()
    print(json.dumps(verify(args), indent=2))


if __name__ == "__main__":
    main()
