import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import channel_data
from build_channels import build, empty_parts
from channel_data import ChannelData, validate
from sync_iptv import Upstream, check_upstream, merge, merge_menu, parse_m3u, screen


def channel(id, urls, visible=True, **extra):
    return {"id": id, "name": id, "country": "FR", "categories": ["news"], "visible": visible, **extra, "urls": urls}


def data(channels, custom=(), custom_categories=()):
    return ChannelData(
        categories=[{"id": "news", "name": "News", "visible": True}, {"id": "music", "name": "Music", "visible": True}],
        countries=[{"code": "FR", "name": "France", "flag": "", "visible": True}],
        channels=list(channels),
        custom_channels=list(custom),
        custom_categories=list(custom_categories),
    )


def upstream(id, urls, name=None):
    return Upstream(id=id, name=name or id, country="FR", categories=["news"], urls=urls)


class SyncTest(unittest.TestCase):
    def test_new_urls_go_first_and_old_ones_stay(self):
        d = data([channel("a@x", ["http://old1", "http://old2"])])
        merge(d, [upstream("a@x", ["http://new", "http://old2"])])
        self.assertEqual(d.channels[0]["urls"], ["http://new", "http://old2", "http://old1"])

    def test_channel_gone_upstream_is_hidden_not_removed_and_returns(self):
        d = data([channel("a@x", ["http://a"]), channel("b@x", ["http://b"])])
        merge(d, [upstream("a@x", ["http://a"])])
        self.assertEqual(d.channels[1], channel("b@x", ["http://b"], visible=False, hiddenBy="sync"))
        merge(d, [upstream("a@x", ["http://a"]), upstream("b@x", ["http://b"])])
        self.assertEqual(d.channels[1], channel("b@x", ["http://b"]))

    def test_sync_never_shows_a_channel_hidden_by_hand(self):
        d = data([channel("a@x", ["http://a"], visible=False)])
        merge(d, [upstream("a@x", ["http://a"], name="Renamed")])
        self.assertFalse(d.channels[0]["visible"])
        self.assertEqual(d.channels[0]["name"], "Renamed")
        merge(d, [])
        self.assertNotIn("hiddenBy", d.channels[0])

    def test_new_channel_is_added_visible(self):
        d = data([])
        changes = merge(d, [upstream("a@x", ["http://a"])])
        self.assertEqual(d.channels, [channel("a@x", ["http://a"])])
        self.assertEqual(len(changes["added"]), 1)

    def test_guard_stops_when_ids_are_renamed_upstream(self):
        d = data([channel(f"c{i}@x", ["http://a"]) for i in range(10)])
        renamed = {f"c{i}@y" for i in range(10)}
        with self.assertRaises(SystemExit):
            check_upstream(d, renamed)
        check_upstream(d, {f"c{i}@x" for i in range(6)})  # 4 of 10 gone: allowed

    def test_screen_fixes_or_sets_aside_bad_upstream_entries(self):
        d = data([channel("bad@x", ["http://old"])])
        ups = [
            Upstream("ok@x", "", "FR", ["news", "nope"], ["http://a", "noscheme"]),
            Upstream("bad@x", "Bad", "ZZ", ["news"], ["http://b"]),
        ]
        usable, set_aside, report = screen(ups, d)
        self.assertEqual(usable, [Upstream("ok@x", "ok", "FR", ["news"], ["http://a"])])
        self.assertEqual(set_aside, {"bad@x"})
        self.assertEqual(len(report), 3)
        merge(d, usable, set_aside)
        self.assertTrue(d.channels[0]["visible"])  # set aside, not hidden
        self.assertEqual(validate(d), [])

    def test_upstream_category_never_replaces_a_custom_one(self):
        d = data([], custom_categories=[{"id": "local", "name": "Mine", "visible": True}])
        report = []
        merge_menu(d.categories, [{"id": "local", "name": "Theirs"}], "id", report, "category", reserved={"local"})
        self.assertNotIn("local", [c["id"] for c in d.categories])
        self.assertEqual(validate(d), [])

    def test_country_change_moves_the_channel_to_its_new_file(self):
        with tempfile.TemporaryDirectory() as tmp, patch.object(channel_data, "DATA", Path(tmp)), patch.object(
            channel_data, "IPTV_DIR", Path(tmp) / "iptv"
        ):
            d = data([channel("a@x", ["http://a"])])
            d.countries.append({"code": "DE", "name": "Germany", "flag": "", "visible": True})
            channel_data.save_iptv(d)
            d = channel_data.load()
            merge(d, [Upstream("a@x", "a@x", "DE", ["news"], ["http://a"])])
            self.assertEqual(validate(d), [])
            channel_data.save_iptv(d)
            self.assertEqual(sorted(p.name for p in (Path(tmp) / "iptv").iterdir()), ["de.json"])
            self.assertEqual(validate(channel_data.load()), [])

    def test_parse_m3u_matches_the_app(self):
        text = '#EXTM3U\r\n#EXTINF:-1 tvg-id="a@x",A, the channel\n#EXTVLCOPT:x\nhttp://a\n#EXTINF:-1 tvg-id="",B\nhttp://b\n'
        self.assertEqual(parse_m3u(text), [("a@x", "the channel", "http://a")])


class BuildTest(unittest.TestCase):
    def test_custom_amends_and_adds(self):
        d = data(
            [channel("a@x", ["http://a"])],
            custom=[
                {"id": "a@x", "name": "Better", "urls": ["http://mine", "http://a"]},
                {"id": "mine@x", "name": "Mine", "country": "FR", "categories": ["local"], "urls": ["http://m"]},
            ],
            custom_categories=[{"id": "local", "name": "Local", "visible": True}],
        )
        self.assertEqual(validate(d), [])
        out = build(d)
        self.assertEqual(out["channels"][0]["name"], "Better")
        self.assertEqual(out["channels"][0]["urls"], ["http://mine", "http://a"])
        self.assertEqual(out["channels"][1]["id"], "mine@x")
        self.assertEqual([c["id"] for c in out["categories"]], ["news", "local"])

    def test_hidden_entries_are_left_out(self):
        d = data([channel("a@x", ["http://a"], visible=False), channel("b@x", ["http://b"], categories=["news", "music"])])
        d.categories[1]["visible"] = False
        out = build(d)
        self.assertEqual([c["id"] for c in out["channels"]], ["b@x"])
        # A hidden category leaves the menu, not the channel.
        self.assertEqual([c["id"] for c in out["categories"]], ["news"])
        self.assertEqual(out["channels"][0]["categories"], ["news", "music"])

    def test_custom_can_hide_an_iptv_channel(self):
        d = data([channel("a@x", ["http://a"])], custom=[{"id": "a@x", "visible": False}])
        self.assertEqual(build(d)["channels"], [])

    def test_an_all_hidden_list_is_never_published(self):
        d = data([channel("a@x", ["http://a"], visible=False)])
        self.assertEqual(empty_parts(build(d)), ["channels", "categories", "countries"])

    def test_validate_catches_mistakes(self):
        d = data(
            [channel("a@x", ["notaurl"]), channel("a@x", ["http://a"], categories=["nope"])],
            custom=[{"id": "new@x", "name": "New"}],
        )
        errors = "\n".join(validate(d))
        for expected in ["not a stream URL", "duplicate id", "unknown category", "a new channel needs"]:
            self.assertIn(expected, errors)


if __name__ == "__main__":
    unittest.main()
