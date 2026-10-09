# Tempora

A Forge (Minecraft 1.7.10, GTNH ecosystem) server-side mod for tracking griefing and other
player activity. It's a slimmed-down alternative to Prism/CoreProtect/LogBlock: block changes
(inside the regions you choose), player movement, inventory transfers, entity deaths/spawns,
explosions, item use, and commands are logged to a local SQLite database and can be queried
in-game by operators.

## Features

- **Per-event-type logging** — block changes, player movement, entity spawns/deaths,
  explosions, inventory transfers, item use, and command history, each in its own SQLite database file.
- **In-game querying** — look up what happened around you (`/tempora lookup`), click blocks with
  the Tempora wand, or run a read-only SQL query directly against a logger's table (`/tempora sql`).
- **Regions** — turn individual loggers on or off in specific areas of the world, e.g. log every
  block change around spawn, or stop logging mob deaths in a mob farm. See [Regions](#regions).
- **Configurable retention and durability** — per-logger data retention window, and a choice
  between safer (`NORMAL`) or faster-but-riskier (`HIGH_RISK`) write durability.
- **Schema evolution support** — the database schema is derived from annotated Java fields and
  updates itself on startup (new columns, and explicit renames via `@Column(renamedFrom = ...)`)
  without handwritten migrations.

## Commands

Everything is under one command, `/tempora` (or `/tem` for short), which lists the subcommands when
run on its own. All of them require operator permission (permission level 2), and
`/tempora help <command>` explains any of them.

| Command | Purpose |
|---|---|
| `/tempora lookup [r:<radius>] [t:<time>] [l:<logger,...>]` | Show the latest events around you. Every filter is optional and they go in any order, e.g. `/tempora lookup t:1d l:break,place`. Defaults to 10 blocks, the last 3 days, every logger. |
| `/tempora home <player> [t:<time>] [dim:<id>]` | Find a player's most-visited chunk, based on movement history. |
| `/tempora sql <SELECT ...>` | Run a read-only `SELECT` against a logger's table for ad-hoc investigation. |
| `/tempora wand` | Get the Tempora wand. Left click a block to see its history, right click a face for the space in front of it. |
| `/tempora region create <name> <x1 y1 z1> <x2 y2 z2> [dim]` | Create a region. Coordinates can be relative to you, e.g. `~-50`. |
| `/tempora region list [dim]` / `/tempora region remove [name]` | List or delete regions. |
| `/tempora region set <name> <logger\|all> <on\|off\|clear>` | Turn a logger on or off inside a region. |
| `/tempora region check [logger]` | Show which loggers record events where you stand, and why. |
| `/tempora help [command]` | List every command, or explain one, e.g. `/tempora help region set`. |

Loggers have short names for commands: `block`, `break`, `place`, `explosion`, `command`, `death`,
`spawn`, `entity`, `inventory`, `item` and `movement`. The full names (e.g. `PlayerBlockBreakLogger`)
also work, and are the table names for `/tempora sql`.

`/tempora tp` and `/tempora trace` also exist, but are run by clicking Tempora's chat output rather
than typed. `/tempora explode <strength>`, for testing explosion logging, only exists in a development
environment.

## Regions

A region is a named box of blocks, created with `/tempora region create <name> <x1> <y1> <z1> <x2> <y2> <z2> [dim]`
(both corners included). On its own a region changes nothing. Give it rules with
`/tempora region set <name> <logger|all> <on|off|clear>`.

To decide whether a logger records an event at a position:

1. The **smallest** region containing the position that has a rule for that logger decides.
   So a small region nested inside a larger one overrides it.
2. If equally sized regions disagree, **ON wins**, since a missing record is worse than an extra one.
3. If no region has a rule for the logger, its `logByDefault` config option applies.

`BlockChangeLogger` (`block`) logs every `setBlock` call, which is far too much data to record
everywhere, so it defaults to `logByDefault=false` and only logs inside regions that turn it on.
Every other logger defaults to logging everywhere. `/tempora region check` shows the result at your
position and which region (or config option) decided it.

## Configuration

Each logger has its own config section (in the standard Forge config file) with:

- `isEnabled` — turns the logger off entirely, regardless of regions.
- `logByDefault` — whether the logger records events where no region has a rule for it.
- `logWriteSafety` — `NORMAL` (fsync every batch, safe) or `HIGH_RISK` (faster, but the last few
  seconds of *logs* — not world data — may be lost on a crash).
- `oldestDataCutoff` — how long to retain logged events before they're deleted (e.g. `4months`).
- A maximum in-memory queue size per logger, to bound memory use if the database falls behind.

## Data collected

For server operators: Tempora records player UUIDs, movement history, block changes (with
container contents at the time of breaking, for some loggers), inventory transfers, and full
command text/arguments, retained for the configured `oldestDataCutoff` window. Any operator can
query this data via `/tempora sql` or `/tempora lookup`.

## License

MIT — see [LICENSE](src/main/resources/LICENSE).
