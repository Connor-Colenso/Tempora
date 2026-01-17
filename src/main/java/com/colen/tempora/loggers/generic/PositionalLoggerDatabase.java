package com.colen.tempora.loggers.generic;

import static com.colen.tempora.Tempora.LOG;
import static com.colen.tempora.Tempora.NETWORK;
import static com.colen.tempora.utils.DatabaseUtils.databaseDir;
import static com.colen.tempora.utils.DatabaseUtils.deleteLoggerDatabase;
import static com.colen.tempora.utils.DatabaseUtils.jdbcUrl;
import static com.colen.tempora.utils.GenericUtils.parseSizeStringToBytes;
import static com.colen.tempora.utils.ReflectionUtils.getAllTableColumns;
import static com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.common.config.Configuration;

import org.jetbrains.annotations.NotNull;
import org.sqlite.SQLiteConfig;

import com.colen.tempora.loggers.generic.column.ColumnDef;
import com.colen.tempora.utils.DatabaseUtils;
import com.colen.tempora.utils.GenericUtils;
import com.colen.tempora.utils.TimeUtils;

public class PositionalLoggerDatabase {

    private static final String OLDEST_DATA_DEFAULT = "4months";

    private LogWriteSafety durabilityMode;
    public static String oldestDataCutoff;
    private long largestDatabaseSizeInBytes;

    private static final int MAX_DATA_ROWS_PER_DB = 5;
    private Connection positionalLoggerDBConnection;
    private GenericPositionalLogger<?> genericPositionalLogger;
    private boolean initialised = false;

    public PositionalLoggerDatabase(GenericPositionalLogger<?> eventToLogGenericPositionalLogger) {
        genericPositionalLogger = eventToLogGenericPositionalLogger;
    }

    public void initialiseDatabase() throws SQLException {
        initDbConnection();

        // Check for corruption
        if (DatabaseUtils.isDatabaseCorrupted(positionalLoggerDBConnection)) {

            String loggerName = genericPositionalLogger.getLoggerName();

            // Todo handle SP equivalent with UI perhaps?
            boolean erase = GenericUtils.askTerminalYesNo(
                "Tempora has detected db corruption in " + loggerName
                    + ". Would you like to erase the database and create a new one?");

            if (erase) {
                closeDbConnection();
                deleteLoggerDatabase(loggerName);
            } else {
                throw new RuntimeException(
                    "Tempora database " + loggerName
                        + ".db is corrupted. "
                        + "Please disable database, fix the corruption manually or delete the database "
                        + "and let Tempora generate a new clean version.");
            }
        }

        if (isHighRiskModeEnabled()) {
            enableHighRiskFastMode();
        }

        positionalLoggerDBConnection.setAutoCommit(false);

        initTable();
        createAllIndexes();

        // Handles data beyond the configs oldest limit or if too large.
        removeOldDatabaseData();
        trimOversizedDatabase();

        initialised = true;
    }

    private void initDbConnection() throws SQLException {
        String dbUrl = jdbcUrl(genericPositionalLogger.getLoggerName() + ".db");
        positionalLoggerDBConnection = DriverManager.getConnection(dbUrl);
    }

    public void closeDbConnection() throws SQLException {
        positionalLoggerDBConnection.close();
    }

