"""Merges iptv-org into data/. Never removes a channel or URL; see README.md for the rules.

Usage: sync_iptv.py --iptv <iptv-org/iptv clone> --database <iptv-org/database clone> [--summary FILE]
"""

from __future__ import annotations

import argparse
import csv
import re
import sys
from dataclasses import dataclass
from pathlib import Path

from channel_data import URL_SCHEME, ChannelData, load, save_iptv, validate

PLAYLIST_NAME = re.compile(r"^[a-z0-9_]+\.m3u$")
TVG_ID = re.compile(r'tvg-id="([^"]*)"')
LINE_BREAK = re.compile(r"\r\n|\r|\n")
# Below this share of our tracked channels still upstream, upstream is more likely broken than shrunk.
MIN_UPSTREAM_SHARE = 0.5


@dataclass
class Upstream:
    id: str
    name: str
    country: str
    categories: list[str]
    urls: list[str]


def read_csv(path: Path) -> list[dict]:
    with path.open(newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f, restval=""))


def parse_m3u(text: str) -> list[tuple[str, str, str]]:
    """(tvg-id, title, url) per entry, skipping entries without a tvg-id. Same rules as the app's M3uParser."""
    entries = []
    tvg_id = title = None
    for raw in LINE_BREAK.split(text):
        line = raw.strip()
        if not line:
            continue
        if line.startswith("#EXTINF:"):
            match = TVG_ID.search(line)
            tvg_id = (match.group(1) if match else "") or None
            title = line.rsplit(",", 1)[1].strip() if "," in line else ""
        elif not line.startswith("#"):
            if tvg_id is not None:
                entries.append((tvg_id, title or "", line))
            tvg_id = title = None
    return entries


def dedupe(urls: list[str]) -> list[str]:
    return list(dict.fromkeys(urls))


def read_upstream(iptv: Path, database: Path) -> tuple[list[Upstream], list[dict], list[dict]]:
    rows = {row["id"]: row for row in read_csv(database / "data" / "channels.csv")}
    entries: dict[str, list[tuple[str, str]]] = {}
    playlists = sorted(p for p in (iptv / "streams").iterdir() if PLAYLIST_NAME.match(p.name))
    if not playlists:
        sys.exit(f"no playlists in {iptv / 'streams'}")
    for path in playlists:
        for tvg_id, title, url in parse_m3u(path.read_text(encoding="utf-8")):
            entries.setdefault(tvg_id, []).append((title, url))

    channels = []
    for tvg_id, items in entries.items():
        row = rows.get(tvg_id.split("@", 1)[0])
        if row is None:
            continue
        channels.append(
            Upstream(
                id=tvg_id,
                name=items[0][0] or row["name"],
                country=row["country"],
                categories=[c for c in row["categories"].split(";") if c],
                urls=dedupe([url for _, url in items]),
            )
        )
    categories = [{"id": r["id"], "name": r["name"]} for r in read_csv(database / "data" / "categories.csv")]
    countries = [{"code": r["code"], "name": r["name"], "flag": r["flag"]} for r in read_csv(database / "data" / "countries.csv")]
    return channels, categories, countries


def screen(upstream: list[Upstream], data: ChannelData) -> tuple[list[Upstream], set[str], list[str]]:
    """Fixes or sets aside upstream entries our checks would reject, so one bad value can't stop the sync.

    Returns the usable channels, the ids set aside (still upstream, for the guard) and a report.
    """
    countries = {c["code"] for c in data.countries}
    categories = {c["id"] for c in data.categories + data.custom_categories}
    usable, skipped, report = [], set(), []
    for up in upstream:
        urls = [u for u in up.urls if URL_SCHEME.match(u)]
        problem = None
        if any(ch.isspace() for ch in up.id):
            problem = "space in the id"
        elif up.country not in countries:
            problem = f"unknown country {up.country!r}"
        elif not urls:
            problem = "no usable URL"
        if problem:
            skipped.add(up.id)
            report.append(f"{up.id}: skipped, {problem}")
            continue
        if len(urls) < len(up.urls):
            report.append(f"{up.id}: dropped {len(up.urls) - len(urls)} URL(s) without a scheme")
        unknown = [c for c in up.categories if c not in categories]
        if unknown:
            report.append(f"{up.id}: dropped unknown categories {unknown}")
        name = up.name.strip() or up.id.split("@", 1)[0]
        usable.append(Upstream(up.id, name, up.country, [c for c in up.categories if c in categories], urls))
    return usable, skipped, report


