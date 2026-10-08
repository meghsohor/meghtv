"""Loads, checks and saves the channel data in data/."""

from __future__ import annotations

import json
import re
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / "data"
IPTV_DIR = DATA / "iptv"

CHANNEL_KEYS = ["id", "name", "country", "categories", "visible", "urls"]
CUSTOM_AMEND_KEYS = {"id", "name", "country", "categories", "visible", "urls"}
URL_SCHEME = re.compile(r"^[A-Za-z][A-Za-z0-9+.-]*://")


@dataclass
class ChannelData:
    categories: list[dict]
    countries: list[dict]
    channels: list[dict]  # the iptv-org copy, in file order
    custom_channels: list[dict]
    custom_categories: list[dict]
    # Channel id -> the file it was read from, to check each sits in its country's file.
    sources: dict[str, Path] = field(default_factory=dict)


def read_json(path: Path, default=None):
    if not path.exists():
        if default is None:
            raise FileNotFoundError(path)
        return default
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path: Path, value) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def iptv_file(country: str) -> Path:
    return IPTV_DIR / f"{country.lower()}.json"


def load() -> ChannelData:
    channels: list[dict] = []
    sources: dict[str, Path] = {}
    for path in sorted(IPTV_DIR.glob("*.json")):
        for channel in read_json(path):
            channels.append(channel)
            sources.setdefault(channel.get("id"), path)
    return ChannelData(
        categories=read_json(DATA / "categories.json", []),
        countries=read_json(DATA / "countries.json", []),
        channels=channels,
        custom_channels=read_json(DATA / "custom" / "channels.json", []),
        custom_categories=read_json(DATA / "custom" / "categories.json", []),
        sources=sources,
    )


def ordered_channel(channel: dict) -> dict:
    return {key: channel[key] for key in CHANNEL_KEYS if key in channel}


def save_iptv(data: ChannelData) -> None:
    """Writes categories, countries and the iptv-org copy. The custom files are never touched."""
    write_json(DATA / "categories.json", data.categories)
    write_json(DATA / "countries.json", data.countries)
    by_file: dict[Path, list[dict]] = {}
    for channel in data.channels:
        by_file.setdefault(iptv_file(channel["country"]), []).append(ordered_channel(channel))
    for path in IPTV_DIR.glob("*.json"):
        if path not in by_file:
            path.unlink()
    for path, channels in by_file.items():
        write_json(path, channels)


def _is_text(value) -> bool:
    return isinstance(value, str) and value.strip() != ""


def _check_urls(where: str, urls, errors: list[str], allow_empty: bool = False) -> None:
    if not isinstance(urls, list) or (not urls and not allow_empty):
        errors.append(f"{where}: urls must be a non-empty list")
        return
    for url in urls:
        if not isinstance(url, str) or not URL_SCHEME.match(url):
            errors.append(f"{where}: not a stream URL: {url!r}")
    if len(set(map(str, urls))) != len(urls):
        errors.append(f"{where}: duplicate URL")


def _check_categories(where: str, value, known: set[str], errors: list[str]) -> None:
    if not isinstance(value, list) or not all(isinstance(c, str) for c in value):
        errors.append(f"{where}: categories must be a list of category ids")
        return
    for category in value:
        if category not in known:
            errors.append(f"{where}: unknown category {category!r}")


def exclusive_categories(data: ChannelData) -> set[str]:
    """Categories whose channels show only there: no country, not in All Channels or Search."""
    return {
        c["id"]
        for rows in (data.categories, data.custom_categories)
        if isinstance(rows, list)
        for c in rows
        if isinstance(c, dict) and c.get("exclusive") is True and "id" in c
    }


