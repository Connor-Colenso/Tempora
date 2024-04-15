package com.colen.tempora.PositionalEvents.Loggers.Generic;

import com.colen.tempora.PositionalEvents.Loggers.GenericPacket;
import com.colen.tempora.PositionalEvents.Loggers.ISerializable;
import com.colen.tempora.TemporaUtils;
import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.colen.tempora.Tempora.NETWORK;

public abstract class GenericPositionalLogger<EventToLog extends GenericQueueElement> {

    protected static final int MAX_DATA_ROWS_PER_PACKET = 5;
    protected static AtomicBoolean savingData;
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean keepRunning = new AtomicBoolean(true);
    protected ConcurrentLinkedQueue<EventToLog> eventQueue = new ConcurrentLinkedQueue<>();

    public abstract void threadedSaveEvent(EventToLog event);

    public static void startEventProcessingThread() {
        executor.submit(() -> {
            while (keepRunning.get()) {
                try {
                    processAllLoggerQueues();
                    //Thread.sleep(5000);
                } catch (Exception e) {
                    System.err.println("An error occurred in the logging processor thread: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
    }

    private static synchronized void processAllLoggerQueues() {
        for (GenericPositionalLogger<?> logger : loggerList) {
            processLoggerQueue(logger);
        }
    }

    private static <T extends GenericQueueElement> void processLoggerQueue(GenericPositionalLogger<T> logger) {
        while (!logger.eventQueue.isEmpty()) {
            logger.threadedSaveEvent(logger.eventQueue.poll());
        }
    }

    public static void stopEventProcessingThread() {
        keepRunning.set(false);
        executor.shutdownNow(); // Attempt to stop all actively executing tasks
    }

    public abstract void handleConfig(Configuration config);

    public static final Set<GenericPositionalLogger<?>> loggerList = new HashSet<>();

    protected abstract ArrayList<ISerializable> generatePacket(ResultSet rs) throws SQLException;

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
                if (tableName != null && !logger.getTableName()
                    .equals(tableName)) continue;
                try (
                    Connection conn = DriverManager
                        .getConnection(TemporaUtils.databaseDirectory() + "PositionalLogger.db");
                    PreparedStatement pstmt = conn.prepareStatement(
                        "SELECT * FROM " + logger.getTableName()
                            + " WHERE ABS(x - ?) <= ? AND ABS(y - ?) <= ? AND ABS(z - ?) <= ?"
                            + " AND dimensionID = ? AND timestamp >= ? ORDER BY timestamp DESC LIMIT ?")) {

                    pstmt.setDouble(1, posX);
                    pstmt.setInt(2, radius);
                    pstmt.setDouble(3, posY);
                    pstmt.setInt(4, radius);
                    pstmt.setDouble(5, posZ);
                    pstmt.setInt(6, radius);
                    pstmt.setInt(7, dimensionId);
                    pstmt.setTimestamp(8, new Timestamp(pastTime)); // Filter events from pastTime onwards]
                    pstmt.setInt(9, MAX_DATA_ROWS_PER_PACKET);

                    // Execute and submit to client via a custom packet if not empty.
                    try (ResultSet rs = pstmt.executeQuery()) {
                        ArrayList<ISerializable> sendList = logger.generatePacket(rs);
                        if (!sendList.isEmpty()) {
                            NETWORK.sendTo(new GenericPacket(sendList), entityPlayerMP);
                        } else {
                            sender.addChatMessage(
                                new ChatComponentText("No results found for " + logger.getTableName() + "."));
                        }
                    }
                } catch (SQLException e) {
                    sender.addChatMessage(
                        new ChatComponentText(
                            "Database query failed on " + logger.getTableName() + ": " + e.getLocalizedMessage()));
                }
            }
        }
    }

    protected static Connection positionLoggerDBConnection;

    public final void registerEvent() {
        MinecraftForge.EVENT_BUS.register(this);

        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    public GenericPositionalLogger() {
        loggerList.add(this);
    }

    public static void onServerStart() {
        try {
            System.out.println(
                "Attempting to open tempora positional db at " + TemporaUtils.databaseDirectory()
                    + "PositionalLogger.db");
            positionLoggerDBConnection = DriverManager
                .getConnection(TemporaUtils.databaseDirectory() + "PositionalLogger.db");

            initAllTables();

            // Init indexex for x, y, z, timestamp and dimensionID for all tables.
            createAllIndexes();

            startEventProcessingThread(); // Start processing thread
        } catch (SQLException sqlException) {
            System.err.println("Critical exception, could not open Tempora databases properly.");
            sqlException.printStackTrace();
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

    public static void onServerClose() {
        try {
            stopEventProcessingThread(); // Ensure the thread is stopped when the server is shutting down
            positionLoggerDBConnection.close();
        } catch (SQLException exception) {
            System.err.println("Critical exception, could not close Tempora databases properly.");
            exception.printStackTrace();
        }
    }

    public abstract void initTable();

    public final String getTableName() {
        return getClass().getSimpleName();
    }

    private static void initAllTables() {
        for (GenericPositionalLogger<?> loggerPositional : loggerList) {
            loggerPositional.initTable();
        }
    }

    private static void createAllIndexes() {
        Connection dbConnection = getNewConnection();

        try (Statement stmt = dbConnection.createStatement()) {
            for (GenericPositionalLogger<?> logger : loggerList) {
                String tableName = logger.getTableName();

                // Creating a composite index for x, y, z, dimensionID and timestamp
                String createCompositeIndex = String.format(
                    "CREATE INDEX IF NOT EXISTS idx_%s_xyz_dimension_time ON %s (x, y, z, dimensionID, timestamp DESC);",
                    tableName, tableName);
                stmt.execute(createCompositeIndex);

                // Creating an index for timestamp alone to optimize for queries primarily sorting or filtering on timestamp
                String createTimestampIndex = String.format(
                    "CREATE INDEX IF NOT EXISTS idx_%s_timestamp ON %s (timestamp DESC);",
                    tableName, tableName);
                stmt.execute(createTimestampIndex);

                System.out.println("Indexes created for table: " + tableName);
            }
        } catch (SQLException e) {
            System.err.println("Error creating indexes: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
