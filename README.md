# Tempora

A Forge (Minecraft 1.7.10, GTNH ecosystem) server-side mod for tracking griefing and other
player activity. It's a slimmed-down alternative to Prism/CoreProtect/LogBlock: block changes
(inside the regions you choose), player positions, inventory transfers, entity deaths/spawns,
explosions, item use, and commands are logged to a local SQLite database and can be queried
in-game by operators. Server-wide events with no position, such as weather changes and TPS, are
logged too.

## Features

- **Per-event-type logging** — block changes, player positions, entity spawns/deaths,
  explosions, inventory transfers, item use, and command history, each in its own SQLite database file.
- **Server-wide logging** — weather changes and TPS, which happen everywhere rather than at a
  place. See [Logger kinds](#logger-kinds).
- **In-game querying** — look up what happened around you (`/tempora lookup`), click blocks with
  the Tempora wand, see the latest server-wide events (`/tempora global`), or run a read-only SQL
  query directly against a logger's table (`/tempora sql`).
- **Regions** — turn individual loggers on or off in specific areas of the world, e.g. log every
  block change around spawn, or stop logging mob deaths in a mob farm. See [Regions](#regions).
- **Configurable retention** — per-logger data retention window. Old events are deleted in the
  background while the server runs, a few thousand at a time, so it never holds up the server.
- **Schema evolution support** — the database schema is derived from annotated Java fields and
  updates itself on startup (new columns, and explicit renames via `@Column(renamedFrom = ...)`)
  without handwritten migrations.

## Commands

Everything is under one command, `/tempora` (or `/tem` for short), which lists the subcommands when
run on its own. All of them require operator permission (permission level 2), and
`/tempora help <command>` explains any of them.

| Command | Purpose |
|---|---|
| `/tempora lookup [r:<radius>] [t:<time>] [l:<logger,...>]` | Show the latest events around you. Every filter is optional and they go in any order, e.g. `/tempora lookup t:1d l:break,place`. Defaults to 10 blocks, the last 3 days, every positional logger. |
| `/tempora global [t:<time>] [l:<logger,...>] [n:<count>]` | Show the latest events from the global loggers (weather, TPS), from anywhere, including the server console. Defaults to the last 10 events of each over the last 3 days. |
| `/tempora home <player> [t:<time>] [dim:<id>]` | Find a player's most-visited chunk, based on their position history. |
| `/tempora sql <SELECT ...>` | Run a read-only `SELECT` against a logger's table for ad-hoc investigation. |
| `/tempora wand` | Get the Tempora wand. Left click a block to see its history, right click a face for the space in front of it. |
| `/tempora region create <name> <x1 y1 z1> <x2 y2 z2> [dim]` | Create a region. Coordinates can be relative to you, e.g. `~-50`. |
| `/tempora region list [dim]` / `/tempora region remove [name]` | List or delete regions. |
| `/tempora region set <name> <logger\|all> <on\|off\|clear>` | Turn a logger on or off inside a region. |
| `/tempora region check [logger]` | Show which loggers record events where you stand, and why. |
| `/tempora help [command]` | List every command, or explain one, e.g. `/tempora help region set`. |

Loggers have short names for commands: `change`, `break`, `place`, `explosion`, `command`, `death`,
`spawn`, `mobpos`, `inventory`, `use` and `playerpos`, plus the global `weather` and `tps`. The full
names (e.g. `PlayerBlockBreakLogger`) also work, and are the table names for `/tempora sql`.

## Logger kinds

Every logger is one of two kinds:

- **Positional** loggers record events that happen at a place: block changes, breaks and
  placements, explosions, deaths, inventory transfers and so on. Each event is stored with its
  dimension and coordinates, follows [region](#regions) rules, is looked up around a position
  (`/tempora lookup`, the wand) and can be drawn in the world.
- **Global** loggers record events that belong to the whole server, so they have no coordinates.
  Regions don't affect them, and `/tempora global` shows them wherever you are. Tempora has two:
  - `weather` records each dimension's weather changing between clear, rain and thunder, whatever
    changed it (the weather cycle, `/weather`, another mod). Dimensions without a sky are skipped.
  - `tps` records the server's TPS every `loggingIntervalSeconds` (default 60): the mean and
    slowest tick time since the last record, and the TPS that mean works out to, as `/forge tps`
    does.

`/tempora tp` and `/tempora trace` also exist, but are run by clicking Tempora's chat output rather
than typed. `/tempora explode <power>`, for testing explosion logging, only exists in a development
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

Regions only affect positional loggers. Global loggers, such as `weather`, have no position, so
they log everywhere whenever they are enabled.

`BlockChangeLogger` (`change`) logs every `setBlock` call, which is far too much data to record
everywhere, so it defaults to `logByDefault=false` and only logs inside regions that turn it on.
Every other logger defaults to logging everywhere. `/tempora region check` shows the result at your
position and which region (or config option) decided it.

## Configuration

Tempora's config is split across three files in `config/tempora/`:

- `loggers.cfg` — a section per logger (below), plus `shouldTemporaRun` to log in singleplayer.
  Loggers added by other mods get their sections here too.
- `commands.cfg` — the lookup time limit (`queryTimeLimitSeconds`) and how many events
  `/tempora sql` shows (`sqlMaxResults`).
- `client.cfg` — how far away looked up events are drawn, and client debug rendering.

Each logger's section in `loggers.cfg` has:

- `isEnabled` — turns the logger off entirely, regardless of regions.
- `logByDefault` — whether the logger records events where no region has a rule for it.
  Positional loggers only.
- `oldestDataCutoff` — how long to retain logged events before they're deleted (e.g. `4months`).
- `databaseCacheSizeMB` — the most memory SQLite may use to cache the logger's database. A bigger
  cache makes writing to a large database faster.
- A maximum in-memory queue size per logger, to bound memory use if the database falls behind.

Events are written by a background thread per logger, in SQLite's WAL mode. A server crash loses
none of the events already written, and a power cut at most the last few seconds of *logs* (not
world data). Neither can corrupt the database. WAL mode needs the world on a local disk, not a
network filesystem.

## API for other mods

Some actions never reach Tempora by themselves: items put into a drawer or barrel by clicking it,
blocks broken by a tool that skips Forge's events, explosions that aren't vanilla ones, or blocks
written straight into chunk storage. Other mods can report these through
`com.colen.tempora.api.TemporaApi`. Reported events are stored and looked up exactly like the ones
Tempora logs itself, and follow the same config and region rules.

Compile against Tempora's API jar (the `api` classifier) and only call it when Tempora is loaded:

```java
if (Loader.isModLoaded(TemporaApi.MODID)) {
    TemporaApi.logItemsAdded(player, insertedStack, inserted, this);
}
```

| Method | Logger |
|---|---|
| `logItemsAdded`, `logItemsRemoved` | `inventory` |
| `logBlockBreak` (call before removing the block) | `break` |
| `logBlockPlace` | `place` |
| `logBlockChange` (only for changes that bypass `Chunk.func_150807_a`) | `change` |
| `logExplosion` | `explosion` |
| `logItemUse` | `use` |
| `logCommand` | `command` |
| `logPlayerPosition` | `playerpos` |
| `logEntitySpawn` | `spawn` |
| `logEntityDeath` | `death` |
| `logEntityPosition` | `mobpos` |

Calls only do anything on the server thread, and only while that logger is turned on. Each method's
Javadoc says what it records and when to call it.

### Adding your own logger

A mod can also add a logger of its own, of either [kind](#logger-kinds). Subclass one of these pairs,
giving the event `@Column` fields of type `boolean`, `int`, `long`, `float`, `double` or `String`:

| Kind | Logger | Event | Log an event with |
|---|---|---|---|
| Positional | `GenericPositionalLogger` | `PositionalEventInfo` | `logEvent(event, dimensionID, x, y, z)` |
| Global | `GenericGlobalLogger` | `GlobalEventInfo` | `logEvent(event)` |

Register it with `TemporaLoggerManager.register(logger, MyEventInfo::new)` while mods load (preInit,
init or postInit). Both kinds get their own database, config section and `/tempora sql` support; a
positional logger also gets region rules and `/tempora lookup`, a global one `/tempora global`. Feed
it events from your own methods, calling its `logEvent` (see `WeatherLogger` and `TPSLogger` for
global examples):

```java
public class ReactorLogger extends GenericGlobalLogger<ReactorEventInfo> {

    // getLoggerName(), getShortName(), newEventInfo() and getLoggerEventType() as for any logger.

    public void logMeltdown(int dimensionID, String reactorName) {
        ReactorEventInfo event = new ReactorEventInfo();
        event.dimensionID = dimensionID;
        event.reactorName = reactorName;
        logEvent(event);
    }
}
```

- Every `@Column` field needs a non-null initial value, e.g. `public String reactorName = "";`. That value
  becomes the column's default, so events logged before you added a column read back with it rather than
  `null`. Your chat line still sees it for those old events, so pick a value it can show, e.g. "unknown".
- Mistakes such as mismatched names, a column type Tempora can't store, or a column with no initial value
  are reported when the logger registers, not when the server starts.
- If one of your events can't be shown (its chat line or render packet throws), a lookup shows a
  placeholder for it and logs the error naming your logger. The rest of the lookup is unaffected.
- A logger that fails later (its database won't open, its in-world rendering throws) is switched off
  on its own, and the rest of Tempora carries on.
- Register it on the client too if its events should be drawn in the world. Clients that don't have
  it simply skip its events.

#### Drawing a positional logger's events

Lookup results are sent to the player's client and drawn in the world for a few seconds. Drawing
lives apart from the logger, in an `EventRenderer` registered from your **client** proxy, after the
logger itself:

```java
EventRenderers.register(myLogger, new MyEventRenderer());
```

A renderer is handed only the events in the player's dimension within render distance, farthest
first, and draws in world coordinates. `BlockEventRenderer`, `EntityEventRenderer` and
`PlayerGhostRenderer` cover the common cases: draw a block, a mob, or the player who did it. The
client only knows what the event's `toBytes`/`fromBytes` send, so include the fields your renderer
needs. A logger without a renderer still shows each event as an outlined block with a label.

## Data collected

For server operators: Tempora records player UUIDs, position history, block changes (with
container contents at the time of breaking, for some loggers), inventory transfers, and full
command text/arguments, plus server-wide weather and TPS, retained for the configured
`oldestDataCutoff` window. Any operator can
query this data via `/tempora sql` or `/tempora lookup`.

## Testing

Tempora's in-game tests use [Horizon-QA](https://github.com/GTNewHorizons/Horizon-QA) and live in
`com.colen.tempora.gametests`. CI runs them on every push. To run them yourself, on a dedicated dev server that runs
the tests and stops:

```
./gradlew runServer "--mcJvmArgs=-Dhorizonqa.mode=ci" "--mcJvmArgs=-Dhorizonqa.tests=tempora"
```

Point `level-name` in `run/server/server.properties` at a new world first: the tests build in the world, and a new
one is made empty for them.

### Testing in the full pack

`./gradlew runFullPackClient` downloads the latest [GTNH daily build](https://github.com/GTNewHorizons/GTNH-Daily-Builds),
swaps your build of Tempora into it and launches the whole pack (Java 8). `./gradlew runFullPackServer` does the same
for the Java 17+ server. Add `--debug-jvm` or use your IDE's Debug action on the task to attach a debugger. Each run
checks for a newer daily and moves to it, keeping your worlds and settings, so client and server stay able to connect;
`-PfullPack.update=false` (or `--offline`) stays on the installed one, and `-PfullPack.tag=<daily>` pins one.
Instances live in `run/fullpack`. Installing the server sets it up for a dev client to join (`online-mode=false`,
`white-list=false`) and accepts the [Minecraft EULA](https://aka.ms/MinecraftEULA) (`-PfullPack.acceptEula=false` to
do that yourself); after that, your changes to `server.properties` and `eula.txt` are kept.

`./gradlew runFullPackServerTests` also injects Horizon-QA and runs every in-game test in the pack, Tempora's and those
other mods ship (GT5, AE2, ...), on a fresh world, then stops; it fails if a test does. Narrow it with
`-PfullPack.tests=tempora`. Reports go to `build/horizonqa-fullpack`. See
[gradle/fullpack.gradle.kts](gradle/fullpack.gradle.kts) for all options.

## License

MIT — see [LICENSE](src/main/resources/LICENSE).
