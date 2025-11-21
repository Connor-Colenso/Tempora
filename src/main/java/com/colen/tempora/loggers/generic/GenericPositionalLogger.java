package com.colen.tempora.loggers.generic;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

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
import org.sqlite.SQLiteConfig;

import com.colen.tempora.TemporaUtils;
import com.colen.tempora.config.Config;
import com.colen.tempora.enums.LoggerEnum;
import com.colen.tempora.utils.TimeUtils;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public abstract class GenericPositionalLogger<EventToLog extends GenericQueueElement> {

    private static final String OLDEST_DATA_DEFAULT = "4months";
    private static final int MAX_DATA_ROWS_PER_DB = 5;
    public static final long SECONDS_RENDERING_DURATION = 10;

    private ExecutorService executor;
    private static volatile boolean running = true;
    private Connection positionalLoggerDBConnection;

    private final LinkedBlockingQueue<EventToLog> eventQueue = new LinkedBlockingQueue<>();
    private static final Set<GenericPositionalLogger<?>> loggerList = new HashSet<>();
    protected List<EventToLog> eventsToRenderInWorld = new ArrayList<>();
    private LogWriteSafety durabilityMode;

    private boolean isEnabled;
    private String oldestDataCutoff;
    private long largestDatabaseSizeInBytes;

    public GenericPositionalLogger() {
        loggerList.add(this);
    }

    public void addEventToRender(EventToLog event) {
        event.eventRenderCreationTime = System.currentTimeMillis();
        eventsToRenderInWorld.add(event);
    }

    public static GenericPositionalLogger<?> getLogger(String playerMovementLogger) {
        for (GenericPositionalLogger<?> logger : loggerList) {
            if (playerMovementLogger.equals(logger.getSQLTableName())) {
                return logger;
            }
        }

        return null;
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

    public abstract List<GenericQueueElement> generateQueryResults(ResultSet rs) throws SQLException;

    public abstract LoggerEnum getLoggerType();

    public abstract void renderEventsInWorld(RenderWorldLastEvent e);

    public final String getSQLTableName() {
        return getLoggerType().toString();
    }

    // Add your own custom columns for each logger with this, we append the default x y z etc with getAllTableColumns
    public abstract List<ColumnDef> getCustomTableColumns();

    public final List<ColumnDef> getAllTableColumns() {
        List<ColumnDef> columns = new ArrayList<>(getCustomTableColumns());
        columns.addAll(getDefaultColumns());
        return columns;
    }

    public void handleCustomLoggerConfig(Configuration config) {}

    public Connection getDBConn() {
        return positionalLoggerDBConnection;
    }

    // These should really, never be missing, so even though the defaults are a bit clunky, it's alright.
    public static List<ColumnDef> getDefaultColumns() {
        return Arrays.asList(
            new ColumnDef("eventID", "TEXT", "PRIMARY KEY"),
            new ColumnDef("x", "INTEGER", "NOT NULL DEFAULT " + Integer.MIN_VALUE),
            new ColumnDef("y", "INTEGER", "NOT NULL DEFAULT " + Integer.MIN_VALUE),
            new ColumnDef("z", "INTEGER", "NOT NULL DEFAULT " + Integer.MIN_VALUE),
            new ColumnDef("timestamp", "DATETIME", "NOT NULL DEFAULT 0"),
            new ColumnDef("dimensionID", "INTEGER", "NOT NULL DEFAULT " + Integer.MIN_VALUE));
    }

    private void enableHighRiskFastMode() throws SQLException {
        Connection conn = getDBConn();

        Statement st = conn.createStatement();
        st.execute("PRAGMA synchronous=OFF;");
        st.execute("PRAGMA wal_autocheckpoint=10000;");
    }

    private void initDbConnection() throws SQLException {
        String dbUrl = TemporaUtils.jdbcUrl(getSQLTableName() + ".db");
        positionalLoggerDBConnection = DriverManager.getConnection(dbUrl);
    }

    private void initTable() throws SQLException {
        String tableName = getSQLTableName();
        List<ColumnDef> columns = getAllTableColumns();

        // Step 1: CREATE TABLE IF NOT EXISTS
        StringBuilder createSQL = new StringBuilder("CREATE TABLE IF NOT EXISTS ").append(tableName)
            .append(" (");

        for (int i = 0; i < columns.size(); i++) {
            ColumnDef col = columns.get(i);
            if (i > 0) createSQL.append(", ");
            createSQL.append(col.name)
                .append(" ")
                .append(col.type);
            if (col.extraCondition != null && !col.extraCondition.isEmpty()) {
                createSQL.append(" ")
                    .append(col.extraCondition);
            }
        }

        createSQL.append(");");

        getDBConn().prepareStatement(createSQL.toString())
            .execute();

        // Step 2: Check existing columns
        Set<String> existingColumns = new HashSet<>();
        PreparedStatement stmt = getDBConn().prepareStatement("PRAGMA table_info(" + tableName + ");");
        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
            existingColumns.add(rs.getString("name"));
        }

        // Step 3: ALTER TABLE to add missing columns
        for (ColumnDef col : columns) {
            if (!existingColumns.contains(col.name)) {
                StringBuilder alterSQL = new StringBuilder("ALTER TABLE ").append(tableName)
                    .append(" ADD COLUMN ")
                    .append(col.name)
                    .append(" ")
                    .append(col.type);

                if (col.extraCondition != null && !col.extraCondition.isEmpty()) {
                    alterSQL.append(" ")
                        .append(col.extraCondition);
                }

                alterSQL.append(";");
                getDBConn().prepareStatement(alterSQL.toString())
                    .execute();
            }
        }
    }

    // This is not strictly thread safe but since we are doing this before the server has even started properly
    // nothing else is interacting with the db, so it's fine for now.
    private void eraseAllDataBeforeTime(long time) {
        // Prepare SQL statement with the safe table name
        String sql = "DELETE FROM " + getSQLTableName() + " WHERE timestamp < ?";

        try (PreparedStatement pstmt = getDBConn().prepareStatement(sql)) {
            // Set the parameter for the PreparedStatement
            pstmt.setLong(1, time);

            // Execute the update
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("SQL error could not erase old data: " + e.getMessage());
        }
    }

    private void removeOldDatabaseData() {
        try {
            eraseAllDataBeforeTime(System.currentTimeMillis() - TimeUtils.convertToSeconds(oldestDataCutoff) * 1000);
        } catch (Exception e) {
            System.err.println(
                "An error occurred while erasing old data in " + getSQLTableName()
                    + " are you sure you spelt the oldest data setting correctly ("
                    + oldestDataCutoff
                    + ")? Check your tempora config.");

            e.printStackTrace();
            throw new RuntimeException("Database cleanup failed");
        }
    }

    public final void registerEvent() {
        // Lazy but genuinely not sure how else to approach this generically without a big switch list.

        MinecraftForge.EVENT_BUS.register(this);

        FMLCommonHandler.instance()
            .bus()
            .register(this);
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
            FMLLog.severe("Tempora queue‑worker '%s' crashed – halting JVM!", thr.getName());
            ex.printStackTrace();
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
                    FMLLog.warning("%s has %,d elements waiting…", sqlTableName, eventQueue.size());
                }

                buffer.add(event);
                eventQueue.drainTo(buffer);

                threadedSaveEvents(buffer);
                getDBConn().commit();
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
        oldestDataCutoff = config.getString(
            "OldestDataCutoff",
            getSQLTableName(),
            OLDEST_DATA_DEFAULT,
            "Any records older than this relative to now, will be erased. This is unrecoverable, be careful!");

        String raw = config
            .get(
                getSQLTableName(),
                "LogWriteSafety",
                defaultLogWriteSafetyMode().name(),
                """
                    NORMAL – Safer, but slower
                      • Best for long-term stability.
                      • Every log is saved to disk right away, so even if your server crashes or the power goes out, your logs will be intact.
                      • Slightly slower performance—may reduce TPS during heavy activity like world edits or explosions.

                    HIGH_RISK – Much faster, but riskier
                      • Boosts performance by delaying how often logs are saved to disk.
                      • Helps maintain TPS during intense events (e.g., TNT, worldedit, busy modpacks).
                      • WARNING: if your server crashes or shuts down suddenly, the last few seconds of logs may be lost or corrupted. This does **not** affect your world—only the logs.
                      • Only recommended if you make regular backups or can afford to lose a few seconds of log data.

                    Tip: Start with HIGH_RISK if you're concerned about performance (there will be warnings in the log if Tempora is struggling to keep up).
                         If you need 100% reliable logging, switch to NORMAL once you're happy with how the server runs.
                    """)
            .getString()
            .trim()
            .toUpperCase();

        try {
            durabilityMode = LogWriteSafety.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw new RuntimeException(
                "Invalid DurabilityMode \"" + raw
                    + "\" in "
                    + getSQLTableName()
                    + ". Valid values are "
                    + LogWriteSafety.NORMAL
                    + " or "
                    + LogWriteSafety.HIGH_RISK
                    + ".",
                ex);
        }

        // Database too big handling.
        final String maxDbSizeString = config.getString(
            "MaxDatabaseSize",
            getSQLTableName(),
            "-1",
            "Approximate maximum database file size (e.g. '500KB', '1MB', '5GB'). By default this is set to -1, meaning no erasure happens.");

        if (maxDbSizeString.equals("-1")) {
            largestDatabaseSizeInBytes = Long.MAX_VALUE;
        } else {
            largestDatabaseSizeInBytes = parseSizeStringToBytes(maxDbSizeString);
        }

    }

    // --------------------------------------
    // Static methods
    // --------------------------------------

    public static void registerLogger(GenericPositionalLogger<?> logger) {
        loggerList.add(logger);
    }

    public static Set<GenericPositionalLogger<?>> getLoggerList() {
        return Collections.unmodifiableSet(loggerList);
    }

    public static List<String> getAllLoggerNames() {
        List<String> loggerNames = new ArrayList<>();

        for (GenericPositionalLogger<?> logger : loggerList) {
            loggerNames.add(logger.getSQLTableName());
        }

        return loggerNames;
    }

    // -1 passed into seconds, equates to, find any event that occurred here, no matter how long ago.
    public static void queryEventsAtPosAndTime(ICommandSender sender, int centreX, int centreY, int centreZ,
        long seconds, String tableName) {
        if (!(sender instanceof EntityPlayerMP player)) return;

        // radius 0 means an exact match – the SQL below
        // (ABS(x - ?) <= radius) degenerates to x == ?, y == ?, z == ?
        queryEventByCoordinate(sender, centreX, centreY, centreZ, 0, seconds, tableName, player.dimension);
    }

    public static void queryEventByCoordinate(ICommandSender sender, int centreX, int centreY, int centreZ, int radius,
        long seconds, String tableName, int dimensionId) {

        synchronized (GenericPositionalLogger.class) {
            for (GenericPositionalLogger<?> logger : loggerList) {
                if (tableName != null && !logger.getSQLTableName()
                    .equals(tableName)) continue;

                String sql = "SELECT * FROM " + logger.getSQLTableName()
                    + " WHERE ABS(x - ?) <= ?  AND ABS(y - ?) <= ?  AND ABS(z - ?) <= ? "
                    + "   AND dimensionID = ? AND timestamp >= ? "
                    + " ORDER BY timestamp DESC LIMIT ?";

                try (PreparedStatement ps = logger.getReadOnlyConnection()
                    .prepareStatement(sql)) {

                    ps.setInt(1, centreX);
                    ps.setInt(2, radius);
                    ps.setInt(3, centreY);
                    ps.setInt(4, radius);
                    ps.setInt(5, centreZ);
                    ps.setInt(6, radius);

                    ps.setInt(7, dimensionId);

                    if (seconds < 0) {
                        // Don’t apply any timestamp filter, fetch everything.
                        ps.setTimestamp(8, new Timestamp(0));
                    } else {
                        ps.setTimestamp(8, new Timestamp(System.currentTimeMillis() - seconds * 1000L));
                    }

                    ps.setInt(9, MAX_DATA_ROWS_PER_DB);

                    try (ResultSet rs = ps.executeQuery()) {
                        List<GenericQueueElement> packets = logger.generateQueryResults(rs);

                        if (packets.isEmpty()) {
                            IChatComponent noResults = new ChatComponentTranslation(
                                "message.queryevents.no_results",
                                logger.getSQLTableName());
                            noResults.getChatStyle()
                                .setColor(EnumChatFormatting.GRAY);
                            sender.addChatMessage(noResults);

                        } else {
                            IChatComponent showingResults = new ChatComponentTranslation(
                                "message.queryevents.showing_results",
                                packets.size(),
                                logger.getSQLTableName());
                            showingResults.getChatStyle()
                                .setColor(EnumChatFormatting.GRAY);
                            sender.addChatMessage(showingResults);
                            if (logger.eventQueue.size() > 100) {
                                IChatComponent tooMany = new ChatComponentTranslation(
                                    "message.queryevents.too_many_pending",
                                    logger.eventQueue.size());
                                tooMany.getChatStyle()
                                    .setColor(EnumChatFormatting.RED);
                                sender.addChatMessage(tooMany);
                            }

                            // EntityPlayerMP specific stuff, like sending animation positions to user and the text
                            // itself.
                            EntityPlayerMP player = (EntityPlayerMP) sender;

                            // Do not remove!
                            Collections.reverse(packets);

                            String uuid = player.getUniqueID()
                                .toString();
                            packets.forEach(p -> sender.addChatMessage(p.localiseText(uuid)));

                            // This tells the client what to render in world, as it needs this info.
                            for (GenericQueueElement packet : packets) {
                                packet.sendEventToClientForRendering(player);
                            }
                        }
                    }
                } catch (SQLException e) {
                    sender.addChatMessage(
                        new ChatComponentTranslation(
                            "message.queryevents.query_failed",
                            logger.getSQLTableName(),
                            e.getMessage()));

                }
            }
        }
    }

    public EventToLog queryEventByEventID(String eventID) {

        synchronized (GenericPositionalLogger.class) {

            String sql = "SELECT * FROM " + getSQLTableName() + " WHERE eventID == ? LIMIT 1";

            try (PreparedStatement ps = getReadOnlyConnection().prepareStatement(sql)) {

                ps.setString(1, eventID);

                ResultSet rs = ps.executeQuery();
                List<GenericQueueElement> packets = generateQueryResults(rs);
                if (packets.isEmpty()) return null;

                return (EventToLog) packets.get(0);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    public static void onServerStart() {
        System.out.println("Opening Tempora DBs...");

        for (GenericPositionalLogger<?> logger : loggerList) {
            try {
                // Just to 100% ensure we are not getting events from a prior save.
                logger.clearEvents();

                logger.initDbConnection();

                if (logger.isHighRiskModeEnabled()) {
                    logger.enableHighRiskFastMode();
                }

                // Enable batching, to reduce overhead on db writes.
                logger.getDBConn()
                    .setAutoCommit(false);

                logger.initTable();
                logger.createAllIndexes();
                logger.removeOldDatabaseData();
                logger.trimOversizedDatabase();

                logger.startQueueWorker(logger.getSQLTableName());
            } catch (SQLException sqlException) {
                String loggerName = logger.getClass()
                    .getSimpleName();
                System.err.println("Failed to initialize logger: " + loggerName);
                sqlException.printStackTrace();
                throw new RuntimeException(
                    "Tempora database initial stages failure for logger: " + loggerName,
                    sqlException);
            }
        }
    }

    private void clearEvents() {
        eventQueue.clear();
    }

    private void trimOversizedDatabase() throws SQLException {
        Path dbPath = TemporaUtils.databaseDir()
            .resolve(getSQLTableName() + ".db");
        if (!Files.exists(dbPath)) {
            throw new IllegalStateException("Database file not found: " + dbPath);
        }

        // Do this first, to prevent fragmentation.
        checkpointAndVacuum();

        Connection conn = getDBConn();

        long usedBytes = physicalDbBytes(conn);
        if (usedBytes <= largestDatabaseSizeInBytes) {
            conn.commit();
            return;
        }

        long totalRows = countRows(conn);
        if (totalRows == 0) {
            conn.commit();
            return;
        }

        // Calculate how many rows to delete to get under limit
        double overshoot = (double) usedBytes / largestDatabaseSizeInBytes;
        long rowsToDelete = Math.max(1, (long) Math.ceil(totalRows * (overshoot - 1) / overshoot));

        String sql = "DELETE FROM " + getSQLTableName()
            + " WHERE rowid IN (SELECT rowid FROM "
            + getSQLTableName()
            + " ORDER BY timestamp ASC LIMIT ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, rowsToDelete);
            int deleted = ps.executeUpdate();
            System.out.printf("[Tempora] Deleted %,d rows from %s%n", deleted, getSQLTableName());
        }

        conn.commit();

        checkpointAndVacuum();

        System.out.printf(
            "[Tempora] %s DB is now %.2f MB (limit %.2f MB)%n",
            getSQLTableName(),
            physicalDbBytes(conn) / 1_048_576.0,
            largestDatabaseSizeInBytes / 1_048_576.0);
    }

    /* ---------- helpers ---------- */

    private static long physicalDbBytes(Connection c) throws SQLException {
        long pageSize = pragmaLong(c, "page_size");
        long pageCount = pragmaLong(c, "page_count");
        return pageSize * pageCount;
    }

    private long countRows(Connection c) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + getSQLTableName();
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    private static long pragmaLong(Connection c, String pragma) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("PRAGMA " + pragma)) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    private void checkpointAndVacuum() throws SQLException {
        try (Statement st = getDBConn().createStatement()) {
            getDBConn().commit();
            getDBConn().setAutoCommit(true);
            st.execute("PRAGMA wal_checkpoint(TRUNCATE)");
            st.execute("VACUUM");
            getDBConn().setAutoCommit(false);
        }
    }

    public static void onServerClose() {
        try {
            running = false; // Signal worker to stop

            for (GenericPositionalLogger<?> logger : loggerList) {
                // Shut down each executor.
                logger.shutdownExecutor();

                // Shut down each db.
                if (logger.getDBConn() != null && !logger.getDBConn()
                    .isClosed()) {
                    logger.getDBConn()
                        .close();
                }
            }

        } catch (Exception e) {
            System.err.println("Error closing resources:");
            e.printStackTrace();
        } finally {
            // Just to ensure that we are not carrying data over to a new world opening.
            for (GenericPositionalLogger<?> logger : loggerList) {
                logger.clearEvents();
            }
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void shutdownExecutor() throws InterruptedException {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();

            if (Config.shouldTemporaAlwaysWait) {
                // Wait indefinitely until tasks finish.
                executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            } else {
                // Wait up to 10 seconds, then force shutdown if needed.
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    System.err.println("Executor timeout. Forcing shutdown.");
                    executor.shutdownNow();
                }
            }
        }
    }

    private void createAllIndexes() throws SQLException {

        Statement stmt = getDBConn().createStatement();

        String tableName = getSQLTableName();

        // Creating a composite index for x, y, z, dimensionID and timestamp
        String createCompositeIndex = String.format(
            "CREATE INDEX IF NOT EXISTS idx_%s_xyz_dimension_time ON %s (x, y, z, dimensionID, timestamp DESC);",
            tableName,
            tableName);
        stmt.execute(createCompositeIndex);

        // Creating an index for timestamp alone to optimize for queries primarily sorting or filtering on
        // timestamp
        String createTimestampIndex = String
            .format("CREATE INDEX IF NOT EXISTS idx_%s_timestamp ON %s (timestamp DESC);", tableName, tableName);
        stmt.execute(createTimestampIndex);

        System.out.println("Indexes created for table: " + tableName);

    }

    // Use the fastest durability mode: may lose or corrupt the DB on sudden power loss.
    // Only recommended if recent data loss is acceptable and backups exist.
    public final boolean isHighRiskModeEnabled() {
        return durabilityMode == LogWriteSafety.HIGH_RISK;
    }

    private boolean loggerEnabledByDefault() {
        return true;
    }

    public Connection getReadOnlyConnection() {
        try {
            String dbUrl = TemporaUtils.jdbcUrl(getSQLTableName() + ".db");

            SQLiteConfig config = new SQLiteConfig();
            config.setReadOnly(true);

            Connection conn = DriverManager.getConnection(dbUrl, config.toProperties());
            conn.setReadOnly(true);

            return conn;
        } catch (Exception e) {
            return null;
        }
    }

    public void clearOldEventsToRender() {
        double expiryCutoff = System.currentTimeMillis() - SECONDS_RENDERING_DURATION * 1000L;
        eventsToRenderInWorld.removeIf(eventPosition -> eventPosition.eventRenderCreationTime < expiryCutoff);
    }
}
