package com.colen.tempora.logging.loggers.generic;

import static com.colen.tempora.Tempora.NETWORK;
import static java.lang.Math.min;

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

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.TemporaUtils;
import com.colen.tempora.utils.TimeUtils;

import cpw.mods.fml.common.FMLCommonHandler;

public abstract class GenericPositionalLogger<EventToLog extends GenericQueueElement> {

    private static final String OLDEST_DATA_DEFAULT = "4months";
    protected static final int MAX_DATA_ROWS_PER_PACKET = 5;

    private static ExecutorService executor;
    private static volatile boolean running = true;
    protected Connection positionalLoggerDBConnection;

    private final LinkedBlockingQueue<EventToLog> eventQueue = new LinkedBlockingQueue<>();
    private static final Set<GenericPositionalLogger<?>> loggerList = new HashSet<>();

    private boolean isEnabled;
    private String oldestDataCutoff;

    public GenericPositionalLogger() {
        loggerList.add(this);
    }

    public abstract void threadedSaveEvents(List<EventToLog> event) throws SQLException;

    public void handleCustomLoggerConfig(Configuration config) {

    }

    public Connection getDBConn() {
        return positionalLoggerDBConnection;
    }

    protected abstract List<ColumnDef> getTableColumns();

    private List<ColumnDef> getDefaultColumns() {
        return Arrays.asList(
            new ColumnDef("x", "INTEGER", "NOT NULL"),
            new ColumnDef("y", "INTEGER", "NOT NULL"),
            new ColumnDef("z", "INTEGER", "NOT NULL"),
            new ColumnDef("timestamp", "DATETIME", "NOT NULL"),
            new ColumnDef("dimensionID", "INTEGER", "NOT NULL"));
    }

    public void initTable() {
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

    protected abstract ArrayList<ISerializable> generatePacket(ResultSet rs) throws SQLException;

    // This is not strictly thread safe but since we are doing this before the server has even started properly
    // nothing else is interacting with the db, so it's fine for now.
    private void eraseAllDataBeforeTime(long time) {
        // Prepare SQL statement with the safe table name
        String sql = "DELETE FROM " + this.getSQLTableName() + " WHERE timestamp < ?";

        try (PreparedStatement pstmt = getNewConnection().prepareStatement(sql)) {
            // Set the parameter for the PreparedStatement
            pstmt.setLong(1, time);

            // Execute the update
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("SQL error could not erase old data: " + e.getMessage());
        }
    }

    public final void removeOldDatabaseData() {
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

    private void startQueueWorker() {
        executor.submit(() -> {
            List<EventToLog> buffer = new ArrayList<>();
            final int FLUSH_INTERVAL_MS = 100;

            try {
                while (running || !eventQueue.isEmpty()) {
                    // Wait up to FLUSH_INTERVAL_MS for an event
                    EventToLog event = eventQueue.poll(FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);

                    if (event != null) {
                        buffer.add(event);
                    }

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


    public abstract String getSQLTableName();

    public final void genericConfig(@NotNull Configuration config) {
        isEnabled = config.getBoolean("isEnabled", getSQLTableName(), loggerEnabledByDefault(), "Enables this logger.");
        oldestDataCutoff = config.getString(
            "OldestDataCutoff",
            getSQLTableName(),
            OLDEST_DATA_DEFAULT,
            "Any records older than this relative to now, will be erased. This is unrecoverable, be careful!");
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
                    Connection conn = DriverManager
                        .getConnection(TemporaUtils.databaseDirectory() + "PositionalLogger.db");
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
                    pstmt.setInt(9, MAX_DATA_ROWS_PER_PACKET);

                    // Execute and submit to client via a custom packet if not empty.
                    try (ResultSet rs = pstmt.executeQuery()) {
                        ArrayList<ISerializable> sendList = logger.generatePacket(rs);
                        if (!sendList.isEmpty()) {
                            for (ISerializable packet : sendList) {
                                entityPlayerMP.addChatMessage(packet.localiseText(entityPlayerMP.getUniqueID().toString()));
                            }
                        } else {
                            sender.addChatMessage(
                                new ChatComponentText("No results found for " + logger.getSQLTableName() + "."));
                        }
                    }
                } catch (SQLException e) {
                    sender.addChatMessage(
                        new ChatComponentText(
                            "Database query failed on " + logger.getSQLTableName() + ": " + e.getLocalizedMessage()));
                }
            }
        }
    }

    private static Connection getNewConnection() {
        try {
            return DriverManager.getConnection(TemporaUtils.databaseDirectory() + "PositionalLogger.db");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void onServerStart() {
        try {
            System.out.println("Opening Tempora DBs...");

            executor = Executors.newFixedThreadPool(Math.max(loggerList.size(), 1));

            for (GenericPositionalLogger<?> logger : loggerList) {
                logger.positionalLoggerDBConnection = DriverManager.getConnection(TemporaUtils.databaseDirectory() + logger.getSQLTableName() + ".db");
                logger.getDBConn().setAutoCommit(false); // Batch in one transaction

                logger.initTable();
                logger.createAllIndexes();

                logger.startQueueWorker();
            }

        } catch (SQLException sqlException) {
            System.err.println("Could not open Tempora databases.");
            sqlException.printStackTrace();
        }
    }

    public static void onServerClose() {
        try {
            running = false; // Signal worker to stop

            if (executor != null && !executor.isShutdown()) {
                executor.shutdown();
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    System.err.println("Executor timeout. Forcing shutdown.");
                    executor.shutdownNow();
                }
            }

            // Shut down each db.
            for (GenericPositionalLogger<?> logger : loggerList) {
                if (logger.getDBConn() != null && !logger.getDBConn().isClosed()) {
                    logger.getDBConn().close();
                }
            }


        } catch (Exception e) {
            System.err.println("Error closing resources:");
            e.printStackTrace();
        } finally {
            executor = null;
        }
    }

    private void createAllIndexes() {
        Connection dbConnection = getNewConnection();

        try (Statement stmt = dbConnection.createStatement()) {
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

    private boolean loggerEnabledByDefault() {
        return true;
    }
}
