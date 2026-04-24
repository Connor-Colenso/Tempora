package com.colen.tempora.loggers.generic;

import static com.colen.tempora.Tempora.LOG;
import static com.colen.tempora.utils.ReflectionUtils.getAllTableColumns;
import static com.colen.tempora.utils.RenderingUtils.squaredDistance;

import java.awt.Color;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.colen.tempora.utils.PlayerUtils;
import com.gtnewhorizon.gtnhlib.chat.customcomponents.ChatComponentNumber;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.TemporaLoggerManager;
import com.colen.tempora.enums.LoggerEventType;
import com.colen.tempora.loggers.generic.column.ColumnDef;
import com.colen.tempora.loggers.generic.undo.UndoEventInfo;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SuppressWarnings("SqlDialectInspection")
public abstract class GenericPositionalLogger<EventInfo extends GenericEventInfo> {

    protected PositionalLoggerDatabase databaseManager = new PositionalLoggerDatabase(this);

    private ArrayBlockingQueue<EventInfo> concurrentEventQueue; // Must be init'd after config load, so it can set the
    protected List<EventInfo> transparentEventsToRenderInWorld = new ArrayList<>();
    protected List<EventInfo> nonTransparentEventsToRenderInWorld = new ArrayList<>();
    private static final long SECONDS_RENDERING_DURATION = 10;

    protected boolean isLoggerEnabled;
    private Thread queueWorkerThread;
    private volatile boolean shuttingDownDatabaseThread = false;

    private int maxShutdownTimeoutMilliseconds;
    private int maxEventsInQueueBeforeServerFreeze;
    private static final int QUEUE_POLL_TIMEOUT_MILLISECONDS = 1000;

    public void addEventToRender(EventInfo event) {
        event.eventRenderCreationTime = System.currentTimeMillis();

        if (event.needsTransparencyToRender()) {
            transparentEventsToRenderInWorld.add(event);
        } else {
            nonTransparentEventsToRenderInWorld.add(event);
        }
    }

    @SideOnly(Side.CLIENT)
    public List<EventInfo> getSortedLatestEventsByDistance(Collection<EventInfo> input, RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        int playerDim = mc.thePlayer.dimension;

        Map<String, EventInfo> latestPerBlock = new HashMap<>();

        for (EventInfo element : input) {
            if (element.dimensionID != playerDim) continue;

            String key = (int) element.x + "," + (int) element.y + "," + (int) element.z;
            EventInfo existing = latestPerBlock.get(key);

            if (existing == null || element.timestamp > existing.timestamp) {
                latestPerBlock.put(key, element);
            }
        }

        List<EventInfo> sorted = new ArrayList<>(latestPerBlock.values());
        sortByDistanceDescending(sorted, e);
        return sorted;
    }

    @SideOnly(Side.CLIENT)
    public void sortByDistanceDescending(List<EventInfo> list, RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        double px = mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * e.partialTicks;
        double py = mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * e.partialTicks;
        double pz = mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * e.partialTicks;

        list.sort((a, b) -> Double.compare(squaredDistance(b, px, py, pz), squaredDistance(a, px, py, pz)));
    }

    protected LogWriteSafety defaultLogWriteSafetyMode() {
        return LogWriteSafety.NORMAL;
    }

    public PositionalLoggerDatabase getDatabaseManager() {
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

            // Populate shared/base fields
            element.populateDefaultFieldsFromResultSet(resultSet);

            // Populate column-mapped fields
            for (ColumnDef col : columns) {
                ColumnDef.ColumnAccessor accessor = col.columnAccessor;

                Object value = accessor.reader.read(resultSet, col.name);
                accessor.set(element, value);
            }

            eventList.add(element);
        }

