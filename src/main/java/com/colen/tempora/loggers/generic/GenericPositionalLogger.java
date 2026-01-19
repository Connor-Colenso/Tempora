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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.colen.tempora.loggers.generic.undo.UndoEventInfo;
import com.colen.tempora.loggers.generic.undo.UndoResponse;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.TemporaLoggerManager;
import com.colen.tempora.enums.LoggerEventType;
import com.colen.tempora.loggers.generic.column.ColumnDef;
import com.colen.tempora.utils.ChatUtils;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SuppressWarnings("SqlDialectInspection")
public abstract class GenericPositionalLogger<EventInfo extends GenericEventInfo> {

    protected PositionalLoggerDatabase databaseManager = new PositionalLoggerDatabase(this);

    private final LinkedBlockingQueue<EventInfo> concurrentEventQueue = new LinkedBlockingQueue<>();
    protected List<EventInfo> transparentEventsToRenderInWorld = new ArrayList<>();
    protected List<EventInfo> nonTransparentEventsToRenderInWorld = new ArrayList<>();
    private static final long SECONDS_RENDERING_DURATION = 10;

    protected boolean isLoggerEnabled;
    private Thread queueWorkerThread;
    private int maxShutdownTimeoutMilliseconds;
    private static final int queuePollTimeoutMilliseconds = 100;
    private static final int LARGE_QUEUE_THRESHOLD = 5_000;

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

