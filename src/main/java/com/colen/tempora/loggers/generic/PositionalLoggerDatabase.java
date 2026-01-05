package com.colen.tempora.loggers.generic;

import com.colen.tempora.TemporaUtils;
import com.colen.tempora.utils.TimeUtils;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.common.config.Configuration;
import org.jetbrains.annotations.NotNull;
import org.sqlite.SQLiteConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.colen.tempora.Tempora.LOG;
import static com.colen.tempora.utils.GenericUtils.parseSizeStringToBytes;

public class PositionalLoggerDatabase {

    private static final String OLDEST_DATA_DEFAULT = "4months";

    private LogWriteSafety durabilityMode;
    public static String oldestDataCutoff;
    private long largestDatabaseSizeInBytes;

    private static final int MAX_DATA_ROWS_PER_DB = 5;
    private Connection positionalLoggerDBConnection;
    private GenericPositionalLogger<?> genericPositionalLogger;

    public PositionalLoggerDatabase(GenericPositionalLogger<?> eventToLogGenericPositionalLogger) {
        genericPositionalLogger = eventToLogGenericPositionalLogger;
    }

    public void initDbConnection() throws SQLException {
        String dbUrl = TemporaUtils.jdbcUrl(genericPositionalLogger.getLoggerName() + ".db");
        positionalLoggerDBConnection = DriverManager.getConnection(dbUrl);
    }

    public void closeDbConnection() throws SQLException {
        positionalLoggerDBConnection.close();
    }

    public void initTable() throws SQLException {
        String tableName = genericPositionalLogger.getLoggerName();
        List<ColumnDef> columns = genericPositionalLogger.getAllTableColumns();

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
        String sql = "DELETE FROM " + genericPositionalLogger.getLoggerName() + " WHERE timestamp < ?";

        try (PreparedStatement pstmt = getDBConn().prepareStatement(sql)) {
            // Set the parameter for the PreparedStatement
            pstmt.setLong(1, time);

            // Execute the update
            pstmt.executeUpdate();
        } catch (SQLException e) {
            LOG.error("SQL error, could not erase old data.", e);
        }
    }

    public void removeOldDatabaseData() {
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

    public void enableHighRiskFastMode() throws SQLException {
        Connection conn = getDBConn();

        Statement st = conn.createStatement();
        st.execute("PRAGMA synchronous=OFF;");
        st.execute("PRAGMA wal_autocheckpoint=10000;");
    }


    public Connection getDBConn() {
        return positionalLoggerDBConnection;
    }

    // TODO Default version hash, and then if undoing events across versions, issue warning.
    // These should really, never be missing, so even though the defaults are a bit clunky, it's alright.
    public static List<ColumnDef> getDefaultColumns() {
        return Arrays.asList(
            new ColumnDef("eventID", "TEXT", "PRIMARY KEY"),
            new ColumnDef("x", "INTEGER", "NOT NULL DEFAULT " + Integer.MIN_VALUE),
            new ColumnDef("y", "INTEGER", "NOT NULL DEFAULT " + Integer.MIN_VALUE),
            new ColumnDef("z", "INTEGER", "NOT NULL DEFAULT " + Integer.MIN_VALUE),
            new ColumnDef("timestamp", "DATETIME", "NOT NULL DEFAULT 0"),
            new ColumnDef("dimensionID", "INTEGER", "NOT NULL DEFAULT " + Integer.MIN_VALUE),
            new ColumnDef("versionID", "INTEGER", "NOT NULL DEFAULT " + Integer.MIN_VALUE));
    }

    public Connection getReadOnlyConnection() {
        try {
            String dbUrl = TemporaUtils.jdbcUrl(genericPositionalLogger.getLoggerName() + ".db");

            SQLiteConfig config = new SQLiteConfig();
            config.setReadOnly(true);

            Connection conn = DriverManager.getConnection(dbUrl, config.toProperties());
            conn.setReadOnly(true);

            return conn;
        } catch (Exception e) {
            LOG.error("Could not establish readonly connection to {} database.", genericPositionalLogger.getLoggerName(), e);
            return null;
        }
    }

    // -1 passed into seconds, equates to, find any event that occurred here, no matter how long ago.
    public void queryEventsAtPosAndTime(ICommandSender sender, int centreX, int centreY, int centreZ,
                                               long seconds) {
        if (!(sender instanceof EntityPlayerMP player)) return;

        // radius 0 means an exact match – the SQL below
        // (ABS(x - ?) <= radius) degenerates to x == ?, y == ?, z == ?
        queryEventByCoordinate(sender, centreX, centreY, centreZ, 0, seconds, player.dimension);
    }

    public void queryEventByCoordinate(ICommandSender sender, int centreX, int centreY, int centreZ, int radius,
                                              long seconds, int dimensionId) {

        synchronized (GenericPositionalLogger.class) {

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
                    List<GenericQueueElement> packets = genericPositionalLogger.generateQueryResults(rs);

                    if (packets.isEmpty()) {
                        IChatComponent noResults = new ChatComponentTranslation(
                            "message.queryevents.no_results",
                            genericPositionalLogger.getLoggerName());
                        noResults.getChatStyle()
                            .setColor(EnumChatFormatting.GRAY);
                        sender.addChatMessage(noResults);

                    } else {
                        IChatComponent showingResults = new ChatComponentTranslation(
                            "message.queryevents.showing_results",
                            packets.size(),
                            genericPositionalLogger.getLoggerName());
                        showingResults.getChatStyle()
                            .setColor(EnumChatFormatting.GRAY);
                        sender.addChatMessage(showingResults);
                        if (genericPositionalLogger.getEventQueue().size() > 100) {
                            IChatComponent tooMany = new ChatComponentTranslation(
                                "message.queryevents.too_many_pending",
                                genericPositionalLogger.getEventQueue().size());
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
                            new RenderEventPacket(packet).sendEventToClientForRendering(player);
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
    }

    public GenericQueueElement queryEventByEventID(String eventID) {

        synchronized (GenericPositionalLogger.class) {

            String sqlQuery = "SELECT * FROM " + genericPositionalLogger.getLoggerName() + " WHERE eventID == ? LIMIT 1";

            try (PreparedStatement ps = getReadOnlyConnection().prepareStatement(sqlQuery)) {

                ps.setString(1, eventID);

                ResultSet rs = ps.executeQuery();
                List<GenericQueueElement> packets = genericPositionalLogger.generateQueryResults(rs);
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
        }

        return null;
    }

    public void createAllIndexes() throws SQLException {

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
        Path dbPath = TemporaUtils.databaseDir()
            .resolve(genericPositionalLogger.getLoggerName() + ".db");
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
            String.format("%.2f", sizeMb),
            String.format("%.2f", limitMb));

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
        try (PreparedStatement ps = positionalLoggerDBConnection.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    private long pragmaLong(String pragma) throws SQLException {
        try (Statement st = positionalLoggerDBConnection.createStatement(); ResultSet rs = st.executeQuery("PRAGMA " + pragma)) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    public void genericConfig(@NotNull Configuration config) {
        String raw = config
            .get(
                genericPositionalLogger.getLoggerName(),
                "LogWriteSafety",
                genericPositionalLogger.defaultLogWriteSafetyMode().name(),
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
            "Any records older than this relative to now, will be erased. This is unrecoverable, be careful!");

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
    public final boolean isHighRiskModeEnabled() {
        return durabilityMode == LogWriteSafety.HIGH_RISK;
    }

}
