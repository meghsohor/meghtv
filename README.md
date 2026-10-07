# MeghTV channel list

This branch holds the channel list that MeghTV downloads. It shares no history with `main` and is never merged into it.

- `data/iptv/<country>.json`: a copy of [iptv-org](https://github.com/iptv-org/iptv), one file per country.
- `data/categories.json`, `data/countries.json`: the menus.
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
- `"hiddenBy": "sync"` marks a channel the sync hid because it left iptv-org.

## Hiding

- **Channel:** set `"visible": false`. It disappears from the app.
- **Category or country:** set `"visible": false` in `categories.json` or `countries.json`. Only the menu entry disappears; its channels still show in their other categories, All Channels and Search.

Nothing is ever deleted. Edit a channel in its country's file.

A channel the sync hid (`"hiddenBy": "sync"`) is the sync's to manage, and it's shown again if it returns to iptv-org:
- **To show it anyway:** add `{"id": "<its id>", "visible": true}` to `data/custom/channels.json`. Setting `visible` in its iptv file doesn't last, because the next sync hides it again.
- **To keep it hidden even if it returns:** delete its `hiddenBy` line. It then counts as hidden by you.

## Custom channels

`data/custom/channels.json` is a list of entries:
- **New channel:** use an ID not in `data/iptv/`, and give `name`, `country`, `categories` and `urls`. `visible` defaults to true. Any unique ID without spaces works, for example `MyChannel.bd@HD`.
- **Change an iptv-org channel:** use its ID and only the fields to change. Its `urls` are tried before iptv-org's. `name`, `country`, `categories` and `visible` replace iptv-org's.

A new category goes in `data/custom/categories.json` as `{"id", "name", "visible"}`.

## Daily sync

`sync-iptv.yml` on `main` runs every day. It merges iptv-org into `data/` and opens a PR against this branch, or updates the one already open. Nothing is published until that PR is merged.

- New channels, categories and countries are added, visible.
- For an existing channel:
  - iptv-org's current URLs go first, and older URLs stay after them;
  - its name, country and categories follow iptv-org;
  - a channel you hid stays hidden.
- A channel gone from iptv-org is hidden with `hiddenBy: "sync"`, and shown again if it returns.
- If under half of the channels we track are still in iptv-org (a broken upstream, or renamed ids), the run stops instead of hiding them.
- An upstream entry our checks would reject is fixed or skipped, and listed in the PR:
  - URLs without a scheme are dropped;
  - unknown categories are dropped from the channel;
  - a channel with a space in its id, an unknown country or no usable URL is skipped, and left as it is if we already have it.
- An iptv-org category with the same id as one in `data/custom/categories.json` is ignored; yours is kept.
- If a newer run finds nothing to change, it closes the open PR, because merging it would publish out-of-date changes.

Don't edit the `sync/iptv` branch: the next run replaces it. Make changes on this branch after merging.

## Checks

```sh
python3 -m unittest discover -s scripts   # script tests
python3 scripts/build_channels.py --check # validate data/
python3 scripts/build_channels.py --out site  # build the published files locally
```

Your PRs to this branch run both checks. The sync PR runs none, because GitHub starts no workflows for a PR opened by a workflow; the sync job runs the checks itself before opening it.

A publish refuses:
- invalid data;
- a list with no visible channels, categories or countries;
- a list with under half the channels of the live one, unless you run "Publish channels" by hand with `force`.
