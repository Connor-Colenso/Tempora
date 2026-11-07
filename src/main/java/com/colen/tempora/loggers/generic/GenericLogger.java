package com.colen.tempora.loggers.generic;

import static com.colen.tempora.Tempora.LOG;
import static com.colen.tempora.utils.ReflectionUtils.getAllTableColumns;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.command.ICommandSender;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.colen.tempora.TemporaLoggerManager;
import com.colen.tempora.chat.ChatComponentWrappedTranslation;
import com.colen.tempora.chat.EventChat;
import com.colen.tempora.enums.LoggerEventType;
import com.colen.tempora.loggers.generic.column.ColumnDef;
import com.colen.tempora.loggers.generic.column.ColumnType;
import com.colen.tempora.utils.PlayerFilter;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.TimeWindow;
import com.gtnewhorizon.gtnhlib.chat.customcomponents.ChatComponentNumber;

import cpw.mods.fml.common.FMLCommonHandler;

/**
 * Base for every logger, holds its names, config, database and the worker thread that writes events. Loggers extend one
 * of the two kinds
 * <ul>
 * <li>{@link GenericPositionalLogger} for stuff that happens at a place, like a block breaking. Follows region rules
 * and gets looked up around a position</li>
 * <li>{@link GenericGlobalLogger} for server wide stuff, like weather changing. No position, can be looked up from
 * anywhere</li>
 * </ul>
 */
@SuppressWarnings("SqlDialectInspection")
public abstract class GenericLogger<EventInfo extends GenericEventInfo> {

    protected LoggerDatabase databaseManager = new LoggerDatabase(this);

    private ArrayBlockingQueue<EventInfo> concurrentEventQueue; // Init'd after config load, size comes from config

    protected boolean isEnabledByConfig;
    private Thread queueWorkerThread;
    // Set at shutdown, worker writes what's still queued then exits
    private volatile boolean shuttingDownDatabaseThread = false;
    // Set when shutdown gives up waiting, worker exits without writing the rest
    private volatile boolean abandonQueue = false;
    private volatile boolean isRunning;
    // Started for this server, db open and worker running (until it crashes, see isRunning)
    private volatile boolean started;
    private volatile long lastQueueFullWarning;
    // Set by requestPrune, worker prunes next time round instead of waiting for the schedule
    private volatile boolean pruneRequested;

    private static volatile boolean loggersStarted;

    // Why this logger can't store events right now, null if it can. Set by the worker, announceFailures tells ops from
    // the server thread since the worker can't safely send chat
    private volatile String failure;
    // Worker thread only
    private int consecutiveBatchFailures;
    // Server thread only
    private long lastFailureAnnounced;
    private static final int BATCH_FAILURES_BEFORE_ALERT = 3;
    private static final long FAILURE_ANNOUNCE_INTERVAL_MILLISECONDS = TimeUnit.MINUTES.toMillis(10);

    // Events dropped since admins were last told, from either thread (server thread for a bad position, worker for
    // something SQLite refused)
    private final AtomicInteger droppedEvents = new AtomicInteger();
    private volatile String lastDropReason = "";
    // Server thread only
    private long lastDropAnnounced;
    // A broken mob can be logged every few seconds, so only tell admins this often with the count
    private static final long DROP_ANNOUNCE_INTERVAL_MILLISECONDS = TimeUnit.MINUTES.toMillis(1);

    private int maxShutdownTimeoutMilliseconds;
    private int maxEventsInQueueBeforeServerFreeze;
    private static final int QUEUE_POLL_TIMEOUT_MILLISECONDS = 1000;
    private static final int MAX_EVENTS_PER_BATCH = 5000;
    private static final long PRUNE_INTERVAL_MILLISECONDS = TimeUnit.MINUTES.toMillis(10);
    private static final long QUEUE_FULL_WARNING_INTERVAL_MILLISECONDS = TimeUnit.SECONDS.toMillis(10);

    // Only the two kinds extend this directly
    GenericLogger() {}

    public boolean isEnabledByConfig() {
        return isEnabledByConfig;
    }

    /**
     * Whether events reaching this logger get stored right now (enabled, started and worker alive). False whenever
     * Tempora is switched off. Hot paths like mixins and the inventory tracker check this before doing any work
     */
    public final boolean isActive() {
        return isEnabledByConfig && started && isRunning;
    }

    public LoggerDatabase getDatabaseManager() {
        return databaseManager;
    }

