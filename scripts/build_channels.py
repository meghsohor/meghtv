"""Builds the list the app downloads: the iptv-org copy, the custom list on top, hidden entries dropped.

Usage: build_channels.py [--check] [--out DIR] [--live URL] [--force]
  --check  validate only
  --out    write channels.json and manifest.json there
  --live   the published site; skip when unchanged, refuse a big drop unless --force
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

from channel_data import ChannelData, exclusive_categories, load, validate

SCHEMA_VERSION = 1
# A deliberate mass hide (a whole category) needs --force; anything else this big is probably a mistake.
MIN_SHARE_OF_LIVE = 0.5


def merged_channels(data: ChannelData) -> list[dict]:
    channels = {c["id"]: dict(c) for c in data.channels}
    for entry in data.custom_channels:
        channel = channels.get(entry["id"])
        if channel is None:
            channels[entry["id"]] = {"visible": True, **entry}
            continue
        for key in ("name", "country", "categories", "visible"):
            if key in entry:
                channel[key] = entry[key]
        if "urls" in entry:
            channel["urls"] = list(dict.fromkeys(entry["urls"] + channel["urls"]))
    return list(channels.values())


def build(data: ChannelData) -> dict:
    exclusive = exclusive_categories(data)
    channels = []
    for c in merged_channels(data):
        if not c["visible"]:
            continue
        only = [cat for cat in c["categories"] if cat in exclusive]
        if only:
            # Shown only in its exclusive categories: no country, so no flag and no Countries entry.
            c = {**c, "categories": only, "country": ""}
        channels.append(c)
    used_categories = {cat for c in channels for cat in c["categories"]}
    used_countries = {c["country"] for c in channels if c["country"]}
    return {
        "schemaVersion": SCHEMA_VERSION,
        "categories": [
            {"id": c["id"], "name": c["name"], **({"exclusive": True} if c["id"] in exclusive else {})}
            for c in data.categories + data.custom_categories
            if c["visible"] and c["id"] in used_categories
        ],
        "countries": [
            {"code": c["code"], "name": c["name"], "flag": c["flag"]}
            for c in data.countries
            if c["visible"] and c["code"] in used_countries
        ],
        "channels": [
            {"id": c["id"], "name": c["name"], "country": c["country"], "categories": c["categories"], "urls": c["urls"]}
            for c in channels
        ],
    }


def empty_parts(listing: dict) -> list[str]:
    """Never published, --force or not: the app would replace every channel with nothing."""
    return [key for key in ("channels", "categories", "countries") if not listing[key]]


def fetch_live_manifest(base: str, attempts: int = 3) -> dict | None:
    for attempt in range(attempts):
        try:
            with urllib.request.urlopen(f"{base.rstrip('/')}/manifest.json", timeout=30) as response:
                return json.load(response)
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None  # nothing published yet
            if attempt == attempts - 1:
                raise
        except urllib.error.URLError:
            if attempt == attempts - 1:
                raise
        time.sleep(10 * (attempt + 1))
    return None


def set_output(name: str, value: str) -> None:
    path = os.environ.get("GITHUB_OUTPUT")
    if path:
        with open(path, "a", encoding="utf-8") as f:
            f.write(f"{name}={value}\n")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--out", type=Path)
    parser.add_argument("--live")
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args()

    data = load()
    errors = validate(data)
    if errors:
        print("\n".join(errors[:200]), file=sys.stderr)
        sys.exit(f"{len(errors)} problems in data/")
    listing = build(data)
    print(f"{len(listing['channels'])} channels, {len(listing['categories'])} categories, {len(listing['countries'])} countries")
    if empty_parts(listing):
        sys.exit(f"nothing visible in: {', '.join(empty_parts(listing))}")
    if args.check or not args.out:
        return

    body = json.dumps(listing, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    sha256 = hashlib.sha256(body).hexdigest()
    live = fetch_live_manifest(args.live) if args.live else None
    if live and live.get("sha256") == sha256:
        print("unchanged since the last publish")
        set_output("changed", "false")
        return
    if live and not args.force and len(listing["channels"]) < live.get("channels", 0) * MIN_SHARE_OF_LIVE:
        sys.exit(f"{len(listing['channels'])} channels against {live['channels']} live; run by hand with force if intended")

    manifest = {
        "schemaVersion": SCHEMA_VERSION,
        "updatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "sha256": sha256,
        "size": len(body),
        "channels": len(listing["channels"]),
    }
    args.out.mkdir(parents=True, exist_ok=True)
    (args.out / "channels.json").write_bytes(body)
    (args.out / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(manifest))
    set_output("changed", "true")


if __name__ == "__main__":
    main()
