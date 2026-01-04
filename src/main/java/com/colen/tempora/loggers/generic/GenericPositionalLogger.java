package com.colen.tempora.loggers.generic;

import static com.colen.tempora.Tempora.LOG;
import static com.colen.tempora.TemporaUtils.deleteLoggerDatabase;
import static com.colen.tempora.utils.GenericUtils.parseSizeStringToBytes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.colen.tempora.enums.LoggerEventType;
import net.minecraft.client.Minecraft;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sqlite.SQLiteConfig;

import com.colen.tempora.TemporaUtils;
import com.colen.tempora.enums.LoggerEnum;
import com.colen.tempora.utils.DatabaseUtils;
import com.colen.tempora.utils.GenericUtils;
import com.colen.tempora.utils.TimeUtils;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SuppressWarnings("SqlDialectInspection")
public abstract class GenericPositionalLogger<EventToLog extends GenericQueueElement> {

    public static final long SECONDS_RENDERING_DURATION = 10;

    public final PositionalLoggerDatabase db = new PositionalLoggerDatabase(this);

    private static volatile boolean running = true;

    private final LinkedBlockingQueue<EventToLog> eventQueue = new LinkedBlockingQueue<>();
    private static final Map<String, GenericPositionalLogger<?>> loggerMap = new HashMap<>();
    protected List<EventToLog> transparentEventsToRenderInWorld = new ArrayList<>();
    protected List<EventToLog> nonTransparentEventsToRenderInWorld = new ArrayList<>();


    private boolean isEnabled;


    public void addEventToRender(EventToLog event) {
        event.eventRenderCreationTime = System.currentTimeMillis();

        if (event.needsTransparencyToRender()) {
            transparentEventsToRenderInWorld.add(event);
        } else {
            nonTransparentEventsToRenderInWorld.add(event);
        }
    }

    public static @Nullable GenericPositionalLogger<?> getLogger(String playerMovementLogger) {
        return loggerMap.get(playerMovementLogger);
    }

    @SideOnly(Side.CLIENT)
    public List<EventToLog> getSortedLatestEventsByDistance(Collection<EventToLog> input, RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        int playerDim = mc.thePlayer.dimension;

        Map<String, EventToLog> latestPerBlock = new HashMap<>();

        for (EventToLog element : input) {
            if (element.dimensionId != playerDim) continue;

            String key = (int) element.x + "," + (int) element.y + "," + (int) element.z;
            EventToLog existing = latestPerBlock.get(key);

            if (existing == null || element.timestamp > existing.timestamp) {
                latestPerBlock.put(key, element);
            }
        }

        List<EventToLog> sorted = new ArrayList<>(latestPerBlock.values());
        sortByDistanceDescending(sorted, e);
        return sorted;
    }

    @SideOnly(Side.CLIENT)
    public void sortByDistanceDescending(List<EventToLog> list, RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        double px = mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * e.partialTicks;
        double py = mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * e.partialTicks;
        double pz = mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * e.partialTicks;

        list.sort((a, b) -> Double.compare(squaredDistance(b, px, py, pz), squaredDistance(a, px, py, pz)));
    }

    @SideOnly(Side.CLIENT)
    private static double squaredDistance(GenericQueueElement e, double x, double y, double z) {
        double dx = e.x - x;
        double dy = e.y - y;
        double dz = e.z - z;
        return dx * dx + dy * dy + dz * dz;
    }

    protected LogWriteSafety defaultLogWriteSafetyMode() {
        return LogWriteSafety.NORMAL;
    }

    public abstract void threadedSaveEvents(List<EventToLog> event) throws SQLException;

    public abstract @NotNull LoggerEventType getLoggerEventType();

    public abstract @NotNull List<GenericQueueElement> generateQueryResults(ResultSet rs) throws SQLException;

    public abstract LoggerEnum getLoggerType();

    public abstract void renderEventsInWorld(RenderWorldLastEvent e);

    public final String getSQLTableName() {
        return getLoggerType().toString();
    }

    // Add your own custom columns for each logger with this, we append the default x y z etc with getAllTableColumns
    public abstract List<ColumnDef> getCustomTableColumns();

    public String getLoggerName() {
        return getLoggerType().name();
    }

    public final List<ColumnDef> getAllTableColumns() {
        List<ColumnDef> columns = new ArrayList<>(getCustomTableColumns());
        columns.addAll(PositionalLoggerDatabase.getDefaultColumns());
        return columns;
    }

    public void handleCustomLoggerConfig(Configuration config) {}

    public final void registerEvent() {
        if (!getLoggerEventType().shouldRegister()) return;

        switch (getLoggerEventType()) {
            case ForgeEvent -> MinecraftForge.EVENT_BUS.register(this);
            case MinecraftEvent -> FMLCommonHandler.instance().bus().register(this);
            default -> throw new IllegalStateException(
                "Unknown LoggerEventType: " + getLoggerEventType()
            );
        }
    }

    public final void queueEvent(EventToLog event) {
        if (!isEnabled) return;
        eventQueue.offer(event); // Non-blocking, thread-safe
    }

