package com.colen.tempora.loggers.generic;

import static com.colen.tempora.Tempora.LOG;
import static com.colen.tempora.Tempora.NETWORK;
import static com.colen.tempora.utils.DatabaseUtils.jdbcUrl;
import static com.colen.tempora.utils.ReflectionUtils.getAllTableColumns;

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

import com.colen.tempora.Tempora;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.common.config.Configuration;

import org.jetbrains.annotations.NotNull;
import org.sqlite.SQLiteConfig;

import com.colen.tempora.loggers.generic.column.ColumnDef;
import com.colen.tempora.utils.TimeUtils;
import com.gtnewhorizon.gtnhlib.chat.customcomponents.ChatComponentNumber;

public class PositionalLoggerDatabase {

    private static final String OLDEST_DATA_DEFAULT = "4months";

    private LogWriteSafety durabilityMode;
    public static String oldestDataCutoff;

    private static final int MAX_RESULTS = 5;
    private Connection positionalLoggerDBConnection;
    private final GenericPositionalLogger<?> genericPositionalLogger;

    public PositionalLoggerDatabase(GenericPositionalLogger<?> eventToLogGenericPositionalLogger) {
        genericPositionalLogger = eventToLogGenericPositionalLogger;
    }

    public void initialiseDatabase() throws SQLException {
        String loggerName = genericPositionalLogger.getLoggerName();
        Tempora.LOG.info("Initialising {} database.", loggerName);

        initDbConnection();

        if (isHighRiskModeEnabled()) {
            Tempora.LOG.info("The database for {} has high risk mode enabled.", loggerName);
            enableHighRiskFastMode();
        }

        positionalLoggerDBConnection.setAutoCommit(false);

        initTable();

        // Only make indexes if they do not already exist.
        if (!tableHasIndexes()) {
            createAllIndexes();
        }

        removeOldDatabaseData();
        cleanupDatabase();
    }

    private void initDbConnection() throws SQLException {
        String dbUrl = jdbcUrl(genericPositionalLogger.getLoggerName() + ".db");
        positionalLoggerDBConnection = DriverManager.getConnection(dbUrl);
    }

    public void closeDbConnection() throws SQLException {
        positionalLoggerDBConnection.close();
        positionalLoggerDBConnection = null;
    }

    private void initTable() throws SQLException {
        String tableName = genericPositionalLogger.getLoggerName();
        List<ColumnDef> columns = getAllTableColumns(genericPositionalLogger);

        // Create the table if it doesn't exist.
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

    private void removeOldDatabaseData() {
        final String loggerName = genericPositionalLogger.getLoggerName();
        Tempora.LOG.info("Removing data older than {} from {}.", oldestDataCutoff, loggerName);

        try {
            // Prepare SQL statement with the safe table name
            String sql = "DELETE FROM " + loggerName + " WHERE timestamp < ?";

            try (PreparedStatement p_stmt = positionalLoggerDBConnection.prepareStatement(sql)) {
                // Set the parameter for the PreparedStatement
                long time = System.currentTimeMillis() - TimeUtils.convertToSeconds(oldestDataCutoff) * 1000;
                p_stmt.setLong(1, time);

                // Execute the update
                p_stmt.executeUpdate();
            } catch (SQLException e) {
                LOG.error("SQL error, could not erase old data.", e);
                throw new RuntimeException(e);
            }

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
            st.execute("PRAGMA temp_store = MEMORY;"); // todo review
        } catch (SQLException e) {
            LOG.error("Unable to enable high-risk mode for {}.", genericPositionalLogger.getLoggerName(), e);
            throw new RuntimeException(
                "Failed to enable high-risk mode for logger " + genericPositionalLogger.getLoggerName(),
                e);
        }
    }

    public Connection getDBConn() {
        return positionalLoggerDBConnection;
    }

    public @NotNull Connection getReadOnlyConnection() {
        try {
            String dbUrl = jdbcUrl(genericPositionalLogger.getLoggerName() + ".db");

            SQLiteConfig config = new SQLiteConfig();
            config.setReadOnly(true);

            Connection conn = DriverManager.getConnection(dbUrl, config.toProperties());
            conn.setReadOnly(true);

            // Make timeout longer, helps prevent errors.
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA busy_timeout = 5000;");
            }

            return conn;
        } catch (Exception e) {
            LOG.error(
                "Could not establish readonly connection to {} database.",
                genericPositionalLogger.getLoggerName(),
                e);
            throw new RuntimeException("Failed to open readonly connection");
        }
    }