    public abstract @NotNull LoggerEventType getLoggerEventType();

    public abstract @NotNull EventInfo newEventInfo();

    public @NotNull List<EventInfo> generateQueryResults(ResultSet resultSet) throws SQLException {

        List<EventInfo> eventList = new ArrayList<>();

        List<ColumnDef> columns = getAllTableColumns(this);

        while (resultSet.next()) {

            EventInfo element = newEventInfo();

            for (ColumnDef col : columns) {
                ColumnDef.ColumnAccessor accessor = col.columnAccessor;

                Object value = accessor.reader.read(resultSet, col.name);
                accessor.set(element, value);
            }

            eventList.add(element);
        }

        return eventList;
    }

    // Also the SQL table name, don't rename it
    public abstract @NotNull String getLoggerName();

    // What players type in commands, e.g. "break". Fine to change unlike getLoggerName
    public abstract @NotNull String getShortName();

    /**
     * Name shown in chat, e.g. "Player block breaks", from {@code tempora.logger.<logger name>.name} translated on the
     * client. Loggers from other mods add the key to their own lang file, otherwise chat falls back to the short name
     */
    public final IChatComponent getDisplayName() {
        String key = translationKey("name");
        return StatCollector.canTranslate(key) ? new ChatComponentTranslation(key)
            : new ChatComponentText(getShortName());
    }

    // From tempora.logger.<logger name>.description, wrapped and translated on the client
    public final IChatComponent getDescription() {
        String key = translationKey("description");
        return new ChatComponentWrappedTranslation(
            StatCollector.canTranslate(key) ? key : "tempora.logger.no_description");
    }

    // Description as a hover, for loggers that haven't started so their settings aren't known
    public final IChatComponent getDescriptionHover() {
        return whiteDescription();
    }

    // The [Info] hover in lookups, description then how the server has it set up, a grey line each
    public final IChatComponent getInfoHover() {
        List<IChatComponent> settings = new ArrayList<>();
        addSettingsInfo(settings);
        settings.add(
            settingLine(
                "tempora.logger.setting.retention",
                duration(databaseManager.getOldestDataMilliseconds() / 1000)));
        return EventChat.titledHover(whiteDescription(), settings.toArray(new IChatComponent[0]));
    }

    // White not grey since it opens a hover, see EventChat.titledHover
    private IChatComponent whiteDescription() {
        IChatComponent description = getDescription();
        description.getChatStyle()
            .setColor(EnumChatFormatting.WHITE);
        return description;
    }

    /**
     * Adds a line per server setting worth knowing when reading this logger's events (e.g. whether it saves NBT) to the
     * [Info] hover. Retention always goes last. Override and call super, then add your own with {@link #settingLine},
     * each line gets translated on the client with this server's values
     */
    protected void addSettingsInfo(List<IChatComponent> lines) {}

    /**
     * Settings line from a translation key and args. Args can be strings, numbers (formatted on the client) or another
     * settingLine/duration
     */
    protected static ChatComponentWrappedTranslation settingLine(String key, Object... args) {
        return new ChatComponentWrappedTranslation(key, args);
    }

    // Seconds as the biggest unit that divides them exactly, e.g. "4 months" or "10 seconds", translated on the client
    protected static ChatComponentWrappedTranslation duration(long seconds) {
        long[] unitSeconds = { 31_557_600L, 2_592_000L, 604_800L, 86_400L, 3_600L, 60L, 1L };
        String[] units = { "year", "month", "week", "day", "hour", "minute", "second" };
        for (int i = 0; i < units.length; i++) {
            if (seconds > 0 && seconds % unitSeconds[i] == 0) {
                long count = seconds / unitSeconds[i];
                return new ChatComponentWrappedTranslation(
                    "tempora.duration." + units[i] + (count == 1 ? ".one" : ".many"),
                    count);
            }
        }
        return new ChatComponentWrappedTranslation("tempora.duration.second.many", seconds);
    }

    // Ticks as a duration, or as ticks if not a whole number of seconds
    protected static ChatComponentWrappedTranslation ticks(int ticks) {
        if (ticks % 20 == 0) return duration(ticks / 20);
        return new ChatComponentWrappedTranslation(
            ticks == 1 ? "tempora.duration.tick.one" : "tempora.duration.tick.many",
            ticks);
    }

    private String translationKey(String part) {
        return "tempora.logger." + getLoggerName() + "." + part;
    }