        return eventList;
    }

    @SideOnly(Side.CLIENT)
    public abstract void renderEventsInWorld(RenderWorldLastEvent renderEvent);

    // Logger name is also the SQL table name. So choose it and be careful not to rename it.
    public abstract @NotNull String getLoggerName();

    public void handleCustomLoggerConfig(Configuration config) {}

    public final void registerEvent() {
        if (!getLoggerEventType().shouldRegister()) return;

        switch (getLoggerEventType()) {
            case ForgeEvent -> MinecraftForge.EVENT_BUS.register(this);
            case MinecraftEvent -> FMLCommonHandler.instance()
                .bus()
                .register(this);
            default -> throw new IllegalStateException("Unknown LoggerEventType: " + getLoggerEventType());
        }
    }

    public final void queueEventInfo(EventInfo eventInfo) {
        if (!isLoggerEnabled) return;

        // Populate this automatically, as it is fixed per world for all events.
        eventInfo.versionID = ModpackVersionData.CURRENT_VERSION;

        // Blocking & thread safe.
        try {
            if (getQueueSize() >= maxEventsInQueueBeforeServerFreeze) {
                LOG.warn("Maximum queue size of {} reached for {}, slowing server down.", maxEventsInQueueBeforeServerFreeze, getLoggerName());
                PlayerUtils.sendMessageToOps("tempora.op.warning.queue.too.large", getLoggerName(), new ChatComponentNumber(getQueueSize()));
            }
            concurrentEventQueue.put(eventInfo);
        } catch (InterruptedException e) {
            LOG.error("Queue worker for {} was interrupted while queueing an event.", getLoggerName());
        }
    }

    private void startQueueWorker() {
        shuttingDownDatabaseThread = false;

        // Create the queue, this can be re-used as config will not shift between world reloads etc.
        if (concurrentEventQueue == null) {
            concurrentEventQueue = new ArrayBlockingQueue<>(maxEventsInQueueBeforeServerFreeze);
        }

        queueWorkerThread = new Thread(this::queueLoop, "Tempora-" + getLoggerName());
        queueWorkerThread.setDaemon(false);
        queueWorkerThread.setUncaughtExceptionHandler((thr, ex) -> {
            LOG.fatal("Queue worker in thread '{}' crashed – this is a serious failure! This logger is now effectively disabled.", thr.getName(), ex);
            // Shut down the logger and prevent new events queueing.
            isLoggerEnabled = false;
        });
        queueWorkerThread.start();
    }

    private void queueLoop() {
        List<EventInfo> buffer = new ArrayList<>(maxEventsInQueueBeforeServerFreeze);

        try {
            while (true) {
                if (shuttingDownDatabaseThread && concurrentEventQueue.isEmpty()) {
                    return;
                }

                EventInfo event = concurrentEventQueue.poll(QUEUE_POLL_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS);

                if (event == null) {
                    continue;
                }

                buffer.add(event);
                concurrentEventQueue.drainTo(buffer, maxEventsInQueueBeforeServerFreeze / 10); // todo review over capacity
                databaseManager.insertBatch(buffer);
                buffer.clear();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Queue worker {} interrupted during shutdown. Remaining {} events will be discarded.",
                getLoggerName(),
                concurrentEventQueue.size());
        } catch (Exception e) {
            LOG.error(
                "Queue worker {} threw non-interrupt based exception and has shut down. Remaining queue size is={}",
                getLoggerName(),
                concurrentEventQueue.size()
            );
        } finally {
            isLoggerEnabled = false;
        }
    }

    public final void genericConfig(@NotNull Configuration config) {
        isLoggerEnabled = config.getBoolean("isEnabled", getLoggerName(), true, "Enables this loggers functionality.");
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
            100_000,
            1_000,
            Integer.MAX_VALUE,
            "Maximum amount of events that can be queued before the server will stop tick processing to try catch up. If this is happening a lot, you are logging too much.");

        databaseManager.genericConfig(config);
    }

    // Run on server startup/world load.
    private void initialiseLogger() {
        try {
            databaseManager.initialiseDatabase();
            startQueueWorker();

        } catch (SQLException e) {
            throw new RuntimeException("[Tempora] Failed to initialise database for logger " + getLoggerName(), e);
        }
    }

    private void clearEvents() {
        concurrentEventQueue.clear();
    }

    // --------------------------------------
    // Static methods
    // --------------------------------------

    public static void onServerStart() {
        if (!TemporaLoggerManager.getLoggerList().isEmpty()) {
            LOG.info("Opening Tempora loggers.");
        }

        for (GenericPositionalLogger<?> logger : TemporaLoggerManager.getLoggerList()) {
            if (logger.isLoggerEnabled) {
                LOG.info("Initialising {} logger.", logger.getLoggerName());
                logger.initialiseLogger();
            } else {
                LOG.info("Skipped {}, not enabled in config.", logger.getLoggerName());
            }
        }
    }

    public static void onServerClose() {
        try {
            for (GenericPositionalLogger<?> logger : TemporaLoggerManager.getLoggerList()) {
                if (logger.isLoggerEnabled) {
                    logger.shutdownQueueWorkerThreads();
                    logger.databaseManager.shutdownDatabase();
                }
            }

        } catch (Exception e) {
            LOG.error("Error closing resources.", e);
        } finally {
            // Just to ensure that we are not carrying data over to a new world opening.
            for (GenericPositionalLogger<?> logger : TemporaLoggerManager.getLoggerList()) {
                logger.clearEvents();
            }
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

    public void clearOldEventsToRender() {
        double expiryCutoff = System.currentTimeMillis() - SECONDS_RENDERING_DURATION * 1000L;
        transparentEventsToRenderInWorld
            .removeIf(eventPosition -> eventPosition.eventRenderCreationTime < expiryCutoff);
        nonTransparentEventsToRenderInWorld
            .removeIf(eventPosition -> eventPosition.eventRenderCreationTime < expiryCutoff);
    }

    public Color getColour() {
        return Color.RED;
    }

    public boolean isUndoEnabled() {
        return false;
    }

    // If you enable undo support via isUndoEnabled override, then you MUST also override these and implement their
    // logic.
    protected void undoEventInternal(GenericEventInfo eventInfo, EntityPlayer player) {
        throw new UnsupportedOperationException(
            "The class " + getLoggerName() + " supports undo but has no implementation for undoEvent. This is a bug!");
    }

    public @NotNull UndoEventInfo isUndoSafe(GenericEventInfo eventInfo) {
        throw new UnsupportedOperationException(
            "The class " + getLoggerName() + " supports undo but has no implementation for isUndoSafe. This is a bug!");
    }

    // ---- Internals, do not touch ----

    public final void undoEvent(GenericEventInfo eventInfo, EntityPlayer player) {
        undoEventInternal(eventInfo, player);
    }

    public final void undoEvents(List<? extends GenericEventInfo> results, EntityPlayer player) {

        for (GenericEventInfo element : results) {
            undoEventInternal(element, player);
        }
    }

    public int getQueueSize() {
        return concurrentEventQueue.size();
    }
}
