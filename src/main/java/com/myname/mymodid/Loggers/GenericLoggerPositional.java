package com.myname.mymodid.Loggers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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

import com.myname.mymodid.QueueElement.GenericQueueElement;
import com.myname.mymodid.TemporaUtils;

import cpw.mods.fml.common.FMLCommonHandler;

public abstract class GenericLoggerPositional<EventToLog extends GenericQueueElement> {

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean keepRunning = new AtomicBoolean(true);
    protected ConcurrentLinkedQueue<EventToLog> eventQueue = new ConcurrentLinkedQueue<>();

    public abstract void threadedSaveEvent(EventToLog event);

    public static void startEventProcessingThread() {
        executor.submit(() -> {
            while (keepRunning.get()) {
                try {
                    // Check the total number of events in all queues
                    int totalEvents = loggerList.stream()
                        .mapToInt(logger -> logger.eventQueue.size())
                        .sum();

                    // Process events if the total exceeds 100
                    if (totalEvents >= 100) {
                        for (GenericLoggerPositional<?> logger : loggerList) {
                            processLoggerQueue(logger);
                        }
                    }
                    // Sleep for a while before checking again
                    Thread.sleep(5000); // Check every 5 seconds
                } catch (InterruptedException e) {
                    Thread.currentThread()
                        .interrupt();
                    System.err.println("Thread was interrupted, failed to complete operation.");
                } catch (Exception e) {
                    System.err.println("An error occurred in the logging processor thread: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
    }

    public static void stopEventProcessingThread() {
        keepRunning.set(false);
        executor.shutdownNow(); // Attempt to stop all actively executing tasks
    }

    private static <T extends GenericQueueElement> void processLoggerQueue(GenericLoggerPositional<T> logger) {
        while (!logger.eventQueue.isEmpty()) {
            logger.threadedSaveEvent(logger.eventQueue.poll());
        }
    }

    public abstract void handleConfig(Configuration config);

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static final Set<GenericLoggerPositional<?>> loggerList = new HashSet<>();

    public static ArrayList<String> queryEventsWithinRadiusAndTime(ICommandSender sender, int radius, long seconds,
        String tableName) {

        ArrayList<String> returnList = new ArrayList<>();

        if (!(sender instanceof EntityPlayerMP entityPlayerMP)) return returnList;

        for (GenericLoggerPositional<?> logger : GenericLoggerPositional.loggerList) {
            try {
                if (tableName != null) {
                    if (!logger.getTableName()
                        .equals(tableName)) continue;
                }

                // Construct the SQL query
                final String sql = "SELECT * FROM " + logger.getTableName()
                    + " WHERE ABS(x - ?) <= ? AND ABS(y - ?) <= ? AND ABS(z - ?) <= ?"
                    + " AND dimensionID = ? AND timestamp >= datetime(?, 'unixepoch')";

                // Prepare the statement
                PreparedStatement pstmt = positionLoggerDBConnection.prepareStatement(sql);
                pstmt.setInt(1, sender.getPlayerCoordinates().posX);
                pstmt.setInt(2, radius);
                pstmt.setInt(3, sender.getPlayerCoordinates().posY);
                pstmt.setInt(4, radius);
                pstmt.setInt(5, sender.getPlayerCoordinates().posZ);
                pstmt.setInt(6, radius);
                pstmt.setInt(7, entityPlayerMP.dimension);
                pstmt.setLong(8, System.currentTimeMillis() / 1000 - seconds);

                // Execute the query
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    returnList.add(logger.processResultSet(rs));
                }
            } catch (SQLException e) {
                returnList.add("Database query failed on " + logger.getTableName() + ". " + e.getLocalizedMessage());
            }
        }

        return returnList;
    }

    protected abstract String processResultSet(ResultSet rs) throws SQLException;

    protected static Connection positionLoggerDBConnection;

    public final void registerEvent() {
        MinecraftForge.EVENT_BUS.register(this);

        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    public GenericLoggerPositional() {
        loggerList.add(this);
    }

    public static void onServerStart() {
        try {
            positionLoggerDBConnection = DriverManager
                .getConnection(TemporaUtils.databaseDirectory() + "PositionalLogger.db");
            for (GenericLoggerPositional<?> loggerPositional : loggerList) {
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
