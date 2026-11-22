"""Live repository metadata for pinned release lint; never generate lint findings."""
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import tempfile
import time
import urllib.request
import xml.etree.ElementTree as ET

GOOGLE_MAVEN = "https://dl.google.com/dl/android/maven2/"
MAVEN_CENTRAL = "https://repo.maven.apache.org/maven2/"
GOOGLE_INDEXES = ("master-index.xml", "androidx/navigation3/group-index.xml")
CENTRAL_CACHE = "maven-versions/repo1.maven.org/"
INVENTORY = "config/android-lint-accepted-warnings.txt"
MAX_BYTES = 2 * 1024 * 1024
VERSION = re.compile(r"[0-9]+\.[0-9]+\.[0-9]+(?:-[A-Za-z0-9.]+)?")
CATALOG_UPDATE = re.compile(
    r"DependencyUpdate\|gradle/libs\.versions\.toml\|A newer version of "
    r"([A-Za-z0-9_-]+(?:\.[A-Za-z0-9_-]+)*):([A-Za-z0-9][A-Za-z0-9._-]*) than (\S+)"
)


def fetch(url):
    with urllib.request.urlopen(url, timeout=20) as response:
        if response.status != 200 or response.geturl() != url:
            raise ValueError("Unexpected official index response")
        raw = response.read(MAX_BYTES + 1)
    if not raw or len(raw) > MAX_BYTES:
        raise ValueError("Official index outside size bound")
    return raw


def document(raw):
    if not raw or len(raw) > MAX_BYTES:
        raise ValueError("Unsafe official index")
    # Official indexes are UTF-8; reject alternate encodings before XML parsing
    # so a UTF-16 declaration cannot bypass the entity/DOCTYPE guard.
    decoded = raw.decode("utf-8")
    if "<!DOCTYPE" in decoded.upper() or "<!ENTITY" in decoded.upper() or "\x00" in decoded:
        raise ValueError("Unsafe official index")
    return ET.fromstring(decoded)


def validate(master, group, current):
    root = document(master)
    if root.tag != "metadata" or sum(child.tag == "androidx.navigation3" for child in root) != 1:
        raise ValueError("Master index does not identify Navigation3 uniquely")
    root = document(group)
    if root.tag != "androidx.navigation3" or len({child.tag for child in root}) != len(root):
        raise ValueError("Invalid Navigation3 group index")
    runtime = root.find("navigation3-runtime")
    if runtime is None or set(runtime.attrib) != {"versions"}:
        raise ValueError("Missing runtime version index")
    versions = runtime.attrib["versions"].split(",")
    if not versions or len(versions) > 512 or len(set(versions)) != len(versions):
        raise ValueError("Invalid runtime versions")
    if any(not VERSION.fullmatch(version) for version in versions) or current not in versions:
        raise ValueError("Runtime version not in official index")
    stable = [tuple(map(int, version.split("."))) for version in versions if "-" not in version]
    latest = max(stable)
    if latest <= tuple(map(int, current.split("."))):
        raise ValueError("Reviewed runtime update is not present in live official index")
    return ".".join(map(str, latest))


def central_coordinates(inventory):
    # Plugin marker updates use the Gradle Plugin Portal. AndroidX updates use
    # Google Maven; the remaining reviewed library advisories use Maven Central.
    coordinates = {}
    for line in inventory.splitlines():
        advisory = CATALOG_UPDATE.fullmatch(line.strip())
        if advisory:
            group, artifact, current = advisory.groups()
            if group.startswith("androidx."):
                continue
            coordinates[group, artifact] = current
    return coordinates


def validate_central(raw, group, artifact, current):
    metadata = document(raw)
    if metadata.tag != "metadata" or metadata.findtext("groupId") != group or metadata.findtext("artifactId") != artifact:
        raise ValueError(f"Unexpected Maven Central coordinate for {group}:{artifact}")
    versions = metadata.findall("./versioning/versions/version")
    if not any(version.text == current for version in versions):
        raise ValueError(f"Reviewed version {current} missing from Maven Central metadata for {group}:{artifact}")


