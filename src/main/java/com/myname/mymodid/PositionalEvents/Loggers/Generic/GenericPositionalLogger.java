package com.myname.mymodid.PositionalEvents.Loggers.Generic;

import static com.myname.mymodid.Tempora.NETWORK;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;

import com.myname.mymodid.TemporaUtils;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.network.simpleimpl.IMessage;

public abstract class GenericPositionalLogger<EventToLog extends GenericQueueElement> {

    protected static final int MAX_DATA_ROWS_PER_PACKET = 100;
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean keepRunning = new AtomicBoolean(true);
    protected ConcurrentLinkedQueue<EventToLog> eventQueue = new ConcurrentLinkedQueue<>();

    public abstract void threadedSaveEvent(EventToLog event);

    public static void startEventProcessingThread() {
        executor.submit(() -> {
            while (keepRunning.get()) {
                try {
                    processAllLoggerQueues();
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread()
                        .interrupt();
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

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static final Set<GenericPositionalLogger<?>> loggerList = new HashSet<>();

    protected abstract IMessage generatePacket(ResultSet rs) throws SQLException;

    public static void queryEventsWithinRadiusAndTime(ICommandSender sender, int radius, long seconds,
        String tableName) {
        ArrayList<String> returnList = new ArrayList<>();

        if (!(sender instanceof EntityPlayerMP entityPlayerMP)) return;
        int posX = entityPlayerMP.getPlayerCoordinates().posX;
        int posY = entityPlayerMP.getPlayerCoordinates().posY;
        int posZ = entityPlayerMP.getPlayerCoordinates().posZ;
        int dimensionId = entityPlayerMP.dimension;

        long pastTime = System.currentTimeMillis() - seconds * 1000; // Convert seconds to milliseconds

        ArrayList<IMessage> packetList = new ArrayList<>();
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
                            + " AND dimensionID = ? AND timestamp >= ?")) {

                    pstmt.setDouble(1, posX);
                    pstmt.setInt(2, radius);
                    pstmt.setDouble(3, posY);
                    pstmt.setInt(4, radius);
                    pstmt.setDouble(5, posZ);
                    pstmt.setInt(6, radius);
                    pstmt.setInt(7, dimensionId);
                    pstmt.setTimestamp(8, new Timestamp(pastTime)); // Filter events from pastTime onwards

                    try (ResultSet rs = pstmt.executeQuery()) {
                        packetList.add(logger.generatePacket(rs));
                    }
                } catch (SQLException e) {
                    returnList
                        .add("Database query failed on " + logger.getTableName() + ": " + e.getLocalizedMessage());
                }
            }
        }

        for (IMessage packet : packetList) {
            NETWORK.sendTo(packet, entityPlayerMP);
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
            positionLoggerDBConnection = DriverManager
                .getConnection(TemporaUtils.databaseDirectory() + "PositionalLogger.db");
            for (GenericPositionalLogger<?> loggerPositional : loggerList) {
                loggerPositional.initTable();
            }
            startEventProcessingThread(); // Start processing thread
        } catch (SQLException sqlException) {
            System.err.println("Critical exception, could not open Tempora databases properly.");
            sqlException.printStackTrace();
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

}
