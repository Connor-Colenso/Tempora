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
import com.colen.tempora.enums.LoggerEventType;
import com.colen.tempora.loggers.generic.column.ColumnDef;
import com.colen.tempora.utils.PlayerUtils;
import com.gtnewhorizon.gtnhlib.chat.customcomponents.ChatComponentNumber;

import cpw.mods.fml.common.FMLCommonHandler;

/**
 * What every logger shares: its names, config, database, and the worker thread that writes its events. Loggers extend
 * one of its two kinds:
 * <ul>
 * <li>{@link GenericPositionalLogger}, for events that happen at a place, such as a block breaking. These follow region
 * rules and are looked up around a position.</li>
 * <li>{@link GenericGlobalLogger}, for events that belong to the whole server, such as the weather changing. These have
 * no position and can be looked up from anywhere.</li>
 * </ul>
 */
@SuppressWarnings("SqlDialectInspection")
public abstract class GenericLogger<EventInfo extends GenericEventInfo> {

    protected LoggerDatabase databaseManager = new LoggerDatabase(this);

    private ArrayBlockingQueue<EventInfo> concurrentEventQueue; // Must be init'd after config load, so it can set the

    protected boolean isEnabledByConfig;
    private Thread queueWorkerThread;
    // Set at shutdown: the worker writes what is still queued, then exits.
    private volatile boolean shuttingDownDatabaseThread = false;
    // Set when shutdown gives up waiting: the worker exits without writing the rest.
    private volatile boolean abandonQueue = false;
    private volatile boolean isRunning;
    // Started for the current server: database open, worker running (until it crashes, see isRunning).
    private volatile boolean started;
    private volatile long lastQueueFullWarning;
    // Set by requestPrune: the worker deletes old data next time round rather than waiting for the next scheduled time.
    private volatile boolean pruneRequested;

    private static volatile boolean loggersStarted;

    // Why this logger can't store events right now, or null while it can. Set by its worker, which can't safely send
    // chat itself, announceFailures tells ops from the server thread.
    private volatile String failure;
    // Worker thread only.
    private int consecutiveBatchFailures;
    // Server thread only.
    private long lastFailureAnnounced;
    private static final int BATCH_FAILURES_BEFORE_ALERT = 3;
    private static final long FAILURE_ANNOUNCE_INTERVAL_MILLISECONDS = TimeUnit.MINUTES.toMillis(10);

    private int maxShutdownTimeoutMilliseconds;
    private int maxEventsInQueueBeforeServerFreeze;
    private static final int QUEUE_POLL_TIMEOUT_MILLISECONDS = 1000;
    private static final int MAX_EVENTS_PER_BATCH = 5000;
    private static final long PRUNE_INTERVAL_MILLISECONDS = TimeUnit.MINUTES.toMillis(10);
    private static final long QUEUE_FULL_WARNING_INTERVAL_MILLISECONDS = TimeUnit.SECONDS.toMillis(10);

    // Only the two kinds extend this directly.
    GenericLogger() {}

    public boolean isEnabledByConfig() {
        return isEnabledByConfig;
    }

    /**
     * Whether events reaching this logger now are stored: it is enabled, started for the current server and its
     * worker is running. False whenever Tempora is switched off. Hot paths (mixins, the inventory tracker) check this
     * before doing any work for an event.
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

        // Cached, immutable column definitions
        List<ColumnDef> columns = getAllTableColumns(this);

        while (resultSet.next()) {

            EventInfo element = newEventInfo();

            // Populate column-mapped fields (base fields inherited from GenericEventInfo included)
            for (ColumnDef col : columns) {
                ColumnDef.ColumnAccessor accessor = col.columnAccessor;

                Object value = accessor.reader.read(resultSet, col.name);
                accessor.set(element, value);
            }

            eventList.add(element);
        }

        return eventList;
    }

    // Logger name is also the SQL table name. So choose it and be careful not to rename it.
    public abstract @NotNull String getLoggerName();

    // What players type and see in commands, e.g. "break". Unlike getLoggerName, this can be changed freely.
    public abstract @NotNull String getShortName();

    /**
     * This logger's name in chat, e.g. "Player block breaks", from the translation key
     * {@code tempora.logger.<logger name>.name}, translated on the client. A logger from another mod adds the key to
     * its own lang file; without it, chat shows the short name.
     */
    public final IChatComponent getDisplayName() {
        String key = translationKey("name");
        return StatCollector.canTranslate(key) ? new ChatComponentTranslation(key)
            : new ChatComponentText(getShortName());
    }

