# Tempora

A Forge (Minecraft 1.7.10, GTNH ecosystem) server-side mod for tracking griefing and other
player activity. It's a slimmed-down alternative to Prism/CoreProtect/LogBlock: every block
change, player movement, inventory transfer, entity death/spawn, explosion, item use, and
command is logged to a local SQLite database and can be queried in-game by operators.

## Features

- **Per-event-type logging** — block changes, player movement, entity spawns/deaths,
  explosions, inventory transfers, item use, and command history, each in its own SQLite database file.
- **In-game querying** — look up what happened at a location or time (`/tempora_query_events`),
  or run a read-only SQL query directly against a logger's table (`/tempora_query_sql`).
- **Region whitelisting/blacklisting** — scope which loggers are active in specific regions of
  the world.
- **Configurable retention and durability** — per-logger data retention window, and a choice
  between safer (`NORMAL`) or faster-but-riskier (`HIGH_RISK`) write durability.
- **Schema evolution support** — the database schema is derived from annotated Java fields and
  updates itself on startup (new columns, and explicit renames via `@Column(renamedFrom = ...)`)
  without handwritten migrations.

## Commands

All commands below require operator permission (permission level 2).

| Command | Purpose |
|---|---|
| `/tempora_query_events` | Find logged events near a position and time. |
| `/tempora_query_sql` | Run a read-only `SELECT` against a logger's table for ad-hoc investigation. |
| `/tempora_home_chunk` | Find a player's most-visited chunk, based on movement history. |
| `/tempora_create_region` / `/tempora_list_regions` / `/tempora_remove_regions` | Manage logging regions. |
| `/tempora_region_filter` | Whitelist/blacklist specific loggers within a region. |
| `/tempora_tp` | Teleport to a logged event's location. |
| `/tempora_explode` | Trigger a test explosion (for verifying explosion logging). |
| `/tempora_stacktrace` | Inspect the stack trace recorded against a logged event. |
| `/tempora_help` | List all Tempora commands and their usage. |

## Configuration

Each logger has its own config section (in the standard Forge config file) with:

- `logWriteSafety` — `NORMAL` (fsync every batch, safe) or `HIGH_RISK` (faster, but the last few
  seconds of *logs* — not world data — may be lost on a crash).
- `oldestDataCutoff` — how long to retain logged events before they're deleted (e.g. `4months`).
- A maximum in-memory queue size per logger, to bound memory use if the database falls behind.

## Data collected

For server operators: Tempora records player UUIDs, movement history, block changes (with
container contents at the time of breaking, for some loggers), inventory transfers, and full
command text/arguments, retained for the configured `oldestDataCutoff` window. Any operator can
query this data via `/tempora_query_sql` or `/tempora_query_events`.

## License

MIT — see [LICENSE](src/main/resources/LICENSE).