def validate(data: ChannelData) -> list[str]:
    errors: list[str] = []

    def check_menu(name: str, rows, key: str, extra: list[str], optional: tuple[str, ...] = ()) -> set[str]:
        ids: set[str] = set()
        if not isinstance(rows, list):
            errors.append(f"{name}: must be a list")
            return ids
        for row in rows:
            expected = {key, "name", "visible", *extra}
            if not isinstance(row, dict) or not expected <= set(row) <= expected | set(optional):
                errors.append(f"{name}: each entry needs {sorted(expected)}, optionally {sorted(optional)}: {row!r}")
                continue
            if not _is_text(row[key]) or not _is_text(row["name"]) or not isinstance(row["visible"], bool):
                errors.append(f"{name}: bad entry {row!r}")
            if "exclusive" in row and not isinstance(row["exclusive"], bool):
                errors.append(f"{name}: exclusive must be true or false: {row!r}")
            if row[key] in ids:
                errors.append(f"{name}: duplicate {key} {row[key]!r}")
            ids.add(row[key])
        return ids

    category_ids = check_menu("categories.json", data.categories, "id", [], ("exclusive",))
    custom_category_ids = check_menu("custom/categories.json", data.custom_categories, "id", [], ("exclusive",))
    for clash in sorted(category_ids & custom_category_ids):
        errors.append(f"custom/categories.json: {clash!r} is already an iptv-org category")
    all_categories = category_ids | custom_category_ids
    exclusive = exclusive_categories(data)
    country_codes = check_menu("countries.json", data.countries, "code", ["flag"])

    iptv_ids: set[str] = set()
    for channel in data.channels:
        where = f"iptv {channel.get('id')!r}" if isinstance(channel, dict) else "iptv"
        if not isinstance(channel, dict):
            errors.append(f"{where}: not an object")
            continue
        missing = set(CHANNEL_KEYS) - set(channel)
        unknown = set(channel) - set(CHANNEL_KEYS)
        if missing or unknown:
            errors.append(f"{where}: missing {sorted(missing)}, unknown {sorted(unknown)}")
            continue
        if not _is_text(channel["id"]) or any(ch.isspace() for ch in channel["id"]):
            errors.append(f"{where}: bad id")
        if channel["id"] in iptv_ids:
            errors.append(f"{where}: duplicate id")
        iptv_ids.add(channel["id"])
        if not _is_text(channel["name"]):
            errors.append(f"{where}: empty name")
        if channel["country"] not in country_codes:
            errors.append(f"{where}: unknown country {channel['country']!r}")
        elif data.sources.get(channel["id"], iptv_file(channel["country"])) != iptv_file(channel["country"]):
            errors.append(f"{where}: belongs in {iptv_file(channel['country']).name}")
        _check_categories(where, channel["categories"], all_categories, errors)
        if not isinstance(channel["visible"], bool):
            errors.append(f"{where}: visible must be true or false")
        _check_urls(where, channel["urls"], errors)

    iptv_categories = {c["id"]: c["categories"] for c in data.channels if isinstance(c, dict) and "id" in c and "categories" in c}
    custom_ids: set[str] = set()
    if not isinstance(data.custom_channels, list):
        errors.append("custom/channels.json: must be a list")
        return errors
    for entry in data.custom_channels:
        where = f"custom {entry.get('id')!r}" if isinstance(entry, dict) else "custom"
        if not isinstance(entry, dict) or not _is_text(entry.get("id")):
            errors.append(f"{where}: each entry needs an id")
            continue
        if entry["id"] in custom_ids:
            errors.append(f"{where}: duplicate id")
        custom_ids.add(entry["id"])
        unknown = set(entry) - CUSTOM_AMEND_KEYS
        if unknown:
            errors.append(f"{where}: unknown fields {sorted(unknown)}")
        if entry["id"] not in iptv_ids:
            missing = {"name", "country", "categories", "urls"} - set(entry)
            if missing:
                errors.append(f"{where}: a new channel needs {sorted(missing)}")
        elif len(entry) == 1:
            errors.append(f"{where}: changes nothing")
        if "name" in entry and not _is_text(entry["name"]):
            errors.append(f"{where}: empty name")
        if entry.get("country") == "":
            categories = entry.get("categories") or iptv_categories.get(entry["id"]) or []
            if not isinstance(categories, list) or not any(c in exclusive for c in categories):
                errors.append(f"{where}: only a channel in an exclusive category can have no country")
        elif "country" in entry and entry["country"] not in country_codes:
            errors.append(f"{where}: unknown country {entry['country']!r}")
        if "categories" in entry:
            _check_categories(where, entry["categories"], all_categories, errors)
        if "visible" in entry and not isinstance(entry["visible"], bool):
            errors.append(f"{where}: visible must be true or false")
        if "urls" in entry:
            _check_urls(where, entry["urls"], errors, allow_empty=entry["id"] in iptv_ids)
    return errors