    /**
     * Column holding the player behind each event, matched by /tempora player and the p: filter of /tempora lookup. Has
     * to be a {@link ColumnType#PLAYER} column and gets indexed with the timestamp (see {@link #playerIndexColumns}).
     * Null if events have no player, then player searches skip this logger
     */
    protected @Nullable String playerColumn() {
        return null;
    }

    /**
     * Columns of the index on {@link #playerColumn}, must start with it and the timestamp. Override to add more, e.g.
     * to cover every column one of your own queries reads
     */
    protected String playerIndexColumns() {
        return playerColumn() + ", timestamp";
    }

    public final boolean hasPlayerColumn() {
        return playerColumn() != null;
    }

    public void handleCustomLoggerConfig(Configuration config) {}

    /** Called as the server stops while events can still be logged, e.g. for whatever is still going on */
    protected void beforeServerClose() {}

    // Extra tables, stuff a logger keeps next to its events in the same db file. E.g. block change stack traces, stored
    // once however many events share them

    /** Creates this logger's extra tables and indexes when its db opens, inside the opening transaction */
    protected void createExtraTables(Connection conn) throws SQLException {}

    /**
     * Writes what a batch's events refer to, before them and in the same transaction so an event is never stored
     * without it. Runs before columns are read so it can fill in the events' references too (e.g. a mob's number).
     * Worker thread
     * <p>
     * Returns what to run once the batch commits (e.g. mark the rows written) or null. Not run if the batch fails, and
     * anything written here rolls back with the events
     */
    protected @Nullable Runnable writeExtras(List<? extends GenericEventInfo> events, Connection conn)
        throws SQLException {
        return null;
    }

    /**
     * Fills in what read back events refer to, e.g. their stack traces. Query thread with its read only connection,
     * after every read of this logger's events
     */
    public void loadExtras(List<? extends GenericEventInfo> events, Connection conn) throws SQLException {}

    /** Deletes extra rows nothing points at any more after pruning. Worker thread, inside the prune transaction */
    protected void pruneExtras(Connection conn) throws SQLException {}

    public final void registerEvent() {
        if (!getLoggerEventType().shouldRegister()) return;

        // A logger whose handlers Forge rejects (e.g. a broken @SubscribeEvent in another mod's logger) shouldn't stop
        // the server starting. It can still log what gets reported to it directly
        try {
            switch (getLoggerEventType()) {
                case ForgeEvent -> MinecraftForge.EVENT_BUS.register(this);
                case MinecraftEvent -> FMLCommonHandler.instance()
                    .bus()
                    .register(this);
                default -> throw new IllegalStateException("Unknown LoggerEventType: " + getLoggerEventType());
            }
        } catch (RuntimeException e) {
            LOG.error(
                "Could not register {} for events, so it will miss everything it listens for.",
                getLoggerName(),
                e);
        }
    }

    /**
     * Fills in the shared bits (time, the db assigns the id) and queues it. Each kind wraps this in its own logEvent to
     * add its own stuff like position
     */
    final void logNewEvent(EventInfo eventInfo) {
        eventInfo.timestamp = System.currentTimeMillis();

        queueEventInfo(eventInfo);
    }

    public final void queueEventInfo(EventInfo eventInfo) {
        if (!isEnabledByConfig) return;
        if (!isRunning) return;
        if (!shouldRecord(eventInfo)) return;

        // Fixed per world so just set it here
        eventInfo.versionID = ModpackVersionData.CURRENT_VERSION;

        try {
            if (getQueueSize() >= maxEventsInQueueBeforeServerFreeze) {
                warnQueueFull();
            }
            // Wait while the queue is full, but not if the worker is gone (crashed or stopped with the server). Nobody
            // would empty it, so the wait and the tick would never end
            while (!concurrentEventQueue.offer(eventInfo, QUEUE_POLL_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)) {
                if (!isRunning) return;
            }
        } catch (InterruptedException e) {
            LOG.error("Queue worker for {} was interrupted while queueing an event.", getLoggerName());
        }
    }

    // Only every so often, not on every event while full, or it floods the log and ops' chat right when the server is
    // struggling
    private void warnQueueFull() {
        long now = System.currentTimeMillis();
        if (now - lastQueueFullWarning < QUEUE_FULL_WARNING_INTERVAL_MILLISECONDS) return;
        lastQueueFullWarning = now;

        LOG.warn(
            "Maximum queue size of {} reached for {}, slowing server down.",
            maxEventsInQueueBeforeServerFreeze,
            getLoggerName());
        PlayerUtils.sendMessageToAuthorisedTemporaUsers(
            "tempora.warning.queue_too_large",
            getLoggerName(),
            new ChatComponentNumber(getQueueSize()));
    }