    // -1 passed into seconds, equates to, find any event that occurred here, no matter how long ago.
    public void queryEventsAtPosAndTime(ICommandSender sender, int centreX, int centreY, int centreZ, long seconds) {
        if (!(sender instanceof EntityPlayerMP player)) return;

        // radius 0 means an exact match – the SQL below
        // (ABS(x - ?) <= radius) becomes x == ?, y == ?, z == ?
        queryEventByCoordinate(sender, centreX, centreY, centreZ, 0, seconds, player.dimension);
    }

    public void queryEventByCoordinate(ICommandSender sender, int centreX, int centreY, int centreZ, int radius,
        long seconds, int dimensionId) {

        String sql = String.format(
        """
        SELECT * FROM %s
        WHERE x BETWEEN ? AND ?
          AND y BETWEEN ? AND ?
          AND z BETWEEN ? AND ?
          AND dimensionID = ?
          AND timestamp >= ?
        ORDER BY timestamp DESC
        LIMIT ?;
        """, genericPositionalLogger.getLoggerName());

        try (Connection conn = getReadOnlyConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            // X range
            ps.setInt(1, centreX - radius);
            ps.setInt(2, centreX + radius);

            // Y range
            ps.setInt(3, centreY - radius);
            ps.setInt(4, centreY + radius);

            // Z range
            ps.setInt(5, centreZ - radius);
            ps.setInt(6, centreZ + radius);

            // Dimension ID
            ps.setInt(7, dimensionId);

            if (seconds < 0) {
                // Don’t apply any timestamp filter, fetch everything.
                ps.setTimestamp(8, new Timestamp(0));
            } else {
                ps.setTimestamp(8, new Timestamp(System.currentTimeMillis() - seconds * 1000L));
            }

            ps.setInt(9, MAX_RESULTS);

            try (ResultSet rs = ps.executeQuery()) {
                List<? extends GenericEventInfo> eventDataList = genericPositionalLogger.generateQueryResults(rs);

                if (eventDataList.isEmpty()) {
                    IChatComponent noResults = new ChatComponentTranslation(
                        "tempora.command.query_events.no_results",
                        genericPositionalLogger.getLoggerName());
                    noResults.getChatStyle()
                        .setColor(EnumChatFormatting.GRAY);
                    sender.addChatMessage(noResults);

                } else {
                    int eventsFound = eventDataList.size();
                    // Plural or not.
                    String langKey = (eventsFound == 1) ? "message.query_events.showing_result"
                        : "message.query_events.showing_results";

                    IChatComponent showingResults = new ChatComponentTranslation(
                        langKey,
                        new ChatComponentNumber(eventsFound),
                        genericPositionalLogger.getLoggerName());
                    showingResults.getChatStyle()
                        .setColor(EnumChatFormatting.GRAY);
                    sender.addChatMessage(showingResults);

                    // If events waiting to process is large, then warn the user.
                    // Shouldn't really be happening.
                    int queuedEventsFound = genericPositionalLogger.getConcurrentEventQueue()
                        .size();
                    if (queuedEventsFound > 100) {
                        IChatComponent tooMany = new ChatComponentTranslation(
                            "message.query_events.too_many_pending",
                            new ChatComponentNumber(queuedEventsFound));
                        tooMany.getChatStyle()
                            .setColor(EnumChatFormatting.DARK_RED);
                        sender.addChatMessage(tooMany);
                    }

                    // EntityPlayerMP specific stuff, like sending animation positions to the user and the text
                    // itself.
                    EntityPlayerMP player = (EntityPlayerMP) sender;

                    // Do not remove!
                    Collections.reverse(eventDataList);

                    String uuid = player.getUniqueID()
                        .toString();
                    eventDataList.forEach(p -> sender.addChatMessage(p.localiseText(uuid)));

                    // This tells the client what to render in the world, if anything.
                    for (GenericEventInfo eventData : eventDataList) {
                        NETWORK.sendTo(new RenderEventPacket(eventData), player);
                    }
                }
            }
        } catch (SQLException e) {
            sender.addChatMessage(
                new ChatComponentTranslation(
                    "tempora.command.query_events.query_failed",
                    genericPositionalLogger.getLoggerName(),
                    e.getMessage()));
        }
    }

