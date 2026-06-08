# Distribution & Marketplace Listings

Copy-paste-ready store descriptions and the automated publishing pipeline for
getting LavaRise in front of server admins.

## Files

| File | For | Format |
|---|---|---|
| [`modrinth.md`](modrinth.md) | **Modrinth** & **Hangar** project descriptions | Markdown |
| [`spigot.bbcode`](spigot.bbcode) | **SpigotMC** resource page | BBCode |

Both are derived from the main [README](../../README.md) — keep them in sync when
features change.

## Publishing channels

LavaRise is a Paper plugin, so the audience lives on three storefronts:

- **Modrinth** — modern, API-first, automated publishing. *Recommended primary.*
- **Hangar** (PaperMC's official platform) — automated publishing.
- **SpigotMC** — largest audience, but uploads are **manual** (no public upload API);
  paste `spigot.bbcode` into the resource description by hand.

## Automated publishing (Modrinth + Hangar)

The **`publish-jar` job in [`.github/workflows/release-please.yml`](../../.github/workflows/release-please.yml)**
builds the jar, attaches it to the GitHub Release, and pushes it to Modrinth and
Hangar **every time release-please cuts a release** — all in one run. (It lives
there, rather than in a separate `on: release` workflow, because a release cut by
`GITHUB_TOKEN` can't trigger downstream `on: release` / `on: push: tags` workflows.)

[`.github/workflows/publish.yml`](../../.github/workflows/publish.yml) is a
**manual** (`workflow_dispatch`) fallback — use it to re-push the current build to
the marketplaces out-of-band, e.g. right after first adding the tokens.

### One-time setup

1. **Create the projects** on [Modrinth](https://modrinth.com/) and
   [Hangar](https://hangar.papermc.io/). Use `modrinth.md` for the description.
2. **Generate tokens:**
   - Modrinth → Settings → PATs → a token with the **Create versions** scope.
   - Hangar → Account → API Keys → a key with the **create_version** permission.
3. **Add repository secrets** (Settings → Secrets and variables → Actions):
   - `MODRINTH_TOKEN`
   - `HANGAR_TOKEN`
4. **Set the project IDs** in both `release-please.yml` (the `publish-jar` job) and
   `publish.yml`:
   - `modrinth-id:` your Modrinth project slug (e.g. `lavarise`).
   - `hangar-id:` your Hangar `owner/Project` slug (e.g. `DeWost/LavaRise`).

If a token is missing the workflow **warns and skips** that platform — so you can
enable Modrinth first and add Hangar later. You can also re-publish manually via
**Actions → Publish to Marketplaces (manual) → Run workflow**.

## Release checklist

- [ ] `release-please` PR merged → GitHub Release + `v*` tag created.
- [ ] `release-please.yml`'s `publish-jar` job attached the jar **and** pushed to
      Modrinth / Hangar (check the Actions run).
- [ ] SpigotMC resource updated manually from `spigot.bbcode` + new jar.