    // Whether to store this event at all, positional loggers check region rules here
    boolean shouldRecord(EventInfo eventInfo) {
        return true;
    }

    /**
     * Notes an event this logger can't store and why, logs it now and tells admins in chat later (see
     * {@link #announceFailures}). Any thread
     */
    final void noteDroppedEvent(GenericEventInfo event, String reason) {
        if (event instanceof PositionalEventInfo positional) {
            LOG.warn(
                "{} could not store an event at {}, {}, {} in dimension {} (timestamp {}), so it is dropped: {}",
                getLoggerName(),
                positional.x,
                positional.y,
                positional.z,
                positional.dimensionID,
                event.timestamp,
                reason);
        } else {
            LOG.warn(
                "{} could not store an event (timestamp {}), so it is dropped: {}",
                getLoggerName(),
                event.timestamp,
                reason);
        }
        lastDropReason = reason;
        droppedEvents.incrementAndGet();
    }

    private void startQueueWorker() {
        failure = null;
        consecutiveBatchFailures = 0;
        lastFailureAnnounced = 0;
        droppedEvents.set(0);
        lastDropAnnounced = 0;
        shuttingDownDatabaseThread = false;
        abandonQueue = false;
        isRunning = true;

        concurrentEventQueue = new ArrayBlockingQueue<>(maxEventsInQueueBeforeServerFreeze);

        queueWorkerThread = new Thread(this::queueLoop, "Tempora-" + getLoggerName());
        queueWorkerThread.setDaemon(false);
        queueWorkerThread.setUncaughtExceptionHandler((thr, ex) -> {
            LOG.fatal(
                "Queue worker in thread '{}' crashed – this is a serious failure! This logger is now disabled.",
                thr.getName(),
                ex);
            isRunning = false;
        });
        queueWorkerThread.start();
    }

