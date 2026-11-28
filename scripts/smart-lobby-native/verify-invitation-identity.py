"""Compare native Party invitation captures without exporting any invitation fields."""

import argparse
import hmac
import json
from pathlib import Path
import re
from urllib.parse import parse_qsl, urlsplit

from PIL import Image
import zxingcpp

FIELDS = {"v", "party", "p", "c", "r", "t", "f"}
CODE = r"[A-HJ-NP-Z2-9]"
IDENTIFIER = r"[0-9a-f]{32}"
FINGERPRINT = r"p2f1-[a-z2-7]{51}[aq]"


def read_invitation(path):
    with Image.open(path) as image:
        decoded = zxingcpp.read_barcodes(image, formats=zxingcpp.BarcodeFormat.QRCode)
    if len(decoded) != 1:
        raise ValueError("Capture must contain exactly one QR code")
    raw = decoded[0].text
    link = urlsplit(raw)
    if len(raw) > 256 or not raw.startswith("parlor://join?") or link.fragment:
        raise ValueError("Capture does not contain a Parlor Party invitation")
    pairs = parse_qsl(link.query, strict_parsing=True, max_num_fields=len(FIELDS))
    fields = dict(pairs)
    if len(pairs) != len(FIELDS) or fields.keys() != FIELDS:
        raise ValueError("Unexpected Party invitation fields")
    patterns = {"v": "2", "p": r"5\.2", "party": IDENTIFIER, "r": IDENTIFIER,
                "c": CODE + "{6}", "t": CODE + "{4}", "f": FINGERPRINT}
    if any(not re.fullmatch(pattern, fields[key]) for key, pattern in patterns.items()):
        raise ValueError("Invitation failed the expected wire shape")
    return fields


def compare_host_invitations(before, after, independent, platform):
    """Return only booleans. Fingerprint equality does not prove a rejoin."""
    return {
        "platform": platform,
        "advertised_fingerprint_retained": hmac.compare_digest(before["f"], after["f"]),
        "independent_host_fingerprint_differs": not hmac.compare_digest(before["f"], independent["f"]),
        "same_party_id_advertised": hmac.compare_digest(before["party"], after["party"]),
        "same_room_id_advertised": hmac.compare_digest(before["r"], after["r"]),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--before", type=Path, required=True)
    parser.add_argument("--after", type=Path, required=True)
    parser.add_argument("--independent-host", type=Path, required=True)
    parser.add_argument("--platform", choices=("android", "ios"), required=True)
    parser.add_argument(
        "--require-same-room",
        action="store_true",
        help="Require the original Party and Room IDs. Always qualify authenticated rejoin separately.",
    )
    args = parser.parse_args()
    before, after, other = (read_invitation(path) for path in (args.before, args.after, args.independent_host))
    result = compare_host_invitations(before, after, other, args.platform)
    print(json.dumps(result, indent=2))
    identity_ok = result["advertised_fingerprint_retained"] and result["independent_host_fingerprint_differs"]
    same_room = result["same_party_id_advertised"] and result["same_room_id_advertised"]
    return 0 if identity_ok and (same_room or not args.require_same_room) else 1


if __name__ == "__main__":
    raise SystemExit(main())