    private void startQueueWorker(String sqlTableName) {

        running = true;

        Thread t = new Thread(() -> queueLoop(sqlTableName), "Tempora-" + sqlTableName);
        t.setDaemon(false);
        t.setUncaughtExceptionHandler((thr, ex) -> {
            LOG.error("Tempora queue-worker '{}' crashed – halting JVM!", thr.getName(), ex);
            FMLCommonHandler.instance()
                .exitJava(-1, false);
        });
        t.start();
    }

    private void queueLoop(String sqlTableName) {
        final List<EventToLog> buffer = new ArrayList<>();
        final int LARGE_QUEUE_THRESHOLD = 5_000;

        while (running || !eventQueue.isEmpty()) {
            try {
                EventToLog event = eventQueue.poll(300, TimeUnit.MILLISECONDS);
                if (event == null) continue;

                if (eventQueue.size() > LARGE_QUEUE_THRESHOLD) {
                    LOG.warn("{} has {} elements waiting…", sqlTableName, eventQueue.size());
                }

                buffer.add(event);
                eventQueue.drainTo(buffer);

                threadedSaveEvents(buffer);
                db.getDBConn().commit();
                buffer.clear();
            } catch (Exception x) {
                throw new RuntimeException("DB failure in " + sqlTableName, x);
            }
        }

        if (running) {
            throw new IllegalStateException("Queue worker terminated unexpectedly");
        }
    }

    public final void genericConfig(@NotNull Configuration config) {
        isEnabled = config.getBoolean("isEnabled", getSQLTableName(), loggerEnabledByDefault(), "Enables this logger.");

        db.genericConfig(config);
    }

    // --------------------------------------
    // Static methods
    // --------------------------------------

    public static void registerLogger(GenericPositionalLogger<?> logger) {
        loggerMap.put(logger.getSQLTableName(), logger);
    }

    public static Collection<GenericPositionalLogger<?>> getLoggerList() {
        return Collections.unmodifiableCollection(loggerMap.values());
    }

    public static List<String> getAllLoggerNames() {
        List<String> loggerNames = new ArrayList<>();

        for (GenericPositionalLogger<?> logger : getLoggerList()) {
            loggerNames.add(logger.getSQLTableName());
        }

        return loggerNames;
    }

    public static void onServerStart() {
        LOG.info("Opening Tempora databases.");

        for (GenericPositionalLogger<?> logger : getLoggerList()) {
            initialiseLogger(logger);
        }
    }

    private static void initialiseLogger(GenericPositionalLogger<?> logger) {
        String loggerName = logger.getLoggerType()
            .name();

        while (true) {
            try {
                // Clear events and initialise connection
                logger.clearEvents();
                logger.db.initDbConnection();
                Connection conn = logger.db.getDBConn();

                // Check for corruption
                if (DatabaseUtils.isDatabaseCorrupted(conn)) {

                    // Todo handle SP equivalent with UI perhaps?
                    boolean erase = GenericUtils.askTerminalYesNo(
                        "Tempora has detected db corruption in " + loggerName
                            + ". Would you like to erase the database and create a new one?");

                    if (erase) {
                        logger.db.closeDbConnection();
                        deleteLoggerDatabase(loggerName);
                        continue;
                    } else {
                        throw new RuntimeException(
                            "Tempora database " + loggerName
                                + ".db is corrupted. "
                                + "Please disable database, fix the corruption manually or delete the database "
                                + "and let Tempora generate a new clean version.");
                    }
                }

                // Normal initialisation logic
                if (logger.db.isHighRiskModeEnabled()) {
                    logger.db.enableHighRiskFastMode();
                }

                conn.setAutoCommit(false);

                logger.db.initTable();
                logger.db.createAllIndexes();
                logger.db.removeOldDatabaseData();
                logger.db.trimOversizedDatabase();

                logger.startQueueWorker(logger.getSQLTableName());

                // Success! exit loop
                break;

            } catch (SQLException e) {
                throw new RuntimeException("[Tempora] Failed to initialise database for logger " + loggerName, e);
            }
        }
    }

    public final LinkedBlockingQueue<EventToLog> getEventQueue() {
        return eventQueue;
    }

    private void clearEvents() {
        eventQueue.clear();
    }

    /* ---------- helpers ---------- */

    public static void onServerClose() {
        try {
            running = false; // Signal worker to stop

            for (GenericPositionalLogger<?> logger : getLoggerList()) {
                // Shut down each db.
                if (logger.db.getDBConn() != null && !logger.db.getDBConn().isClosed()) {
                    ;
                    logger.db.closeDbConnection();
                }
            }

        } catch (Exception e) {
            LOG.error("Error closing resources.", e);
        } finally {
            // Just to ensure that we are not carrying data over to a new world opening.
            for (GenericPositionalLogger<?> logger : getLoggerList()) {
                logger.clearEvents();
            }
        }
    }

    private boolean loggerEnabledByDefault() {
        return true;
    }

    public void clearOldEventsToRender() {
        double expiryCutoff = System.currentTimeMillis() - SECONDS_RENDERING_DURATION * 1000L;
        transparentEventsToRenderInWorld
            .removeIf(eventPosition -> eventPosition.eventRenderCreationTime < expiryCutoff);
        nonTransparentEventsToRenderInWorld
            .removeIf(eventPosition -> eventPosition.eventRenderCreationTime < expiryCutoff);
    }
}