def check_upstream(data: ChannelData, present: set[str]) -> None:
    """Stops when most channels we track are gone upstream: it's broken, or renamed its ids."""
    tracked = {c["id"] for c in data.channels}
    still_there = len(tracked & present)
    if tracked and still_there < len(tracked) * MIN_UPSTREAM_SHARE:
        sys.exit(f"only {still_there} of the {len(tracked)} channels we track are still in iptv-org: refusing to merge")


def merge_menu(rows: list[dict], upstream: list[dict], key: str, report: list[str], label: str, reserved: set[str] = frozenset()) -> None:
    by_key = {row[key]: row for row in rows}
    for item in upstream:
        if item[key] in reserved:
            report.append(f"iptv-org {label} {item[key]!r} has the same id as a custom one; the custom one is kept")
            continue
        row = by_key.get(item[key])
        if row is None:
            rows.append({**item, "visible": True})
            report.append(f"New {label}: {item['name']} ({item[key]})")
        else:
            row.update(item)  # name and flag follow upstream; visible is ours


def merge(data: ChannelData, upstream: list[Upstream]) -> dict[str, list[str]]:
    """Adds and updates; never hides. A channel gone from iptv-org stays as it is, and only the owner hides channels."""
    changes: dict[str, list[str]] = {k: [] for k in ("added", "urls", "details")}
    current = {c["id"]: c for c in data.channels}
    for up in upstream:
        channel = current.get(up.id)
        if channel is None:
            channel = {"id": up.id, "name": up.name, "country": up.country, "categories": up.categories, "visible": True, "urls": up.urls}
            data.channels.append(channel)
            current[up.id] = channel
            changes["added"].append(f"{up.name} ({up.id})")
            continue
        # Upstream's URLs first, in its order; older ones stay as fallbacks in case they come back.
        old = channel["urls"]
        merged = dedupe(up.urls + old)
        if merged != old:
            new_count = len(set(up.urls) - set(old))
            note = f"{new_count} new" if new_count else "reordered"
            changes["urls"].append(f"{up.name} ({up.id}): {note}")
            channel["urls"] = merged
        details = {"name": up.name, "country": up.country, "categories": up.categories}
        changed = [k for k, v in details.items() if channel[k] != v]
        if changed:
            changes["details"].append(f"{up.name} ({up.id}): {', '.join(changed)}")
            channel.update(details)
            data.sources.pop(up.id, None)  # a new country moves it to that country's file on save
    return changes


SECTIONS = [
    ("added", "New channels"),
    ("urls", "Channels with new or reordered URLs"),
    ("details", "Channels with a new name, country or categories"),
    ("menus", "Categories and countries"),
    ("screened", "Fixed or skipped upstream entries"),
]


def summary(changes: dict[str, list[str]], limit: int = 100) -> str:
    lines = ["Daily sync with iptv-org. Merging publishes the list to the app.", ""]
    for key, title in SECTIONS:
        items = changes.get(key, [])
        if not items:
            continue
        lines.append(f"**{title}: {len(items)}**")
        lines += [f"- {item}" for item in items[:limit]]
        if len(items) > limit:
            lines.append(f"- … and {len(items) - limit} more")
        lines.append("")
    return "\n".join(lines)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--iptv", type=Path, required=True)
    parser.add_argument("--database", type=Path, required=True)
    parser.add_argument("--summary", type=Path)
    args = parser.parse_args()

    data = load()
    upstream, categories, countries = read_upstream(args.iptv, args.database)

    menus: list[str] = []
    merge_menu(data.categories, categories, "id", menus, "category", reserved={c["id"] for c in data.custom_categories})
    merge_menu(data.countries, countries, "code", menus, "country")
    usable, set_aside, screened = screen(upstream, data)
    check_upstream(data, {up.id for up in usable} | set_aside)
    changes = merge(data, usable)
    changes["menus"] = menus
    changes["screened"] = screened

    errors = validate(data)
    if errors:
        sys.exit("invalid data after the merge:\n" + "\n".join(errors[:50]))
    save_iptv(data)
    for key, title in SECTIONS:
        print(f"{title}: {len(changes[key])}")
    if args.summary:
        args.summary.write_text(summary(changes), encoding="utf-8")


if __name__ == "__main__":
    main()
