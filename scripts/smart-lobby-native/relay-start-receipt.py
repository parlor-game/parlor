"""Classify a stopped test relay without exposing dynamic URLs or private keys."""

import json
from pathlib import Path
import re
import sys

root = Path(sys.argv[1])
files = [root / "mobile-lan-preflight/tunnel-supervisor.log",
         root / "mobile-lan/tunnel/cloudflared.log"]
chunks = []
for source in files:
    if source.is_file():
        with source.open("rb") as stream:
            stream.seek(max(0, source.stat().st_size - 65536))
            chunks.append(stream.read().decode("utf-8", errors="replace"))
details = "\n".join(chunks)
receipt = {
    "result": "FAILED_BEFORE_PUBLISHING_LAN_ENDPOINT",
    "supervisor_log_present": files[0].is_file(),
    "connector_log_present": files[1].is_file(),
    "checksum_mismatch": "download checksum mismatch" in details,
    "route_not_proven": "Cloudflare connector did not prove the route" in details,
    "http_statuses": sorted(set(re.findall(r"(?:HTTP Error |status(?: code)?[=: ]+)([45][0-9]{2})", details, re.I))),
    "failure_classes": sorted(set(re.findall(r"(?m)^([A-Za-z]+Error|ConnectorUnavailable): ", details))),
}
output = root / "parlor-proof"
output.mkdir(exist_ok=True)
(output / "relay-startup.json").write_text(json.dumps(receipt, indent=2) + "\n")
print(json.dumps(receipt))
