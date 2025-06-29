package com.colen.tempora.logging.loggers.generic;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.colen.tempora.config.Config;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.TemporaUtils;
import com.colen.tempora.utils.TimeUtils;

import cpw.mods.fml.common.FMLCommonHandler;

public abstract class GenericPositionalLogger<EventToLog extends GenericQueueElement> {

    private static final String OLDEST_DATA_DEFAULT = "4months";
    private static final int MAX_DATA_ROWS_PER_DB = 5;

    private ExecutorService executor;
    private static volatile boolean running = true;
    private Connection positionalLoggerDBConnection;

    private final LinkedBlockingQueue<EventToLog> eventQueue = new LinkedBlockingQueue<>();
    private static final Set<GenericPositionalLogger<?>> loggerList = new HashSet<>();
    private LogWriteSafety durabilityMode;

    private boolean isEnabled;
    private String oldestDataCutoff;

    public GenericPositionalLogger() {
        loggerList.add(this);
    }

    protected LogWriteSafety defaultLogWriteSafetyMode() {
        return LogWriteSafety.NORMAL;
    }

    public abstract void threadedSaveEvents(List<EventToLog> event) throws SQLException;
    protected abstract ArrayList<ISerializable> generateQueryResults(ResultSet rs) throws SQLException;
    public abstract String getSQLTableName();
    protected abstract List<ColumnDef> getTableColumns();

    public void handleCustomLoggerConfig(Configuration config) { }

    public Connection getDBConn() {
        return positionalLoggerDBConnection;
    }

    private List<ColumnDef> getDefaultColumns() {
        return Arrays.asList(
            new ColumnDef("x", "INTEGER", "NOT NULL"),
            new ColumnDef("y", "INTEGER", "NOT NULL"),
            new ColumnDef("z", "INTEGER", "NOT NULL"),
            new ColumnDef("timestamp", "DATETIME", "NOT NULL"),
            new ColumnDef("dimensionID", "INTEGER", "NOT NULL"));
    }