    // What the logger records, from tempora.logger.<logger name>.description, wrapped and translated on the client.
    public final IChatComponent getDescription() {
        String key = translationKey("description");
        return new ChatComponentWrappedTranslation(
            StatCollector.canTranslate(key) ? key : "tempora.logger.no_description");
    }

    // The [Info] hover in lookups: what this logger records, then how the server has it set up, one grey line each.
    public final IChatComponent getInfoHover() {
        IChatComponent hover = new ChatComponentText("");
        hover.appendSibling(getDescription());

        List<IChatComponent> settings = new ArrayList<>();
        addSettingsInfo(settings);
        settings.add(
            settingLine(
                "tempora.logger.setting.retention",
                duration(databaseManager.getOldestDataMilliseconds() / 1000)));
        for (IChatComponent setting : settings) {
            setting.getChatStyle()
                .setColor(EnumChatFormatting.GRAY);
            hover.appendSibling(new ChatComponentText("\n"))
                .appendSibling(setting);
        }
        return hover;
    }

    /**
     * Adds a line for each of this logger's server settings worth knowing when reading its events, e.g. whether it
     * saves NBT, to the [Info] hover in lookups. How long events are kept is always added last. Override to add your
     * own after calling super, with {@link #settingLine}: each line is translated on the client, with the values this
     * server uses.
     */
    protected void addSettingsInfo(List<IChatComponent> lines) {}

    /** A settings line: a translation key and its arguments, each a string or another settingLine or duration. */
    protected static ChatComponentWrappedTranslation settingLine(String key, Object... args) {
        return new ChatComponentWrappedTranslation(key, args);
    }

    // Seconds as the largest unit that divides them exactly, e.g. "4 months" or "10 seconds", translated on the client.
    protected static ChatComponentWrappedTranslation duration(long seconds) {
        long[] unitSeconds = { 31_557_600L, 2_592_000L, 604_800L, 86_400L, 3_600L, 60L, 1L };
        String[] units = { "year", "month", "week", "day", "hour", "minute", "second" };
        for (int i = 0; i < units.length; i++) {
            if (seconds > 0 && seconds % unitSeconds[i] == 0) {
                long count = seconds / unitSeconds[i];
                return new ChatComponentWrappedTranslation(
                    "tempora.duration." + units[i] + (count == 1 ? ".one" : ".many"),
                    String.valueOf(count));
            }
        }
        return new ChatComponentWrappedTranslation("tempora.duration.second.many", String.valueOf(seconds));
    }

    // Server ticks as a duration, or as ticks when they aren't a whole number of seconds.
    protected static ChatComponentWrappedTranslation ticks(int ticks) {
        if (ticks % 20 == 0) return duration(ticks / 20);
        return new ChatComponentWrappedTranslation(
            ticks == 1 ? "tempora.duration.tick.one" : "tempora.duration.tick.many",
            String.valueOf(ticks));
    }

    private String translationKey(String part) {
        return "tempora.logger." + getLoggerName() + "." + part;
    }

    public void handleCustomLoggerConfig(Configuration config) {}

    /** As the server stops, while events can still be logged, e.g. for what is still going on then. */
    protected void beforeServerClose() {}

    // --------------------------------------
    // Extra tables
    // --------------------------------------
    // For what a logger keeps beside its events, in the same database file, e.g. the block change logger's stack
    // traces, stored once each however many events share them.

    /** Creates this logger's extra tables and their indexes, as its database opens. Inside the opening transaction. */
    protected void createExtraTables(Connection conn) throws SQLException {}

    /**
     * Writes what the events in a batch refer to, before them and in the same transaction, so an event is never stored
     * without it. May also fill in the events' references to it, e.g. a mob's number, as it runs before their columns
     * are read. On the worker thread. Returns what to do once the batch has committed, e.g. mark the rows written, or
     * null. If the batch fails it is not run, and the rows written here are rolled back with the events.
     */
    protected @Nullable Runnable writeExtras(List<? extends GenericEventInfo> events, Connection conn)
        throws SQLException {
        return null;
    }

    /**
     * Fills in what events read back refer to, e.g. their stack traces. On the query thread, with its read-only
     * connection, after every read of this logger's events.
     */
    public void loadExtras(List<? extends GenericEventInfo> events, Connection conn) throws SQLException {}

    /**
     * Deletes extra rows nothing refers to any more, after pruning has deleted old events. On the worker thread,
     * inside the pruning transaction.
     */
    protected void pruneExtras(Connection conn) throws SQLException {}

