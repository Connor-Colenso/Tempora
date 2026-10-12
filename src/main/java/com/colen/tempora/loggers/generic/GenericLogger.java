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
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import net.minecraft.command.ICommandSender;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.TemporaLoggerManager;
import com.colen.tempora.enums.LoggerEventType;
import com.colen.tempora.loggers.generic.column.ColumnDef;
import com.colen.tempora.loggers.generic.enums.LogWriteSafety;
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
    private volatile boolean shuttingDownDatabaseThread = false;
    private volatile boolean isRunning;
    // Started for the current server: database open, worker running (until it crashes, see isRunning).
    private volatile boolean started;

    private static volatile boolean loggersStarted;

    private int maxShutdownTimeoutMilliseconds;
    private int maxEventsInQueueBeforeServerFreeze;
    private static final int QUEUE_POLL_TIMEOUT_MILLISECONDS = 1000;

    // Only the two kinds extend this directly.
    GenericLogger() {}

    protected LogWriteSafety defaultLogWriteSafetyMode() {
        return LogWriteSafety.NORMAL;
    }

    public boolean isEnabledByConfig() {
        return isEnabledByConfig;
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

    public void handleCustomLoggerConfig(Configuration config) {}

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
     * Fills in what every event shares (id and time) and queues it. Each kind of logger wraps this in its own logEvent,
     * which fills in what that kind adds, such as the position.
     */
    final void logNewEvent(EventInfo eventInfo) {
        eventInfo.eventID = UUID.randomUUID()
            .toString();
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
                LOG.warn(
                    "Maximum queue size of {} reached for {}, slowing server down.",
                    maxEventsInQueueBeforeServerFreeze,
                    getLoggerName());
                PlayerUtils.sendMessageToOps(
                    "tempora.op.warning.queue.too.large",
                    getLoggerName(),
                    new ChatComponentNumber(getQueueSize()));
            }
            concurrentEventQueue.put(eventInfo);
        } catch (InterruptedException e) {
            LOG.error("Queue worker for {} was interrupted while queueing an event.", getLoggerName());
        }
    }

    // Whether this event should be stored at all. Positional loggers check region rules here.
    boolean shouldRecord(EventInfo eventInfo) {
        return true;
    }

    private void startQueueWorker() {
        shuttingDownDatabaseThread = false;
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
        List<EventInfo> buffer = new ArrayList<>(maxEventsInQueueBeforeServerFreeze);

        try {
            while (!shuttingDownDatabaseThread || !concurrentEventQueue.isEmpty()) {

                EventInfo event = concurrentEventQueue.poll(QUEUE_POLL_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS);

                if (event == null) {
                    continue;
                }

                buffer.add(event);
                concurrentEventQueue.drainTo(buffer, 5000);

                try {
                    databaseManager.insertBatch(buffer);
                } catch (Exception e) {
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

        } catch (InterruptedException e) {
            // Expected during shutdown — just exit
            Thread.currentThread()
                .interrupt();

        } catch (Exception e) {
            LOG.error(
                "Queue worker {} crashed unexpectedly and is shutting down. Remaining queue size={}",
                getLoggerName(),
                concurrentEventQueue.size(),
                e);

        } finally {
            isRunning = false;
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
            "Maximum time (in milliseconds) to wait for this logger to flush and stop during shutdown. Use 0 to wait forever until all queued events are written.");

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

    // Index name suffix -> indexed columns. Every logger is searched by time, positional loggers by place as well.
    Map<String, String> tableIndexes() {
        Map<String, String> indexes = new LinkedHashMap<>();
        indexes.put("timestamp", "timestamp DESC");
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

    @FunctionalInterface
    interface QueryParameters {

        void set(PreparedStatement statement) throws SQLException;
    }

    // Runs a read-only query on this logger's database, returning its events in the order the query gives them.
    final List<EventInfo> queryEvents(String sql, QueryParameters parameters) throws SQLException {
        try (Connection conn = databaseManager.getReadOnlyConnection();
            PreparedStatement ps = conn.prepareStatement(sql)) {
            parameters.set(ps);

            try (ResultSet rs = ps.executeQuery()) {
                return generateQueryResults(rs);
            }
        }
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

        IChatComponent name = new ChatComponentText(getShortName());
        name.getChatStyle()
            .setColor(EnumChatFormatting.WHITE)
            .setBold(true);

        IChatComponent count = new ChatComponentTranslation(
            eventsFound >= limit ? "tempora.command.lookup.header.latest" : "tempora.command.lookup.header.count",
            eventsFound);
        count.getChatStyle()
            .setColor(EnumChatFormatting.GRAY);

        IChatComponent hover = new ChatComponentTranslation("tempora.command.lookup.header.hover", getShortName());
        hover.getChatStyle()
            .setColor(EnumChatFormatting.GRAY);

        IChatComponent header = new ChatComponentTranslation("tempora.command.lookup.header", name, count);
        header.getChatStyle()
            .setColor(EnumChatFormatting.GOLD)
            .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover))
            .setChatClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, narrowCommand));
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
            sender.addChatMessage(
                newestFirst.get(i)
                    .localiseText());
        }
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
        // Each logger separately: one that fails to close must not leave the others' (non-daemon) worker threads
        // running, which would stop the server from exiting.
        for (GenericLogger<?> logger : TemporaLoggerManager.getLoggerList()) {
            if (!logger.started) continue;

            try {
                logger.shutdownQueueWorkerThreads();
                logger.databaseManager.shutdownDatabase();
            } catch (Exception e) {
                LOG.error("Error closing {}.", logger.getLoggerName(), e);
            } finally {
                // Just to ensure that we are not carrying data over to a new world opening.
                logger.concurrentEventQueue.clear();
                logger.started = false;
            }
        }

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

    private void shutdownQueueWorkerThreads() throws InterruptedException {
        shuttingDownDatabaseThread = true;

        // Wait to shut down, should be near instant if queue is low.
        LOG.info(
            "Attempting to shut down queue worker {} for logger {}. There are {} events remaining to be processed within {}ms.",
            queueWorkerThread.getName(),
            getLoggerName(),
            concurrentEventQueue.size(),
            maxShutdownTimeoutMilliseconds);
        queueWorkerThread.join(maxShutdownTimeoutMilliseconds);

        if (queueWorkerThread.isAlive()) {
            queueWorkerThread.interrupt();
            LOG.error(
                "Queue worker {} for logger {} did not stop in time. Tempora discarded {} events.",
                queueWorkerThread.getName(),
                getLoggerName(),
                concurrentEventQueue.size());
            queueWorkerThread.join();
        }
    }

    public int getQueueSize() {
        // Null until the logger starts.
        return concurrentEventQueue == null ? 0 : concurrentEventQueue.size();
    }
}