def write_atomic(path, raw):
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary = tempfile.mkstemp(prefix=".lint-index-", dir=path.parent)
    try:
        with os.fdopen(fd, "wb") as stream:
            stream.write(raw)
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def prepare(root, download=fetch):
    root = Path(root).resolve(strict=True)
    catalog_bytes = (root / "gradle/libs.versions.toml").read_bytes()
    catalog = catalog_bytes.decode("utf-8").replace("\r\n", "\n")
    sections = re.findall(r"(?ms)^\[versions\]\s*\n(.*?)(?=^\[|\Z)", catalog)
    if len(sections) != 1:
        raise ValueError("Missing/duplicate reviewed versions section")
    section = sections[0]
    pins = {}
    for key in ("navigation3-runtime", "agp"):
        matches = re.findall(r'^' + re.escape(key) + r'\s*=\s*"([^"\n]+)"\s*$', section, re.MULTILINE)
        if len(matches) != 1:
            raise ValueError("Missing/duplicate reviewed catalog pin")
        pins[key] = matches[0]
    current = pins["navigation3-runtime"]
    if current != "1.0.0" or pins["agp"] != "8.13.2":
        raise ValueError("Re-review repository inputs after toolchain/dependency migration")
    properties = (root / "gradle.properties").read_text().splitlines()
    if "android.experimental.lint.version=9.1.1" not in properties:
        raise ValueError("Unsupported lint cache layout")
    google_paths = [f"maven.google/{index}" for index in GOOGLE_INDEXES]
    urls = dict(zip(google_paths, (GOOGLE_MAVEN + index for index in GOOGLE_INDEXES)))
    central = {}
    for (group, artifact), version in central_coordinates((root / INVENTORY).read_text()).items():
        relative = f"{group.replace('.', '/')}/{artifact}/maven-metadata.xml"
        path = CENTRAL_CACHE + relative
        urls[path] = MAVEN_CENTRAL + relative
        central[path] = (group, artifact, version)
    # Keep official fetches bounded without serializing every Central artifact.
    with ThreadPoolExecutor(max_workers=6) as pool:
        raw = dict(zip(urls, pool.map(download, urls.values())))
    latest = validate(*(raw[path] for path in google_paths), current)
    for path, coordinate in central.items():
        validate_central(raw[path], *coordinate)
    target = root / "composeApp/build/lint-repository-inputs"
    # Generated checkout-owned inputs only; never modify Gradle's dependency cache.
    for parent in (target, target.parent, target.parent.parent):
        if parent.is_symlink():
            raise ValueError("Symlinked lint input directory")
    for name, data in raw.items():
        path = target / name
        if any(parent.is_symlink() for parent in (path, *path.parents) if parent != root.parent):
            raise ValueError("Symlinked lint index")
        write_atomic(path, data)
    write_atomic(target / "cache-files.txt", ("\n".join(raw) + "\n").encode())
    receipt = dict(schema=2, source_head=os.environ.get("GITHUB_SHA"),
                   source_catalog_sha256=hashlib.sha256(catalog_bytes).hexdigest(),
                   lint="32.1.1", agp="8.13.2", current=current, latest_observed=latest,
                   inputs=[dict(path=name, url=urls[name], bytes=len(data), sha256=hashlib.sha256(data).hexdigest())
                           for name, data in raw.items()],
                   result="PASS_LIVE_REPOSITORY_INPUTS_NOT_LINT_QUALIFICATION")
    encoded = (json.dumps(receipt, indent=2) + "\n").encode()
    report = root / "composeApp/build/reports" / f"lint-repository-inputs-{time.time_ns()}.json"
    if any(parent.is_symlink() for parent in (report, *report.parents)):
        raise ValueError("Symlinked lint receipt directory")
    report.parent.mkdir(parents=True, exist_ok=True)
    with report.open("xb") as stream:
        stream.write(encoded)
    print(json.dumps(receipt))
    return receipt


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: android_lint_repository.py REPOSITORY_ROOT")
    prepare(sys.argv[1])