    public GenericEventInfo queryEventByEventID(String eventID) {

        String sqlQuery = "SELECT * FROM " + genericPositionalLogger.getLoggerName() + " WHERE eventID == ? LIMIT 1";

        try (Connection conn = getReadOnlyConnection();
             PreparedStatement ps = conn.prepareStatement(sqlQuery)) {

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

    private boolean tableHasIndexes() {
        String tableName = genericPositionalLogger.getLoggerName();

        String sql = """
        SELECT 1
        FROM sqlite_master
        WHERE type = 'index'
          AND tbl_name = ?
        LIMIT 1;
        """;

        try (PreparedStatement ps = positionalLoggerDBConnection.prepareStatement(sql)) {
            ps.setString(1, tableName);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next(); // true if at least one index exists
            }

        } catch (SQLException e) {
            LOG.error("Failed to check indexes for table '{}'", tableName, e);
            throw new RuntimeException(e);
        }
    }

    private void createAllIndexes() {
        String tableName = genericPositionalLogger.getLoggerName();

        LOG.info("Creating indexes for Tempora database: {}", tableName);

        String createCompositeIndex = String.format(
            "CREATE INDEX IF NOT EXISTS idx_%s_xyz_dimension_time ON %s (dimensionID, x, y, z, timestamp DESC);",
            tableName,
            tableName
        );

        String createTimestampIndex = String.format(
            "CREATE INDEX IF NOT EXISTS idx_%s_timestamp ON %s (timestamp DESC);",
            tableName,
            tableName
        );

        try (Statement stmt = positionalLoggerDBConnection.createStatement()) {
            stmt.execute(createCompositeIndex);
            stmt.execute(createTimestampIndex);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create index for " + tableName, e);
        }

        LOG.info("Successfully created indexes for Tempora database: {}", tableName);
    }

    private void cleanupDatabase() throws SQLException {
        LOG.info("Cleaning up database for {}", genericPositionalLogger.getLoggerName());

        try (Statement st = positionalLoggerDBConnection.createStatement()) {
            positionalLoggerDBConnection.commit();
            positionalLoggerDBConnection.setAutoCommit(true);
            st.execute("PRAGMA wal_checkpoint(TRUNCATE)");
            st.execute("VACUUM");
            positionalLoggerDBConnection.setAutoCommit(false);
        }
    }

    public void genericConfig(@NotNull Configuration config) {
        String raw = config
            .get(
                genericPositionalLogger.getLoggerName(),
                "logWriteSafety",
                genericPositionalLogger.defaultLogWriteSafetyMode().name(),
                """
                    NORMAL – Safer, but slower
                      - Best for long-term stability.
                      - Every event is saved to disk right away, so even if your server crashes or the power goes out, your logs will be intact.
                      - Slightly slower performance, may reduce TPS during heavy activity like world edits or explosions.

                    HIGH_RISK – Much faster, but riskier
                      - Boosts performance by delaying how often logs are saved to disk.
                      - WARNING: if your server crashes or shuts down suddenly, the last few seconds of logs may be lost or corrupted. This does **not** affect your world, only the Tempora logs.
                      - Only recommended if you make regular backups or can afford to lose a few seconds of log data.
                    """)
            .getString()
            .trim()
            .toUpperCase();

        oldestDataCutoff = config.getString(
            "oldestDataCutoff",
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

    }

    // Use the fastest durability mode: may lose or corrupt the DB on sudden power loss.
    // Only recommended if recent data loss is acceptable and backups exist.
    private boolean isHighRiskModeEnabled() {
        return durabilityMode == LogWriteSafety.HIGH_RISK;
    }

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
        if (positionalLoggerDBConnection == null) throw new SQLException("Database connection is null for " +  genericPositionalLogger.getLoggerName());
        if (eventInfoQueue == null || eventInfoQueue.isEmpty()) {
            return;
        }

        final String sql = generateInsertSQL();
        final List<ColumnDef> columnDefs = getAllTableColumns(genericPositionalLogger);

        try (PreparedStatement p_stmt = positionalLoggerDBConnection.prepareStatement(sql)) {

            for (EventInfo eventInfo : eventInfoQueue) {
                int index = 1;

                for (ColumnDef columnDef : columnDefs) {
                    Object value = columnDef.columnAccessor.get(eventInfo);

                    columnDef.columnAccessor.binder.bind(p_stmt, index++, value);
                }

                p_stmt.addBatch();
            }

            p_stmt.executeBatch();
            positionalLoggerDBConnection.commit();
        } catch (SQLException e) {
            LOG.error("Exception has been thrown by {} while inserting data, attempting to rollback database to safe state.", genericPositionalLogger.getLoggerName());
            positionalLoggerDBConnection.rollback();
            throw e;
        }

    }

    public void shutdownDatabase() throws SQLException {
        if (positionalLoggerDBConnection != null && !positionalLoggerDBConnection.isClosed()) {
            closeDbConnection();
        } else {
            throw new IllegalStateException("Could not shutdown database connection");
        }
    }

}
