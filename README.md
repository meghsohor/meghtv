# MeghTV channel list

This branch holds the channel list that MeghTV downloads. It shares no history with `main` and is never merged into it.

- `data/iptv/<country>.json`: a copy of [iptv-org](https://github.com/iptv-org/iptv), one file per country.
- `data/categories.json`, `data/countries.json`: the menus.
- `data/gone.json`: channels missing from iptv-org, with the day each went missing. The sync keeps it.
- `data/custom/channels.json`, `data/custom/categories.json`: channels and categories added by hand.

Every push to this branch publishes the list to GitHub Pages:
- `https://meghsohor.github.io/meghtv/manifest.json`: `updatedAt`, plus the checksum and size of the list. The app checks this.
- `https://meghsohor.github.io/meghtv/channels.json`: the list itself. Hidden entries and empty menus are left out, and the custom list is applied on top.

## Channel entry

```json
{
  "id": "France24.fr@English",
  "name": "France 24 English (1080p)",
  "country": "FR",
  "categories": ["news"],
  "visible": true,
  "urls": ["https://…", "https://…"]
}
```

- `id` is iptv-org's `channelId@feedId`. Never change it: the app keys favourites on it.
- The player tries `urls` in order.

## Hiding

- **Channel:** set `"visible": false`. It disappears from the app.
- **Category or country:** set `"visible": false` in `categories.json` or `countries.json`. Only the menu entry disappears; its channels still show in their other categories, All Channels and Search.

Only you hide channels: the sync never does. Edit a channel in its country's file.

## Custom channels

`data/custom/channels.json` is a list of entries:
- **New channel:** use an ID not in `data/iptv/`, and give `name`, `country`, `categories` and `urls`. `visible` defaults to true. Any unique ID without spaces works, for example `MyChannel.bd@HD`.
- **Change an iptv-org channel:** use its ID and only the fields to change. Its `urls` are tried before iptv-org's. `name`, `country`, `categories` and `visible` replace iptv-org's.

A new category goes in `data/custom/categories.json` as `{"id", "name", "visible"}`.

### Exclusive categories

A category with `"exclusive": true` (like `xyz`) keeps its channels to itself. They show only in that category: not under any country, and not in All Channels or Search. A user's Favourites still show them.
- A channel in an exclusive category can set `"country": ""`. Only such channels may have an empty country.
- If a channel is also in other categories, the published list keeps only its exclusive ones and empties its country.

```json
{"id": "Example.xyz@HD", "name": "Example", "country": "", "categories": ["xyz"], "urls": ["https://…"]}
```

## Daily sync

`sync-iptv.yml` on `main` runs every day. It merges iptv-org into `data/`, runs the checks, commits to this branch and publishes the list. The run's summary lists what changed.

- New channels, categories and countries are added, visible.
- For an existing channel:
  - iptv-org's current URLs go first, and older URLs stay after them;
  - its name, country and categories follow iptv-org;
  - a channel you hid stays hidden.
- A channel still missing from iptv-org 7 days after a sync first found it missing is removed (it's back to day 0 whenever a sync finds it there), and the app drops it, with any favourite of it, on its next update. A hidden channel, or one `data/custom/channels.json` changes, is kept instead, so a hidden one stays hidden if it comes back.
- If under half of the channels we track are still in iptv-org (a broken upstream, or renamed ids), the run stops.
- An upstream entry our checks would reject is fixed or skipped, and listed in the summary:
  - URLs without a scheme are dropped;
  - unknown categories are dropped from the channel;
  - a channel with a space in its id, an unknown country or no usable URL is skipped, and left as it is if we already have it.
- An iptv-org category with the same id as one in `data/custom/categories.json` is ignored; yours is kept.

If you push to this branch while a sync runs, the sync's push is refused and the next day's run picks up both.

## Checks

```sh
python3 -m unittest discover -s scripts   # script tests
python3 scripts/build_channels.py --check # validate data/
python3 scripts/build_channels.py --out site  # build the published files locally
python3 scripts/check_urls.py                 # test the custom channels' URLs (playlist, variant, first segment)
python3 scripts/check_urls.py data/iptv/fr.json --id France24.fr@English  # any file, any channels
```

Run `check_urls.py` from a network without a firewall: an office network can refuse or reset connections to streaming servers.

Your PRs to this branch run both checks. The sync runs them itself before committing.

A publish refuses:
- invalid data;
- a list with no visible channels, categories or countries;
- a list with under half the channels of the live one, unless you run "Publish channels" by hand with `force`.