    private void initTable() throws SQLException {
        String tableName = genericPositionalLogger.getLoggerName();
        List<ColumnDef> columns = getAllTableColumns(genericPositionalLogger);

        // Create the table, if it doesn't exist.
        StringBuilder createSQL = new StringBuilder();
        createSQL.append("CREATE TABLE IF NOT EXISTS ")
            .append(tableName)
            .append(" (");

        // Build column definitions
        String columnsSQL = columns.stream()
            .map(col -> {
                String sql = col.name + " " + col.type;
                if (!col.extraCondition.isEmpty()) {
                    sql += " " + col.extraCondition;
                }
                return sql;
            })
            .collect(Collectors.joining(", "));

        createSQL.append(columnsSQL)
            .append(");");

        // Execute statement
        try (PreparedStatement stmt = positionalLoggerDBConnection.prepareStatement(createSQL.toString())) {
            stmt.execute();
        }

        positionalLoggerDBConnection.prepareStatement(createSQL.toString())
            .execute();

        // Step 2: Check existing columns
        Set<String> existingColumns = new HashSet<>();
        try (PreparedStatement stmt = positionalLoggerDBConnection
            .prepareStatement("PRAGMA table_info(" + tableName + ");")) {
            ResultSet rs = stmt.executeQuery();

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

                if (!col.extraCondition.isEmpty()) {
                    alterSQL.append(" ")
                        .append(col.extraCondition);
                }

                alterSQL.append(";");
                positionalLoggerDBConnection.prepareStatement(alterSQL.toString())
                    .execute();
            }
        }
    }

    // This is not strictly thread safe but since we are doing this before the server has even started properly
    // nothing else is interacting with the db, so it's fine for now.
    private void eraseAllDataBeforeTime(long time) {
        // Prepare SQL statement with the safe table name
        String sql = "DELETE FROM " + genericPositionalLogger.getLoggerName() + " WHERE timestamp < ?";

        try (PreparedStatement pstmt = positionalLoggerDBConnection.prepareStatement(sql)) {
            // Set the parameter for the PreparedStatement
            pstmt.setLong(1, time);

            // Execute the update
            pstmt.executeUpdate();
        } catch (SQLException e) {
            LOG.error("SQL error, could not erase old data.", e);
        }
    }

    private void removeOldDatabaseData() {
        try {
            eraseAllDataBeforeTime(System.currentTimeMillis() - TimeUtils.convertToSeconds(oldestDataCutoff) * 1000);
        } catch (Exception e) {
            LOG.error(
                "An error occurred while erasing old data in table '{}' — oldestDataCutoff={} (check Tempora config).",
                genericPositionalLogger.getLoggerName(),
                oldestDataCutoff,
                e);

            throw new RuntimeException("Database cleanup failed: " + genericPositionalLogger.getLoggerName(), e);
        }
    }

    private void enableHighRiskFastMode() {
        try (Statement st = getDBConn().createStatement()) {
            st.execute("PRAGMA synchronous=OFF;");
            st.execute("PRAGMA wal_autocheckpoint=10000;");
        } catch (SQLException e) {
            LOG.error("Unable to enable high-risk mode for {}.", genericPositionalLogger.getLoggerName(), e);
            throw new RuntimeException(
                "Failed to enable high-risk mode for logger " + genericPositionalLogger.getLoggerName(),
                e);
        }
    }

    public Connection getDBConn() {
        if (!initialised) throw new IllegalStateException("Database not initialised.");
        return positionalLoggerDBConnection;
    }

    public Connection getReadOnlyConnection() {
        try {
            String dbUrl = jdbcUrl(genericPositionalLogger.getLoggerName() + ".db");

            SQLiteConfig config = new SQLiteConfig();
            config.setReadOnly(true);

            Connection conn = DriverManager.getConnection(dbUrl, config.toProperties());
            conn.setReadOnly(true);

            return conn;
        } catch (Exception e) {
            LOG.error(
                "Could not establish readonly connection to {} database.",
                genericPositionalLogger.getLoggerName(),
                e);
            return null;
        }
    }

    // -1 passed into seconds, equates to, find any event that occurred here, no matter how long ago.
    public void queryEventsAtPosAndTime(ICommandSender sender, int centreX, int centreY, int centreZ, long seconds) {
        if (!(sender instanceof EntityPlayerMP player)) return;

        // radius 0 means an exact match – the SQL below
        // (ABS(x - ?) <= radius) degenerates to x == ?, y == ?, z == ?
        queryEventByCoordinate(sender, centreX, centreY, centreZ, 0, seconds, player.dimension);
    }

    public void queryEventByCoordinate(ICommandSender sender, int centreX, int centreY, int centreZ, int radius,
        long seconds, int dimensionId) {

        String sql = String.format("""
            SELECT * FROM %s
            WHERE ABS(x - ?) <= ?
              AND ABS(y - ?) <= ?
              AND ABS(z - ?) <= ?
              AND dimensionID = ?
              AND timestamp >= ?
            ORDER BY timestamp DESC
            LIMIT ?;
            """, genericPositionalLogger.getLoggerName());

        try (PreparedStatement ps = getReadOnlyConnection().prepareStatement(sql)) {

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
                List<? extends GenericEventInfo> eventDataList = genericPositionalLogger.generateQueryResults(rs);

                if (eventDataList.isEmpty()) {
                    IChatComponent noResults = new ChatComponentTranslation(
                        "message.queryevents.no_results",
                        genericPositionalLogger.getLoggerName());
                    noResults.getChatStyle()
                        .setColor(EnumChatFormatting.GRAY);
                    sender.addChatMessage(noResults);

                } else {
                    IChatComponent showingResults = new ChatComponentTranslation(
                        "message.queryevents.showing_results",
                        eventDataList.size(),
                        genericPositionalLogger.getLoggerName());
                    showingResults.getChatStyle()
                        .setColor(EnumChatFormatting.GRAY);
                    sender.addChatMessage(showingResults);
                    if (genericPositionalLogger.getConcurrentEventQueue()
                        .size() > 100) {
                        IChatComponent tooMany = new ChatComponentTranslation(
                            "message.queryevents.too_many_pending",
                            genericPositionalLogger.getConcurrentEventQueue()
                                .size());
                        tooMany.getChatStyle()
                            .setColor(EnumChatFormatting.RED);
                        sender.addChatMessage(tooMany);
                    }

                    // EntityPlayerMP specific stuff, like sending animation positions to user and the text
                    // itself.
                    EntityPlayerMP player = (EntityPlayerMP) sender;

                    // Do not remove!
                    Collections.reverse(eventDataList);

                    String uuid = player.getUniqueID()
                        .toString();
                    eventDataList.forEach(p -> sender.addChatMessage(p.localiseText(uuid)));

                    // This tells the client what to render in world.
                    for (GenericEventInfo eventData : eventDataList) {
                        NETWORK.sendTo(new RenderEventPacket(eventData), player);
                    }
                }
            }
        } catch (SQLException e) {
            sender.addChatMessage(
                new ChatComponentTranslation(
                    "message.queryevents.query_failed",
                    genericPositionalLogger.getLoggerName(),
                    e.getMessage()));
        }
    }

    public GenericEventInfo queryEventByEventID(String eventID) {

        String sqlQuery = "SELECT * FROM " + genericPositionalLogger.getLoggerName() + " WHERE eventID == ? LIMIT 1";

        try (PreparedStatement ps = getReadOnlyConnection().prepareStatement(sqlQuery)) {

            ps.setString(1, eventID);

            ResultSet rs = ps.executeQuery();
            List<? extends GenericEventInfo> packets = genericPositionalLogger.generateQueryResults(rs);
            if (packets.isEmpty()) return null;

            return packets.get(0);

        } catch (Exception e) {
            LOG.error(
                "SQL query failed for table '{}' by eventID={}. Query: {}",
                genericPositionalLogger.getLoggerName(),
                eventID,
                sqlQuery,
                e);
        }

        return null;
    }

    private void createAllIndexes() throws SQLException {

        Statement stmt = positionalLoggerDBConnection.createStatement();

        String tableName = genericPositionalLogger.getLoggerName();

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

        LOG.info("Created indexes for Tempora database: {}", tableName);
    }

    public void trimOversizedDatabase() throws SQLException {
        Path dbPath = databaseDir().resolve(genericPositionalLogger.getLoggerName() + ".db");
        if (!Files.exists(dbPath)) {
            throw new IllegalStateException("Database file not found: " + dbPath);
        }

        // Do this first, to prevent fragmentation.
        checkpointAndVacuum();

        long usedBytes = physicalDbBytes();
        if (usedBytes <= largestDatabaseSizeInBytes) {
            positionalLoggerDBConnection.commit();
            return;
        }

        long totalRows = countRows();
        if (totalRows == 0) {
            positionalLoggerDBConnection.commit();
            return;
        }

        // Calculate how many rows to delete to get under limit
        double overshoot = (double) usedBytes / largestDatabaseSizeInBytes;
        long rowsToDelete = Math.max(1, (long) Math.ceil(totalRows * (overshoot - 1) / overshoot));

        String sql = "DELETE FROM " + genericPositionalLogger.getLoggerName()
            + " WHERE rowid IN (SELECT rowid FROM "
            + genericPositionalLogger.getLoggerName()
            + " ORDER BY timestamp ASC LIMIT ?)";
        try (PreparedStatement ps = positionalLoggerDBConnection.prepareStatement(sql)) {
            ps.setLong(1, rowsToDelete);
            ps.executeUpdate();
            LOG.info("Deleted {} rows from {} database.", genericPositionalLogger.getLoggerName(), rowsToDelete);
        }

        positionalLoggerDBConnection.commit();

        checkpointAndVacuum();

        double sizeMb = physicalDbBytes() / 1_048_576.0;
        double limitMb = largestDatabaseSizeInBytes / 1_048_576.0;

        LOG.info(
            "{} database is now {} MB (Config limit is {} MB).",
            genericPositionalLogger.getLoggerName(),
            formatNumber(sizeMb),
            formatNumber(limitMb));

    }

    private void checkpointAndVacuum() throws SQLException {
        try (Statement st = positionalLoggerDBConnection.createStatement()) {
            positionalLoggerDBConnection.commit();
            positionalLoggerDBConnection.setAutoCommit(true);
            st.execute("PRAGMA wal_checkpoint(TRUNCATE)");
            st.execute("VACUUM");
            positionalLoggerDBConnection.setAutoCommit(false);
        }
    }

    private long physicalDbBytes() throws SQLException {
        long pageSize = pragmaLong("page_size");
        long pageCount = pragmaLong("page_count");
        return pageSize * pageCount;
    }

    private long countRows() throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + genericPositionalLogger.getLoggerName();
        try (PreparedStatement ps = positionalLoggerDBConnection.prepareStatement(sql);
            ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    private long pragmaLong(String pragma) throws SQLException {
        try (Statement st = positionalLoggerDBConnection.createStatement();
            ResultSet rs = st.executeQuery("PRAGMA " + pragma)) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    public void genericConfig(@NotNull Configuration config) {
        String raw = config
            .get(
                genericPositionalLogger.getLoggerName(),
                "LogWriteSafety",
                genericPositionalLogger.defaultLogWriteSafetyMode()
                    .name(),
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

        oldestDataCutoff = config.getString(
            "OldestDataCutoff",
            genericPositionalLogger.getLoggerName(),
            OLDEST_DATA_DEFAULT,
            "Any records older than this relative to server boot, will be erased. This is unrecoverable, be careful!");

        try {
            durabilityMode = LogWriteSafety.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw new RuntimeException(
                "Invalid DurabilityMode \"" + raw
                    + "\" in "
                    + genericPositionalLogger.getLoggerName()
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
            genericPositionalLogger.getLoggerName(),
            "-1",
            "Approximate maximum database file size (e.g. '500KB', '1MB', '5GB'). By default this is set to -1, meaning no erasure happens.");

        if (maxDbSizeString.equals("-1")) {
            largestDatabaseSizeInBytes = Long.MAX_VALUE;
        } else {
            largestDatabaseSizeInBytes = parseSizeStringToBytes(maxDbSizeString);
        }
    }

    // Use the fastest durability mode: may lose or corrupt the DB on sudden power loss.
    // Only recommended if recent data loss is acceptable and backups exist.
    private boolean isHighRiskModeEnabled() {
        return durabilityMode == LogWriteSafety.HIGH_RISK;
    }

    private List<ColumnDef> cachedColumnDefs;

    public String generateInsertSQL() {
        List<ColumnDef> columns = getAllTableColumns(genericPositionalLogger);

        // Join the column names for the INSERT clause
        String columnList = columns.stream()
            .map(ColumnDef::getName)
            .collect(Collectors.joining(", "));

        // Generate a placeholder for each column
        String placeholders = columns.stream()
            .map(c -> "?")
            .collect(Collectors.joining(", "));

        return "INSERT INTO " + genericPositionalLogger
            .getLoggerName() + " (" + columnList + ") VALUES (" + placeholders + ")";
    }

    // This is responsible for logging the actual events. It seems rather convoluted,
    // but is trying to optimise and minimise the impact of heavy reflection usage.
    public <EventInfo extends GenericEventInfo> void insertBatch(List<EventInfo> eventInfoQueue) throws SQLException {

        if (eventInfoQueue == null || eventInfoQueue.isEmpty()) {
            return;
        }

        final String sql = generateInsertSQL();
        final List<ColumnDef> columnDefs = getAllTableColumns(genericPositionalLogger);

        try (PreparedStatement pstmt = positionalLoggerDBConnection.prepareStatement(sql)) {

            for (EventInfo eventInfo : eventInfoQueue) {
                int index = 1;

                for (ColumnDef columnDef : columnDefs) {
                    Object value = columnDef.columnAccessor.get(eventInfo);

                    columnDef.columnAccessor.binder.bind(pstmt, index++, value);
                }

                pstmt.addBatch();
            }

            pstmt.executeBatch();
        }
    }

    public void shutdownDatabase() throws SQLException {
        if (positionalLoggerDBConnection != null && !positionalLoggerDBConnection.isClosed()) {
            closeDbConnection();
        }
    }

}