    private void queueLoop() {
        List<EventInfo> buffer = new ArrayList<>(MAX_EVENTS_PER_BATCH);
        // Due straight away so a server that was down a while catches up on old data first
        long nextPruneTime = 0;
        Exception death = null;

        try {
            while (!abandonQueue) {
                if (pruneRequested) {
                    pruneRequested = false;
                    nextPruneTime = 0;
                }
                boolean pruneDue = System.currentTimeMillis() >= nextPruneTime;
                // Don't wait for events while there's old data to prune or when shutting down
                long pollTimeout = pruneDue || shuttingDownDatabaseThread ? 0 : QUEUE_POLL_TIMEOUT_MILLISECONDS;

                EventInfo event;
                try {
                    event = concurrentEventQueue.poll(pollTimeout, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    // Shutdown wakes the thread this way, whatever's still queued is written below
                    event = null;
                }

                if (event != null) buffer.add(event);
                concurrentEventQueue.drainTo(buffer, MAX_EVENTS_PER_BATCH - buffer.size());

                if (!buffer.isEmpty()) {
                    writeBatch(buffer);
                } else if (shuttingDownDatabaseThread) {
                    break;
                }

                // One batch of old data at a time between writes until it's all gone
                if (pruneDue && !shuttingDownDatabaseThread) {
                    // Never throws, so a bad prune doesn't kill the worker, it just tries again next time
                    boolean morePending = databaseManager.pruneOldData();
                    nextPruneTime = morePending ? 0 : System.currentTimeMillis() + PRUNE_INTERVAL_MILLISECONDS;
                }
            }

        } catch (Exception e) {
            LOG.error(
                "Queue worker {} crashed unexpectedly and is shutting down. Remaining queue size={}",
                getLoggerName(),
                concurrentEventQueue.size(),
                e);
            death = e;

        } finally {
            isRunning = false;
            // Errors aren't caught above but still end up here
            if (!shuttingDownDatabaseThread) failure = "its worker stopped" + (death != null ? ": " + death : "");
        }
    }

    private void writeBatch(List<EventInfo> buffer) {
        try {
            databaseManager.insertBatch(buffer);
            consecutiveBatchFailures = 0;
            failure = null;
        } catch (Exception e) {
            if (++consecutiveBatchFailures >= BATCH_FAILURES_BEFORE_ALERT) failure = String.valueOf(e.getMessage());
            // One bad batch (bad value, disk full, SQLITE_BUSY etc) shouldn't kill the worker, log it and keep draining
            LOG.error(
                "Failed to insert a batch of {} event(s) for {}. This batch is dropped, logging continues.",
                buffer.size(),
                getLoggerName(),
                e);
        } finally {
            buffer.clear();
        }
    }

    public final void genericConfig(@NotNull Configuration config) {
        isEnabledByConfig = config.getBoolean("enabled", getLoggerName(), true, "Enables this logger.");
        maxShutdownTimeoutMilliseconds = config.getInt(
            "maxShutdownTimeoutMilliseconds",
            getLoggerName(),
            QUEUE_POLL_TIMEOUT_MILLISECONDS * 20,
            QUEUE_POLL_TIMEOUT_MILLISECONDS * 2,
            Integer.MAX_VALUE,
            "Maximum time (in milliseconds) to wait for this logger to flush and stop during shutdown. If it hasn't stopped by then, what is still queued is discarded.");

        maxEventsInQueueBeforeServerFreeze = config.getInt(
            "maxEventsInQueueBeforeServerFreeze",
            getLoggerName(),
            30_000,
            5_000,
            1_000_000,
            "Maximum amount of events that can be queued before the server will stop tick processing to try catch up. If this is happening a lot, you are logging too much.");

        kindConfig(config);
        databaseManager.genericConfig(config);
    }

    // Settings only one kind has, e.g. logByDefault for positional loggers
    void kindConfig(Configuration config) {}

    /**
     * Throws if Tempora can't store this logger's columns. Unknown field type, a field that starts null, a player
     * column that isn't one, or a table SQLite refuses (column named after a keyword, duplicate names). Runs at
     * registration so the author finds out then and not on server start
     */
    public final void checkColumns() {
        List<ColumnDef> columns = getAllTableColumns(this);

        // Player searches match by number, so the player column has to store one
        String playerColumn = playerColumn();
        if (playerColumn != null) {
            boolean storesPlayers = columns.stream()
                .anyMatch(col -> col.name.equals(playerColumn) && col.columnType == ColumnType.PLAYER);
            if (!storesPlayers) {
                throw new IllegalStateException(
                    getLoggerName() + "'s playerColumn() is "
                        + playerColumn
                        + ", which is not a ColumnType.PLAYER column.");
            }
        }

        databaseManager.checkSql();
    }

    // Index name suffix -> indexed columns. Every logger gets searched by time, positional ones by place too and ones
    // with a player column by player
    Map<String, String> tableIndexes() {
        Map<String, String> indexes = new LinkedHashMap<>();
        // Ascending is fine, SQLite reads indexes backwards just as fast for newest first
        indexes.put("timestamp", "timestamp");
        if (playerColumn() != null) indexes.put("player", playerIndexColumns());
        return indexes;
    }

    // Server start/world load
    private void initialiseLogger() {
        try {
            databaseManager.initialiseDatabase();
            startQueueWorker();
            started = true;

        } catch (SQLException e) {
            throw new RuntimeException("[Tempora] Failed to initialise database for logger " + getLoggerName(), e);
        }
    }

    /** Whether this logger started for the current server so its db is open. False if disabled or it failed to start */
    public final boolean notStarted() {
        return !started;
    }

    // Lookups

    @FunctionalInterface
    interface QueryParameters {

        void set(PreparedStatement statement) throws SQLException;
    }

    /**
     * Runs a read only query on this logger's db and returns events in query order. Query thread only, see
     * {@link TemporaQueries}. Throws {@link TemporaQueries.QueryTimedOutException} if the lookup ran out of time
     */
    final List<EventInfo> queryEvents(String sql, QueryParameters parameters) throws SQLException {
        try (PreparedStatement ps = TemporaQueries.connection(this)
            .prepareStatement(sql)) {
            parameters.set(ps);

            List<EventInfo> events;
            try (ResultSet rs = ps.executeQuery()) {
                events = generateQueryResults(rs);
            }
            loadExtras(events, TemporaQueries.connection(this));
            return events;
        } catch (SQLException e) {
            TemporaQueries.throwIfTimedOut();
            throw e;
        }
    }

    /**
     * Latest events of the players picked out by {@code players}, up to {@code limit}, from anywhere. Query thread (see
     * {@link TemporaQueries}) and only for loggers with a {@link #playerColumn}. Showing them lists them in chat with
     * {@code narrowCommand} to search just this logger, and draws the ones in the sender's dimension. Says nothing if
     * there are none so callers checking lots of loggers can report that once
     */
    public final FoundEvents<EventInfo> findEventsBy(PlayerFilter players, TimeWindow window, int limit,
        String narrowCommand) {
        String playerColumn = playerColumn();
        if (playerColumn == null) throw new IllegalStateException(getLoggerName() + " has no player column.");

        // One player hits the player index since "IN (?)" turns into "= ?". Only excluding players walks the timestamp
        // index from the newest end instead
        String sql = String.format("""
            SELECT * FROM %s
            WHERE timestamp >= ? AND timestamp <= ?%s
            ORDER BY timestamp DESC
            LIMIT ?;
            """, getLoggerName(), players.sqlCondition(playerColumn));

        List<EventInfo> events;
        try {
            events = queryEvents(sql, ps -> {
                long now = System.currentTimeMillis();
                ps.setLong(1, window.oldestMillis(now));
                ps.setLong(2, window.newestMillis(now));
                int index = players.bind(ps, 3);
                ps.setInt(index, limit);
            });
        } catch (SQLException e) {
            return FoundEvents.failed(this, e);
        }

        return FoundEvents.found(this, events, limit, narrowCommand);
    }

    // Draws found events in the world for the sender, if this kind has anything to draw. Server thread
    void drawInWorld(ICommandSender sender, List<EventInfo> events) {}

    /**
     * Header with the logger name and count, then the events in chat oldest first. Takes them newest first like they're
     * queried, plus the query limit so the header can say there might be more. Clicking the header fills in
     * {@code narrowCommand} to search just this logger. Says nothing if there are none so callers checking lots of
     * loggers can
     * report that once
     */
    final void sendEventsInChat(ICommandSender sender, List<EventInfo> newestFirst, int limit, String narrowCommand) {
        if (newestFirst.isEmpty()) return;

        int eventsFound = newestFirst.size();

        // E.g. "Player block breaks (3) [Info]", clicking the name searches just this logger
        IChatComponent nameHover = new ChatComponentTranslation(
            "tempora.command.lookup.header.hover",
            getDisplayName());
        nameHover.getChatStyle()
            .setColor(EnumChatFormatting.GRAY);
        IChatComponent name = getDisplayName();
        name.getChatStyle()
            .setColor(EnumChatFormatting.GOLD)
            .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, nameHover))
            .setChatClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, narrowCommand));

        IChatComponent info = new ChatComponentTranslation("tempora.chat.info");
        info.getChatStyle()
            .setColor(EnumChatFormatting.DARK_AQUA)
            .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, getInfoHover()));

        IChatComponent header = new ChatComponentTranslation(
            "tempora.command.lookup.header",
            name,
            countLabel(eventsFound, limit),
            info);
        header.getChatStyle()
            .setColor(EnumChatFormatting.GRAY);
        sender.addChatMessage(header);

        sendPendingWarning(sender);

        // Newest at the bottom where chat gets read from
        for (int i = eventsFound - 1; i >= 0; i--) {
            sender.addChatMessage(chatLineOrPlaceholder(newestFirst.get(i)));
        }
    }

    // E.g. "(3)", or "(latest 5)" when the lookup hit its limit so there could be more
    static IChatComponent countLabel(int eventsFound, int limit) {
        IChatComponent count = new ChatComponentTranslation(
            eventsFound >= limit ? "tempora.command.lookup.header.latest" : "tempora.command.lookup.header.count",
            new ChatComponentNumber(eventsFound));
        count.getChatStyle()
            .setColor(EnumChatFormatting.GRAY);
        return count;
    }

    // Warn if loads of events are waiting to be written, shouldn't really happen
    final void sendPendingWarning(ICommandSender sender) {
        if (getQueueSize() <= 100) return;

        IChatComponent tooMany = new ChatComponentTranslation(
            "tempora.command.lookup.too_many_pending",
            new ChatComponentNumber(getQueueSize()));
        tooMany.getChatStyle()
            .setColor(EnumChatFormatting.GOLD);
        sender.addChatMessage(tooMany);
    }

    /**
     * E.g. "[break]" before each of this logger's events when a lookup is sorted by time, name and description on
     * hover. Clicking it fills in {@code narrowCommand} to search just this logger
     */
    final IChatComponent timelineTag(String narrowCommand) {
        IChatComponent title = getDisplayName();
        title.getChatStyle()
            .setColor(EnumChatFormatting.GOLD);
        IChatComponent hover = EventChat.titledHover(
            title,
            getInfoHover(),
            new ChatComponentTranslation("tempora.command.lookup.header.hover", getDisplayName()));

        IChatComponent tag = new ChatComponentTranslation("tempora.command.lookup.tag", getShortName());
        tag.getChatStyle()
            .setColor(EnumChatFormatting.GOLD)
            .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover))
            .setChatClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, narrowCommand));
        return tag;
    }

    /**
     * An event's chat line, or a grey placeholder if building it throws (e.g. a bug in another mod's logger). One
     * broken event only costs its own line not the whole lookup, the server log says which logger and event
     */
    public static IChatComponent chatLineOrPlaceholder(GenericEventInfo event) {
        try {
            return event.localiseText();
        } catch (RuntimeException e) {
            LOG.error(
                "Could not make the chat line for {} event {}, so it is shown as a placeholder.",
                event.getLoggerName(),
                event.eventID,
                e);
            IChatComponent placeholder = new ChatComponentTranslation("tempora.command.lookup.event_failed");
            placeholder.getChatStyle()
                .setColor(EnumChatFormatting.GRAY)
                .setItalic(true);
            return placeholder;
        }
    }

    // Lookup results for this logger couldn't be shown because showing them threw
    final void sendShowFailed(ICommandSender sender, RuntimeException e) {
        LOG.error("Could not show {}'s lookup results.", getLoggerName(), e);
        IChatComponent failed = new ChatComponentTranslation("tempora.command.lookup.logger_failed", getDisplayName());
        failed.getChatStyle()
            .setColor(EnumChatFormatting.RED);
        sender.addChatMessage(failed);
    }

    final void sendQueryFailed(ICommandSender sender, SQLException e) {
        IChatComponent failed = new ChatComponentTranslation(
            "tempora.command.lookup.query_failed",
            getShortName(),
            e.getMessage());
        failed.getChatStyle()
            .setColor(EnumChatFormatting.RED);
        sender.addChatMessage(failed);
    }

    /** Whether the loggers have started for the current server */
    public static boolean areLoggersStarted() {
        return loggersStarted;
    }

    public static void onServerStart() {
        loggersStarted = true;

        // Before the loggers, their player columns store numbers from it
        try {
            PlayerIDs.open();
        } catch (SQLException e) {
            LOG.error(
                "Could not open {}.db, so events with a player can't be stored until the server restarts.",
                PlayerIDs.TABLE,
                e);
        }

        if (!TemporaLoggerManager.getLoggerList()
            .isEmpty()) {
            LOG.info("Opening Tempora loggers.");
        } else {
            LOG.info("All Tempora loggers are disabled by config.");
        }

        for (GenericLogger<?> logger : TemporaLoggerManager.getLoggerList()) {
            if (logger.isEnabledByConfig) {
                LOG.info("Initialising {} logger.", logger.getLoggerName());
                try {
                    logger.initialiseLogger();
                } catch (Exception e) {
                    // One logger failing to start (db won't open, broken logger from another mod) shouldn't take down
                    // the server or the other loggers
                    LOG.error(
                        "{} failed to start, so it will not log anything until the server restarts. Other loggers are unaffected.",
                        logger.getLoggerName(),
                        e);
                    logger.closeAfterFailedStart();
                }
            } else {
                LOG.info("Skipped {}, not enabled in config.", logger.getLoggerName());
            }
        }
    }

    public static void onServerClose() {
        List<GenericLogger<?>> startedLoggers = new ArrayList<>();
        for (GenericLogger<?> logger : TemporaLoggerManager.getLoggerList()) {
            if (logger.started) startedLoggers.add(logger);
        }

        for (GenericLogger<?> logger : startedLoggers) {
            try {
                logger.beforeServerClose();
            } catch (Exception e) {
                LOG.error("Error in {} as the server closed.", logger.getLoggerName(), e);
            }
        }

        // Tell every worker to stop before waiting on any, so they flush at the same time instead of one after another
        for (GenericLogger<?> logger : startedLoggers) {
            logger.requestWorkerStop();
        }

        // Each logger on its own, one failing to close can't leave the others' non daemon workers running or the server
        // won't exit
        for (GenericLogger<?> logger : startedLoggers) {
            try {
                if (logger.awaitWorkerStop()) {
                    logger.databaseManager.shutdownDatabase();
                } else {
                    // Closing would wait on the statement the worker is stuck in
                    LOG.error(
                        "{}'s worker is still running after two waits of {}ms, so its database is left open and the "
                            + "server stops without it.",
                        logger.getLoggerName(),
                        logger.maxShutdownTimeoutMilliseconds);
                }
            } catch (Exception e) {
                LOG.error("Error closing {}.", logger.getLoggerName(), e);
            } finally {
                // So nothing carries over into the next world
                logger.concurrentEventQueue.clear();
                logger.started = false;
            }
        }

        // Last, once nothing can store another player
        PlayerIDs.close();

        loggersStarted = false;
    }

    private void closeAfterFailedStart() {
        isRunning = false;
        // Stop the worker in case it got as far as starting
        shuttingDownDatabaseThread = true;
        try {
            databaseManager.shutdownDatabase();
        } catch (Exception ignored) {
            // Never opened
        }
    }

    private void requestWorkerStop() {
        shuttingDownDatabaseThread = true;
        // Wake the worker if it's waiting on events instead of letting the wait time out
        queueWorkerThread.interrupt();
    }

    // Whether the worker stopped. If not after two waits it's left to finish on its own
    private boolean awaitWorkerStop() throws InterruptedException {
        // Should be near instant if the queue is small
        LOG.info(
            "Attempting to shut down queue worker {} for logger {}. There are {} events remaining to be processed within {}ms.",
            queueWorkerThread.getName(),
            getLoggerName(),
            concurrentEventQueue.size(),
            maxShutdownTimeoutMilliseconds);
        queueWorkerThread.join(maxShutdownTimeoutMilliseconds);

        if (queueWorkerThread.isAlive()) {
            abandonQueue = true;
            LOG.error(
                "Queue worker {} for logger {} did not stop in time. Tempora discarded {} events.",
                queueWorkerThread.getName(),
                getLoggerName(),
                concurrentEventQueue.size());
            // Once more for the batch it's writing, but not forever, a worker stuck in SQLite (locked file) can't hold
            // up server stop
            queueWorkerThread.join(maxShutdownTimeoutMilliseconds);
        }
        return !queueWorkerThread.isAlive();
    }

    /**
     * Makes the worker prune data older than oldestDataCutoff within about a second instead of at the next scheduled
     * run (every 10 min). Used by the tests
     */
    public void requestPrune() {
        pruneRequested = true;
    }

    /** Why this logger can't store events right now, null if it can */
    public @Nullable String getFailure() {
        return failure;
    }

    /**
     * Tells online admins about loggers that can't store events (batches keep failing or the worker died) every ten
     * minutes while it lasts, and about single dropped events at most once a minute each. Otherwise only the server log
     * would know. Server thread
     */
    public static void announceFailures() {
        long now = System.currentTimeMillis();
        for (GenericLogger<?> logger : TemporaLoggerManager.getLoggerList()) {
            if (!logger.started) continue;
            logger.announceFailure(now);
            logger.announceDroppedEvents(now);
        }
    }

    private void announceFailure(long now) {
        String failure = this.failure;
        if (failure == null) return;
        if (now - lastFailureAnnounced < FAILURE_ANNOUNCE_INTERVAL_MILLISECONDS) return;
        lastFailureAnnounced = now;

        LOG.error("{} can't store events: {}", getLoggerName(), failure);
        IChatComponent message = new ChatComponentTranslation(
            "tempora.warning.logger_failing",
            getDisplayName(),
            failure);
        message.getChatStyle()
            .setColor(EnumChatFormatting.RED);
        PlayerUtils.sendMessageToAuthorisedTemporaUsers(message);
    }

    private void announceDroppedEvents(long now) {
        if (droppedEvents.get() == 0) return;
        if (now - lastDropAnnounced < DROP_ANNOUNCE_INTERVAL_MILLISECONDS) return;
        lastDropAnnounced = now;
        int dropped = droppedEvents.getAndSet(0);

        IChatComponent alert = new ChatComponentTranslation("tempora.wand.alert");
        alert.getChatStyle()
            .setColor(EnumChatFormatting.RED);
        PlayerUtils.sendMessageToAuthorisedTemporaUsers(
            EventChat.line(
                "tempora.warning.events_dropped",
                alert,
                getDisplayName(),
                new ChatComponentNumber(dropped),
                lastDropReason));
    }

    public int getQueueSize() {
        // Null until the logger starts
        return concurrentEventQueue == null ? 0 : concurrentEventQueue.size();
    }
}