        // Non-blocking, thread-safe
        concurrentEventQueue.offer(eventInfo);
    }

    private void startQueueWorker() {

        queueWorkerThread = new Thread(() -> queueLoop(), "Tempora-" + getLoggerName());
        queueWorkerThread.setDaemon(false);
        queueWorkerThread.setUncaughtExceptionHandler((thr, ex) -> {
            LOG.error("Tempora queue-worker '{}' crashed – halting JVM!", thr.getName(), ex);
            FMLCommonHandler.instance()
                .exitJava(-1, false);
        });
        queueWorkerThread.start();
    }

    private void queueLoop() {
        List<EventInfo> buffer = new ArrayList<>();
        boolean sawPoisonPill = false;

        try {
            while (true) {
                // Block for an event, but wake periodically to re-check state
                EventInfo event = concurrentEventQueue.poll(queuePollTimeoutMilliseconds, TimeUnit.MILLISECONDS);

                if (event == null) {
                    // No event received
                    if (sawPoisonPill && concurrentEventQueue.isEmpty()) {
                        break; // Clean shutdown: poison seen + fully drained
                    }
                    continue;
                }

                if (event.poisonPill) {
                    sawPoisonPill = true;
                    continue; // Do NOT break, finish draining if possible.
                }

                // Normal event processing
                buffer.add(event);
                concurrentEventQueue.drainTo(buffer);

                if (concurrentEventQueue.size() > LARGE_QUEUE_THRESHOLD) {
                    LOG.warn(
                        "{} has {} events pending, possible slowdown.",
                        getLoggerName(),
                        concurrentEventQueue.size());
                }

                databaseManager.insertBatch(buffer);
                databaseManager.getDBConn()
                    .commit();
                buffer.clear();
            }
        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
            LOG.error(
                "Queue worker interrupted for {} from external manager. The eventQueue was discarded with {} events remaining.",
                getLoggerName(),
                concurrentEventQueue.size());
        } catch (Exception e) {
            throw new RuntimeException("DB failure in " + getLoggerName(), e);
        }

        // If we exit without having seen a poison pill, something is wrong
        if (!sawPoisonPill) {
            throw new IllegalStateException("Queue worker terminated unexpectedly.");
        }
    }

    public final void genericConfig(@NotNull Configuration config) {
        isLoggerEnabled = config.getBoolean("isEnabled", getLoggerName(), true, "Enables this loggers functionality.");
        maxShutdownTimeoutMilliseconds = config.getInt(
            "maxShutdownTimeoutMs",
            getLoggerName(),
            queuePollTimeoutMilliseconds * 5,
            0,
            Integer.MAX_VALUE,
            "Maximum time (in milliseconds) to wait for this logger to flush and stop during shutdown. Use 0 to wait forever until all queued events are written.");
        databaseManager.genericConfig(config);
    }

    // Run on server startup/world load.
    private void initialiseLogger() {
        try {
            databaseManager = new PositionalLoggerDatabase(this);
            databaseManager.initialiseDatabase();
            startQueueWorker();

        } catch (SQLException e) {
            throw new RuntimeException("[Tempora] Failed to initialise database for logger " + getLoggerName(), e);
        }
    }

    public final LinkedBlockingQueue<EventInfo> getConcurrentEventQueue() {
        return concurrentEventQueue;
    }

    private void clearEvents() {
        concurrentEventQueue.clear();
    }

    // --------------------------------------
    // Static methods
    // --------------------------------------

    public static void onServerStart() {
        LOG.info("Opening Tempora databases.");

        for (GenericPositionalLogger<?> logger : TemporaLoggerManager.getLoggerList()) {
            if (logger.isLoggerEnabled) logger.initialiseLogger();
        }
    }

    public static void onServerClose() {
        try {
            // Poison pill each logger, to prepare for shutdown, instead of waiting on each one sequentially.
            for (GenericPositionalLogger<?> logger : TemporaLoggerManager.getLoggerList()) {
                logger.poisonPillQueue();
            }

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

    private void poisonPillQueue() {
        EventInfo poisonedEventInfo = newEventInfo();
        poisonedEventInfo.poisonPill = true;
        concurrentEventQueue.add(poisonedEventInfo);
    }

    private void shutdownQueueWorkerThreads() throws InterruptedException {
        // Wait to shutdown, should be near instant if queue is low.
        queueWorkerThread.join(maxShutdownTimeoutMilliseconds);

        if (queueWorkerThread.isAlive()) {
            queueWorkerThread.interrupt();
            LOG.error(
                "Queue worker {} for logger {} did not stop in time. Tempora discarded {} events.",
                queueWorkerThread.getName(),
                getLoggerName(),
                concurrentEventQueue.size());
            clearEvents();
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

    // If you enable undo support via isUndoEnabled override, then you MUST also override this and implement its logic.
    protected UndoEventInfo undoEvent(GenericEventInfo eventInfo, EntityPlayer player) {
        throw new UnsupportedOperationException(
            "The class " + getLoggerName() + " supports undo but has no implementation. This is a bug!");
    }

    // ---- Internals, do not touch ----
    public final UndoEventInfo undoEvents(GenericEventInfo eventInfo, EntityPlayer player) {
        return undoEvent(eventInfo, player);
    }

    public final List<UndoEventInfo> undoEvents(List<? extends GenericEventInfo> results, EntityPlayer player) {
        List<UndoEventInfo> undoResponse = new ArrayList<>(results.size());

        for (GenericEventInfo element : results) {
            try {
                UndoEventInfo response;
                if (element.versionID != ModpackVersionData.CURRENT_VERSION) {
                    response = new UndoEventInfo();
                    response.state = UndoResponse.VERSION_MISMATCH;
                    IChatComponent uuid = ChatUtils.createHoverableClickable("[UUID]", element.eventID);
                    uuid.getChatStyle().setColor(EnumChatFormatting.AQUA);

                    response.message = new ChatComponentTranslation("tempora.undo.version_mismatch", uuid);
                } else {
                    response = undoEvent(element, player);
                }

                // Strict validation of third-party implementations.
                if (response == null) {
                    throw new IllegalStateException(
                        "undoEvent returned null for" + getLoggerName() + " and eventID " + element.eventID);
                }

                if (response.state == null) {
                    throw new IllegalStateException(
                        "UndoResponse.success was null for " + getLoggerName() + " and eventID " + element.eventID);
                }

                if (response.message == null) {
                    throw new IllegalStateException(
                        "Failure UndoResponse had no message for " + getLoggerName()
                            + " and eventID "
                            + element.eventID);
                }

                undoResponse.add(response);

            } catch (UnsupportedOperationException e) {
                // Abort the entire undo operation, undoEvent has not been implemented, but was called.
                throw e;

            } catch (Throwable t) {
                // Any other throwable is a bad logger implementation.
                LOG.error(
                    "Logger {} failed during undo for event {}. This is a logger implementation error.",
                    getLoggerName(),
                    element.eventID,
                    t);

                // Something gone wrong with the undo implementation. This may not be Tempora's fault, depending on the
                // origin of this logger.

                IChatComponent errorMsg = new ChatComponentTranslation(
                    "tempora.undo.bad_implementation",
                    getLoggerName(),
                    ChatUtils.createHoverableClickable("[UUID]", element.eventID));
                errorMsg.getChatStyle()
                    .setColor(EnumChatFormatting.RED);

                UndoEventInfo undoEventInfo = new UndoEventInfo();
                undoEventInfo.message = errorMsg;
                undoEventInfo.state = UndoResponse.ERROR;

                undoResponse.add(undoEventInfo);
            }
        }

        return undoResponse;
    }

}