    private void enableHighRiskFastMode() {
        Connection conn = getDBConn();

        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA synchronous=OFF;");
            st.execute("PRAGMA wal_autocheckpoint=10000;");
        } catch (Exception e) {
            // Rethrow as unchecked to crash without compiler complaining.
            throw new RuntimeException(e);
        }
    }

    private void initTable() {
        String tableName = getSQLTableName();
        List<ColumnDef> columns = new ArrayList<>(getTableColumns());
        columns.addAll(getDefaultColumns());

        try {
            // Step 1: CREATE TABLE IF NOT EXISTS
            StringBuilder createSQL = new StringBuilder("CREATE TABLE IF NOT EXISTS ").append(tableName)
                .append(" (id INTEGER PRIMARY KEY AUTOINCREMENT");

            for (ColumnDef col : columns) {
                createSQL.append(", ")
                    .append(col.name)
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
            try (
                PreparedStatement stmt = getDBConn()
                    .prepareStatement("PRAGMA table_info(" + tableName + ");");
                ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    existingColumns.add(rs.getString("name"));
                }
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

        } catch (SQLException e) {
            e.printStackTrace();
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
            System.exit(0);
            e.printStackTrace();
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
        executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "Tempora-" + sqlTableName));

        executor.submit(() -> {
            List<EventToLog> buffer = new ArrayList<>();
            final int LARGE_QUEUE_THRESHOLD = 5000;

            try {
                while (running || !eventQueue.isEmpty()) {
                    // Wait up to FLUSH_INTERVAL_MS for an event
                    EventToLog event = eventQueue.poll(100, TimeUnit.MILLISECONDS);

                    if (event == null) {
                        continue;
                    }

                    // If the queue is busy, warn the user.
                    if (eventQueue.size() > LARGE_QUEUE_THRESHOLD) {
                        FMLLog.warning("%s has %d elements waiting to store in Tempora's Database. This may indicate the server is struggling to keep up with logging.", sqlTableName, eventQueue.size());
                    }

                    buffer.add(event);
                    eventQueue.drainTo(buffer);

                    // Flush if enough time has passed
                    if (!buffer.isEmpty()) {
                        try {
                            threadedSaveEvents(buffer);
                            getDBConn().commit();
                        } catch (Exception e) {
                            System.err.println("Batch write failed: " + e.getMessage());
                            e.printStackTrace();
                        }
                        buffer.clear();
                    }
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();  // Restore interrupt status
            }
        });
    }

    public final void genericConfig(@NotNull Configuration config) {
        isEnabled = config.getBoolean("isEnabled", getSQLTableName(), loggerEnabledByDefault(), "Enables this logger.");
        oldestDataCutoff = config.getString(
            "OldestDataCutoff",
            getSQLTableName(),
            OLDEST_DATA_DEFAULT,
            "Any records older than this relative to now, will be erased. This is unrecoverable, be careful!");


        String raw = config.get(
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
            """
        ).getString().trim().toUpperCase();

        try {
            durabilityMode = LogWriteSafety.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw new RuntimeException(
                "Invalid DurabilityMode \"" + raw + "\" in " + getSQLTableName()
                    + ". Valid values are " + LogWriteSafety.NORMAL + " or " + LogWriteSafety.HIGH_RISK + ".", ex);
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

    public static void queryEventsWithinRadiusAndTime(ICommandSender sender, int radius, long seconds,
        String tableName) {

        if (!(sender instanceof EntityPlayerMP entityPlayerMP)) return;
        int posX = entityPlayerMP.getPlayerCoordinates().posX;
        int posY = entityPlayerMP.getPlayerCoordinates().posY;
        int posZ = entityPlayerMP.getPlayerCoordinates().posZ;
        int dimensionId = entityPlayerMP.dimension;

        long pastTime = System.currentTimeMillis() - seconds * 1000; // Convert seconds to milliseconds

        synchronized (GenericPositionalLogger.class) {
            for (GenericPositionalLogger<?> logger : loggerList) {
                if (tableName != null && !logger.getSQLTableName()
                    .equals(tableName)) continue;
                try (
                    PreparedStatement pstmt = logger.getDBConn().prepareStatement(
                        "SELECT * FROM " + logger.getSQLTableName()
                            + " WHERE ABS(x - ?) <= ? AND ABS(y - ?) <= ? AND ABS(z - ?) <= ?"
                            + " AND dimensionID = ? AND timestamp >= ? ORDER BY timestamp DESC LIMIT ?")) {

                    pstmt.setDouble(1, posX);
                    pstmt.setInt(2, radius);
                    pstmt.setDouble(3, posY);
                    pstmt.setInt(4, radius);
                    pstmt.setDouble(5, posZ);
                    pstmt.setInt(6, radius);
                    pstmt.setInt(7, dimensionId);
                    pstmt.setTimestamp(8, new Timestamp(pastTime)); // Filter events from pastTime onwards
                    pstmt.setInt(9, MAX_DATA_ROWS_PER_DB);

                    // Try send the result to the player.
                    try (ResultSet rs = pstmt.executeQuery()) {
                        List<ISerializable> packets = logger.generateQueryResults(rs);

                        if (packets.isEmpty()) {
                            sender.addChatMessage(new ChatComponentText(
                                EnumChatFormatting.GRAY + "No results found for " + logger.getSQLTableName() + '.'));
                            return;
                        } else {
                            sender.addChatMessage(new ChatComponentText(
                                EnumChatFormatting.GRAY + "Showing latest " + packets.size() + " results for " + logger.getSQLTableName() + ':'));
                        }

                        if (logger.eventQueue.size() > 100) {
                            sender.addChatMessage(new ChatComponentText(
                                EnumChatFormatting.RED + "Warning, due to high volume, there are still " + logger.eventQueue.size() + " events pending, query results may be outdated/inaccurate."));
                        }

                        String uuid = entityPlayerMP.getUniqueID().toString();
                        packets.forEach(p -> entityPlayerMP.addChatMessage(p.localiseText(uuid)));
                    }

                } catch (SQLException e) {
                    sender.addChatMessage(
                        new ChatComponentText(
                            "Database query failed on " + logger.getSQLTableName() + ": " + e.getLocalizedMessage()));
                }
            }
        }
    }

    public static void onServerStart() {
        try {
            System.out.println("Opening Tempora DBs...");

            for (GenericPositionalLogger<?> logger : loggerList) {
                String dbUrl = TemporaUtils.jdbcUrl(logger.getSQLTableName() + ".db");
                logger.positionalLoggerDBConnection = DriverManager.getConnection(dbUrl);

                if (logger.isHighRiskModeEnabled()) {
                    logger.enableHighRiskFastMode();
                }

                // Enable batching, to reduce overhead on db writes.
                logger.getDBConn().setAutoCommit(false);

                logger.initTable();
                logger.createAllIndexes();
                logger.removeOldDatabaseData();

                logger.startQueueWorker(logger.getSQLTableName());
            }

        } catch (SQLException sqlException) {
            System.err.println("Could not open Tempora databases.");
            sqlException.printStackTrace();
        }
    }

    public static void onServerClose() {
        try {
            running = false; // Signal worker to stop

            for (GenericPositionalLogger<?> logger : loggerList) {
                // Shut down each executor.
                logger.shutdownExecutor();

                // Shut down each db.
                if (logger.getDBConn() != null && !logger.getDBConn().isClosed()) {
                    logger.getDBConn().close();
                }
            }

        } catch (Exception e) {
            System.err.println("Error closing resources:");
            e.printStackTrace();
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


    private void createAllIndexes() {

        try (Statement stmt = getDBConn().createStatement()) {

            String tableName = getSQLTableName();

            // Creating a composite index for x, y, z, dimensionID and timestamp
            String createCompositeIndex = String.format(
                "CREATE INDEX IF NOT EXISTS idx_%s_xyz_dimension_time ON %s (x, y, z, dimensionID, timestamp DESC);",
                tableName,
                tableName);
            stmt.execute(createCompositeIndex);

            // Creating an index for timestamp alone to optimize for queries primarily sorting or filtering on
            // timestamp
            String createTimestampIndex = String.format(
                "CREATE INDEX IF NOT EXISTS idx_%s_timestamp ON %s (timestamp DESC);",
                tableName,
                tableName);
            stmt.execute(createTimestampIndex);

            System.out.println("Indexes created for table: " + tableName);
        } catch (SQLException e) {
            System.err.println("Error creating indexes: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Use the fastest durability mode: may lose or corrupt the DB on sudden power loss.
    // Only recommended if recent data loss is acceptable and backups exist.
    public final boolean isHighRiskModeEnabled() {
        return durabilityMode == LogWriteSafety.HIGH_RISK;
    }

    private boolean loggerEnabledByDefault() {
        return true;
    }

}