    public final void registerEvent() {
        if (!getLoggerEventType().shouldRegister()) return;

        // A logger whose event handlers Forge rejects (e.g. a malformed @SubscribeEvent in another mod's logger) must
        // not stop the server starting. It can still log what is reported to it directly.
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
     * Fills in what every event shares (its time, the database assigns its id) and queues it. Each kind of logger wraps
     * this in its own logEvent, which fills in what that kind adds, such as the position.
     */
    final void logNewEvent(EventInfo eventInfo) {
        eventInfo.timestamp = System.currentTimeMillis();

        queueEventInfo(eventInfo);
    }

    public final void queueEventInfo(EventInfo eventInfo) {
        if (!isEnabledByConfig) return;
        if (!isRunning) return;
        if (!shouldRecord(eventInfo)) return;

        // Populate this automatically, as it is fixed per world for all events.
        eventInfo.versionID = ModpackVersionData.CURRENT_VERSION;

        // Blocking & thread safe.
        try {
            if (getQueueSize() >= maxEventsInQueueBeforeServerFreeze) {
                warnQueueFull();
            }
            // Waits while the queue is full, but not for a worker that is gone (crashed, or stopped with the server):
            // with nobody to empty the queue, that wait would never end, and on the server thread nor would the tick.
            while (!concurrentEventQueue.offer(eventInfo, QUEUE_POLL_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)) {
                if (!isRunning) return;
            }
        } catch (InterruptedException e) {
            LOG.error("Queue worker for {} was interrupted while queueing an event.", getLoggerName());
        }
    }

    // Once in a while, rather than for every event while the queue is full, which would flood the log and ops' chat
    // just when the server is struggling.
    private void warnQueueFull() {
        long now = System.currentTimeMillis();
        if (now - lastQueueFullWarning < QUEUE_FULL_WARNING_INTERVAL_MILLISECONDS) return;
        lastQueueFullWarning = now;

        LOG.warn(
            "Maximum queue size of {} reached for {}, slowing server down.",
            maxEventsInQueueBeforeServerFreeze,
            getLoggerName());
        PlayerUtils.sendMessageToOps(
            "tempora.warning.queue_too_large",
            getLoggerName(),
            new ChatComponentNumber(getQueueSize()));
    }

    // Whether this event should be stored at all. Positional loggers check region rules here.
    boolean shouldRecord(EventInfo eventInfo) {
        return true;
    }

    private void startQueueWorker() {
        failure = null;
        consecutiveBatchFailures = 0;
        lastFailureAnnounced = 0;
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
            // Shut down the logger and prevent new events queueing.
            isRunning = false;
        });
        queueWorkerThread.start();
    }

    private void queueLoop() {
        List<EventInfo> buffer = new ArrayList<>(MAX_EVENTS_PER_BATCH);
        // Due straight away, so a server that was down for a while catches up on old data first.
        long nextPruneTime = 0;
        Exception death = null;

        try {
            while (!abandonQueue) {
                if (pruneRequested) {
                    pruneRequested = false;
                    nextPruneTime = 0;
                }
                boolean pruneDue = System.currentTimeMillis() >= nextPruneTime;
                // No waiting for events while there is old data left to prune, or once shutting down.
                long pollTimeout = pruneDue || shuttingDownDatabaseThread ? 0 : QUEUE_POLL_TIMEOUT_MILLISECONDS;

                EventInfo event;
                try {
                    event = concurrentEventQueue.poll(pollTimeout, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    // How shutdown wakes this thread. Whatever is still queued is written below.
                    event = null;
                }

                if (event != null) buffer.add(event);
                concurrentEventQueue.drainTo(buffer, MAX_EVENTS_PER_BATCH - buffer.size());

                if (!buffer.isEmpty()) {
                    writeBatch(buffer);
                } else if (shuttingDownDatabaseThread) {
                    break;
                }

                // One batch of old data at a time, between writes, until there is none left.
                if (pruneDue && !shuttingDownDatabaseThread) {
                    // Never throws, so a failing prune doesn't end the worker: it is tried again next time.
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
            // An Error isn't caught above, but still ends up here.
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
            // A single bad/transient batch (a bad value, disk full, SQLITE_BUSY, ...) must not
            // permanently kill this logger's worker thread — log it and keep draining the queue.
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
        isEnabledByConfig = config
            .getBoolean("isEnabled", getLoggerName(), true, "Enables this loggers functionality.");
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

    // Settings only one kind of logger has, e.g. logByDefault for positional loggers.
    void kindConfig(Configuration config) {}

    /**
     * Throws if Tempora can't store this logger's columns, e.g. a field of a type it doesn't know. Run as the logger
     * registers, so its author finds out then rather than when a server starts.
     */
    public final void checkColumns() {
        checkKindColumns(getAllTableColumns(this));
    }

    // What one kind of logger needs of its columns.
    void checkKindColumns(List<ColumnDef> columns) {}

    // Index name suffix -> indexed columns. Every logger is searched by time, positional loggers by place as well.
    Map<String, String> tableIndexes() {
        Map<String, String> indexes = new LinkedHashMap<>();
        // Ascending: SQLite reads an index backwards just as fast, for newest-first lookups.
        indexes.put("timestamp", "timestamp");
        return indexes;
    }

    // Run on server startup/world load.
    private void initialiseLogger() {
        try {
            databaseManager.initialiseDatabase();
            startQueueWorker();
            started = true;

        } catch (SQLException e) {
            throw new RuntimeException("[Tempora] Failed to initialise database for logger " + getLoggerName(), e);
        }
    }

    /**
     * Whether this logger started for the current server, so its database is open. False when it is disabled, or
     * failed to start.
     */
    public final boolean notStarted() {
        return !started;
    }

    // --------------------------------------
    // Lookups
    // --------------------------------------

    /**
     * One logger's part of a lookup: found on the query thread, then shown on the server thread. See
     * {@link TemporaQueries}.
     */
    @FunctionalInterface
    public interface FoundEvents {

        // Sends them to the sender, on the server thread. Returns how many were shown.
        int show(ICommandSender sender);
    }

    @FunctionalInterface
    interface QueryParameters {

        void set(PreparedStatement statement) throws SQLException;
    }

    /**
     * Runs a read-only query on this logger's database, returning its events in the order the query gives them. Only
     * on the query thread, see {@link TemporaQueries}. Throws {@link TemporaQueries.QueryTimedOutException} if the
     * lookup ran out of time.
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

    // What a lookup shows for this logger when its query failed.
    final FoundEvents queryFailed(SQLException e) {
        return sender -> {
            sendQueryFailed(sender, e);
            return 0;
        };
    }

    /**
     * Shows a header naming this logger and how many events were found, then lists them in chat, oldest first. Takes
     * them newest first, as they are queried, and how many the query was limited to, so the header can say when there
     * may be more. Clicking the header fills in {@code narrowCommand}, to search this logger alone. Says nothing if
     * there are none, so callers checking many loggers can report that once.
     */
    final void sendEventsInChat(ICommandSender sender, List<EventInfo> newestFirst, int limit, String narrowCommand) {
        if (newestFirst.isEmpty()) return;

        int eventsFound = newestFirst.size();

        // E.g. "Player block breaks (3) [Info]". The name searches this logger alone when clicked.
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

        IChatComponent count = new ChatComponentTranslation(
            eventsFound >= limit ? "tempora.command.lookup.header.latest" : "tempora.command.lookup.header.count",
            eventsFound);
        count.getChatStyle()
            .setColor(EnumChatFormatting.GRAY);

        IChatComponent info = new ChatComponentTranslation("tempora.chat.info");
        info.getChatStyle()
            .setColor(EnumChatFormatting.DARK_AQUA)
            .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, getInfoHover()));

        IChatComponent header = new ChatComponentTranslation("tempora.command.lookup.header", name, count, info);
        header.getChatStyle()
            .setColor(EnumChatFormatting.GRAY);
        sender.addChatMessage(header);

        // If events waiting to process is large, then warn the user.
        // Shouldn't really be happening.
        if (getQueueSize() > 100) {
            IChatComponent tooMany = new ChatComponentTranslation(
                "tempora.command.lookup.too_many_pending",
                new ChatComponentNumber(getQueueSize()));
            tooMany.getChatStyle()
                .setColor(EnumChatFormatting.GOLD);
            sender.addChatMessage(tooMany);
        }

        // Newest at the bottom, where chat is read from.
        for (int i = eventsFound - 1; i >= 0; i--) {
            sender.addChatMessage(chatLineOrPlaceholder(newestFirst.get(i)));
        }
    }

    /**
     * An event's chat line, or a grey placeholder if making it throws, e.g. a bug in another mod's logger. One broken
     * event costs only its own line, not the rest of the lookup. The server log says which logger and event.
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

    /**
     * Shows a lookup's results for this logger, or says they couldn't be shown if that throws, so one broken logger
     * doesn't stop the others' results being shown.
     */
    final FoundEvents guarded(FoundEvents show) {
        return sender -> {
            try {
                return show.show(sender);
            } catch (RuntimeException e) {
                LOG.error("Could not show {}'s lookup results.", getLoggerName(), e);
                IChatComponent failed = new ChatComponentTranslation(
                    "tempora.command.lookup.logger_failed",
                    getDisplayName());
                failed.getChatStyle()
                    .setColor(EnumChatFormatting.RED);
                sender.addChatMessage(failed);
                return 0;
            }
        };
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

    // --------------------------------------
    // Static methods
    // --------------------------------------

    /** Whether the loggers have been started for the current server. */
    public static boolean areLoggersStarted() {
        return loggersStarted;
    }

    public static void onServerStart() {
        loggersStarted = true;

        // Before the loggers, as their player columns store each player's number from it.
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
                    // One logger that cannot start (a database it cannot open, a broken logger from another mod)
                    // must not take the server, or the other loggers, down with it.
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

        // Every worker is told to stop before waiting on any of them, so they write what they have left at the same
        // time rather than one after another.
        for (GenericLogger<?> logger : startedLoggers) {
            logger.requestWorkerStop();
        }

        // Each logger separately: one that fails to close must not leave the others' (non-daemon) worker threads
        // running, which would stop the server from exiting.
        for (GenericLogger<?> logger : startedLoggers) {
            try {
                if (logger.awaitWorkerStop()) {
                    logger.databaseManager.shutdownDatabase();
                } else {
                    // Closing it would wait on the statement the worker is stuck in.
                    LOG.error(
                        "{}'s worker is still running after two waits of {}ms, so its database is left open and the "
                            + "server stops without it.",
                        logger.getLoggerName(),
                        logger.maxShutdownTimeoutMilliseconds);
                }
            } catch (Exception e) {
                LOG.error("Error closing {}.", logger.getLoggerName(), e);
            } finally {
                // Just to ensure that we are not carrying data over to a new world opening.
                logger.concurrentEventQueue.clear();
                logger.started = false;
            }
        }

        // Last, once nothing can store another player.
        PlayerIDs.close();

        loggersStarted = false;
    }

    private void closeAfterFailedStart() {
        isRunning = false;
        // Stops the worker, in case it got as far as starting.
        shuttingDownDatabaseThread = true;
        try {
            databaseManager.shutdownDatabase();
        } catch (Exception ignored) {
            // It never opened.
        }
    }

    private void requestWorkerStop() {
        shuttingDownDatabaseThread = true;
        // Wakes the worker if it is waiting for events, rather than leaving it to notice once the wait times out.
        queueWorkerThread.interrupt();
    }

    // Whether the worker stopped. If it hasn't after two waits, it is left to finish on its own.
    private boolean awaitWorkerStop() throws InterruptedException {
        // Should be near instant if the queue is low.
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
            // Once more for the batch it is writing, but not forever, a worker stuck inside SQLite (a locked file)
            // must not stop the server from stopping.
            queueWorkerThread.join(maxShutdownTimeoutMilliseconds);
        }
        return !queueWorkerThread.isAlive();
    }

    /**
     * Has the worker delete data older than oldestDataCutoff within about a second, rather than at its next scheduled
     * time (every 10 minutes). Used by the tests.
     */
    public void requestPrune() {
        pruneRequested = true;
    }

    /** Why this logger can't store events right now, or null while it can. */
    public @Nullable String getFailure() {
        return failure;
    }

    /**
     * Tells online ops about loggers that can't store events (their batches keep failing, or their worker stopped),
     * every ten minutes while it lasts, as otherwise only the server log would know. On the server thread.
     */
    public static void announceFailures() {
        long now = System.currentTimeMillis();
        for (GenericLogger<?> logger : TemporaLoggerManager.getLoggerList()) {
            String failure = logger.failure;
            if (failure == null || !logger.started) continue;
            if (now - logger.lastFailureAnnounced < FAILURE_ANNOUNCE_INTERVAL_MILLISECONDS) continue;
            logger.lastFailureAnnounced = now;

            LOG.error("{} can't store events: {}", logger.getLoggerName(), failure);
            IChatComponent message = new ChatComponentTranslation(
                "tempora.warning.logger_failing",
                logger.getDisplayName(),
                failure);
            message.getChatStyle()
                .setColor(EnumChatFormatting.RED);
            PlayerUtils.sendMessageToOps(message);
        }
    }

    public int getQueueSize() {
        // Null until the logger starts.
        return concurrentEventQueue == null ? 0 : concurrentEventQueue.size();
    }
}
