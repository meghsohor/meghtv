"""Checks whether stream URLs work: playlist, first variant, first segment. Reads headers and a few bytes only.

Usage: check_urls.py [FILE ...] [--id ID ...]
  FILE  channel files to check (default: data/custom/channels.json)
  --id  only these channel ids
"""

from __future__ import annotations

import argparse
import json
import socket
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from channel_data import DATA

# Some servers turn away players they don't know, so a failure is retried as VLC.
AGENTS = ["MeghTV (Android; https://github.com/meghsohor/meghtv)", "VLC/3.0.20 LibVLC/3.0.20"]
TS_SYNC_BYTE = 0x47


def get(url: str, agent: str, limit: int = 65536, timeout: int = 20):
    request = urllib.request.Request(url, headers={"User-Agent": agent})
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return response.status, response.geturl(), response.headers.get("Content-Type", ""), response.read(limit)


def first_uri(base: str, text: str) -> str | None:
    for line in text.splitlines():
        line = line.strip()
        if line and not line.startswith("#"):
            return urllib.parse.urljoin(base, line)
    return None


def check_once(url: str, agent: str) -> str:
    status, final, ctype, body = get(url, agent)
    if body[:1] and body[0] == TS_SYNC_BYTE or ctype.startswith("video/"):
        return "OK (direct stream)"
    text = body.decode("utf-8", "replace").lstrip()
    if "<MPD" in text[:2048]:
        return "OK (DASH manifest only)"
    if not text.startswith("#EXTM3U"):
        return f"not a stream (HTTP {status}, {ctype or 'no type'})"
    if "#EXT-X-STREAM-INF" in text:
        status, final, ctype, body = get(first_uri(final, text), agent)
        text = body.decode("utf-8", "replace").lstrip()
        if not text.startswith("#EXTM3U"):
            return f"variant playlist broken (HTTP {status})"
    segment = first_uri(final, text)
    if not segment:
        return "playlist has no segments"
    status, _, _, body = get(segment, agent, limit=1024)
    return "OK" if status == 200 and body else f"segment HTTP {status}"


def describe(error: Exception) -> str:
    if isinstance(error, urllib.error.HTTPError):
        return f"HTTP {error.code}"
    reason = getattr(error, "reason", error)
    if isinstance(reason, socket.timeout) or "timed out" in str(reason):
        return "timed out"
    return str(reason)[:60]


def check(url: str) -> tuple[str, str]:
    results = []
    for agent in AGENTS:
        try:
            result = check_once(url, agent)
        except Exception as e:
            result = describe(e)
        if result.startswith("OK"):
            return url, result + ("" if agent == AGENTS[0] else ", only as VLC")
        results.append(result)
    return url, results[0]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("files", nargs="*", type=Path, default=[DATA / "custom" / "channels.json"])
    parser.add_argument("--id", action="append", dest="ids")
    args = parser.parse_args()

    channels = [c for path in args.files for c in json.loads(path.read_text(encoding="utf-8"))]
    if args.ids:
        channels = [c for c in channels if c["id"] in args.ids]
    urls = list(dict.fromkeys(u for c in channels for u in c.get("urls", [])))
    with ThreadPoolExecutor(12) as pool:
        status = dict(pool.map(check, urls))
    working = sum(s.startswith("OK") for s in status.values())
    for c in channels:
        visible = c.get("visible", True)
        print(f"{c.get('name', c['id'])} ({c['id']}){'' if visible else ', hidden'}")
        for url in c.get("urls", []):
            print(f"  {status[url]:40} {url}")
    print(f"\n{working} of {len(urls)} URLs work")


if __name__ == "__main__":
    main()
