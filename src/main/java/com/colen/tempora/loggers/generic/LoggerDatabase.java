package com.colen.tempora.loggers.generic;

import static com.colen.tempora.Tempora.LOG;
import static com.colen.tempora.utils.DatabaseUtils.jdbcUrl;
import static com.colen.tempora.utils.ReflectionUtils.getAllTableColumns;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.minecraftforge.common.config.Configuration;

import org.jetbrains.annotations.NotNull;
import org.sqlite.SQLiteConfig;

import com.colen.tempora.Tempora;
import com.colen.tempora.loggers.generic.column.ColumnDef;
import com.colen.tempora.utils.TimeUtils;

public class LoggerDatabase {

    private static final String OLDEST_DATA_DEFAULT = "4months";
    private static final int CACHE_SIZE_MEGABYTES_DEFAULT = 16;

    // Old events are deleted this many at a time, so pruning never holds up writing new events for long.
    private static final int PRUNE_BATCH_SIZE = 5_000;

    private String oldestDataCutoff;
    private long oldestDataMilliseconds; // Parsed from oldestDataCutoff when the database opens.
    private int cacheSizeMegabytes;

    private Connection dbConnection;
    private final GenericLogger<?> logger;
    private PreparedStatement addDataInjectionStatement; // Pre made injection statement
    private List<ColumnDef> insertedColumns; // Instantiate upon first database insertion.

    public LoggerDatabase(GenericLogger<?> logger) {
        this.logger = logger;
    }

    public void initialiseDatabase() throws SQLException {
        String loggerName = logger.getLoggerName();
        Tempora.LOG.info("Initialising {} database.", loggerName);

        oldestDataMilliseconds = parseOldestDataCutoff();

        initDbConnection();
        dbConnection.setAutoCommit(false);

        initTable();

        // Cheap when they already exist, and adds any index a newer version of the logger needs.
        createIndexes();

        dbConnection.commit();
    }

    private void initDbConnection() throws SQLException {
        String dbUrl = jdbcUrl(logger.getLoggerName() + ".db");

        SQLiteConfig config = new SQLiteConfig();
        // WAL lets lookups read while events are being written, and with synchronous=NORMAL a commit doesn't wait for
        // the disk. A server crash loses nothing that was committed, a power cut at most the last few commits, and
        // neither can corrupt the database.
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
        // Negative means KiB rather than pages.
        config.setCacheSize(-cacheSizeMegabytes * 1024);

        dbConnection = DriverManager.getConnection(dbUrl, config.toProperties());
    }

    private long parseOldestDataCutoff() {
        try {
            return TimeUtils.convertToSeconds(oldestDataCutoff) * 1000;
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                "Invalid oldestDataCutoff \"" + oldestDataCutoff
                    + "\" for "
                    + logger.getLoggerName()
                    + " (check Tempora config).",
                e);
        }
    }

    public void closeDbConnection() throws SQLException {
        dbConnection.close();
        dbConnection = null;
        // Belonged to the closed connection, so it is made again for the next world opened.
        addDataInjectionStatement = null;
    }

    private void initTable() throws SQLException {
        String tableName = logger.getLoggerName();
        List<ColumnDef> columns = getAllTableColumns(logger);

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
        try (PreparedStatement stmt = dbConnection.prepareStatement(createSQL.toString())) {
            stmt.execute();
        }

        // Step 2: Read existing columns and their declared SQL types.
        Map<String, String> existingColumnTypes = new HashMap<>();
        try (PreparedStatement stmt = dbConnection.prepareStatement("PRAGMA table_info(" + tableName + ");")) {
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                existingColumnTypes.put(rs.getString("name"), rs.getString("type"));
            }
        }

        // Step 3: rename columns that were renamed in code, preserving their data, instead of
        // letting them fall through to "add as new" below and silently orphaning the old column.
        for (ColumnDef col : columns) {
            if (existingColumnTypes.containsKey(col.name)) continue;

            for (String oldName : col.renamedFrom) {
                if (!existingColumnTypes.containsKey(oldName)) continue;

                String renameSQL = "ALTER TABLE " + tableName + " RENAME COLUMN " + oldName + " TO " + col.name + ";";
                dbConnection.prepareStatement(renameSQL)
                    .execute();

                LOG.info(
                    "Renamed column '{}' to '{}' in table '{}', preserving existing data.",
                    oldName,
                    col.name,
                    tableName);

                existingColumnTypes.put(col.name, existingColumnTypes.remove(oldName));
                break;
            }
        }

        // Step 4: ALTER TABLE to add columns that are genuinely new (no matching old column found above).
        for (ColumnDef col : columns) {
            if (!existingColumnTypes.containsKey(col.name)) {
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
                dbConnection.prepareStatement(alterSQL.toString())
                    .execute();

                existingColumnTypes.put(col.name, col.type);
            }
        }

        // Step 5: flag (but do not fail on) columns whose declared type no longer matches the code.
        // SQLite cannot alter a column's type in place, so a mismatch here needs a manual table rebuild.
        for (ColumnDef col : columns) {
            String existingType = existingColumnTypes.get(col.name);
            if (existingType != null && !existingType.equalsIgnoreCase(col.type)) {
                LOG.error(
                    "Schema drift in table '{}': column '{}' is declared as '{}' in code but exists as '{}' in the database. "
                        + "SQLite cannot alter a column's type in place — this requires a manual table rebuild to fix.",
                    tableName,
                    col.name,
                    col.type,
                    existingType);
            }
        }
    }

    /**
     * Deletes up to {@link #PRUNE_BATCH_SIZE} of the oldest events older than oldestDataCutoff, and returns whether
     * there may be more to delete. Run by the logger's worker thread between writes, so it never holds up the server.
     * The freed space is reused for new events, so the file stops growing once it holds oldestDataCutoff's worth.
     */
    boolean pruneOldData() {
        String tableName = logger.getLoggerName();
        String sql = "DELETE FROM " + tableName
            + " WHERE eventID IN (SELECT eventID FROM "
            + tableName
            + " WHERE timestamp < ? ORDER BY timestamp LIMIT ?)";

        try (PreparedStatement ps = dbConnection.prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis() - oldestDataMilliseconds);
            ps.setInt(2, PRUNE_BATCH_SIZE);

            int deleted = ps.executeUpdate();
            dbConnection.commit();
            return deleted == PRUNE_BATCH_SIZE;
        } catch (SQLException e) {
            LOG.error("Could not delete old data from {}, will try again later.", tableName, e);
            try {
                dbConnection.rollback();
            } catch (SQLException rollbackFailure) {
                LOG.error("Failed to roll back {} after a failed prune.", tableName, rollbackFailure);
            }
            return false;
        }
    }

    public Connection getDBConn() {
        return dbConnection;
    }

    public @NotNull Connection getReadOnlyConnection() {
        try {
            String dbUrl = jdbcUrl(logger.getLoggerName() + ".db");

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
            LOG.error("Could not establish readonly connection to {} database.", logger.getLoggerName(), e);
            throw new RuntimeException("Failed to open readonly connection");
        }
    }

    public GenericEventInfo queryEventByEventID(long eventID) {

        String sqlQuery = "SELECT * FROM " + logger.getLoggerName() + " WHERE eventID == ? LIMIT 1";

        try (Connection conn = getReadOnlyConnection(); PreparedStatement ps = conn.prepareStatement(sqlQuery)) {

            ps.setLong(1, eventID);

            ResultSet rs = ps.executeQuery();
            List<? extends GenericEventInfo> packets = logger.generateQueryResults(rs);
            if (packets.isEmpty()) return null;

            return packets.get(0);

        } catch (Exception e) {
            LOG.error(
                "SQL query failed for table '{}' by eventID={}. Query: {}",
                logger.getLoggerName(),
                eventID,
                sqlQuery,
                e);
        }

        return null;
    }

    private void createIndexes() {
        String tableName = logger.getLoggerName();

        try (Statement stmt = dbConnection.createStatement()) {
            for (Map.Entry<String, String> index : logger.tableIndexes()
                .entrySet()) {
                stmt.execute(
                    String.format(
                        "CREATE INDEX IF NOT EXISTS idx_%s_%s ON %s (%s);",
                        tableName,
                        index.getKey(),
                        tableName,
                        index.getValue()));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create index for " + tableName, e);
        }
    }

    public void genericConfig(@NotNull Configuration config) {
        oldestDataCutoff = config.getString(
            "oldestDataCutoff",
            logger.getLoggerName(),
            OLDEST_DATA_DEFAULT,
            "Records older than this are erased, checked every few minutes while the server runs. This is unrecoverable, be careful!");

        cacheSizeMegabytes = config.getInt(
            "databaseCacheSizeMB",
            logger.getLoggerName(),
            CACHE_SIZE_MEGABYTES_DEFAULT,
            1,
            4096,
            "The most memory (in MB) SQLite may use to cache this logger's database. A bigger cache makes writing to a large database faster. Only used as the database grows into it.");
    }

    // Every column but those SQLite fills in itself, i.e. the eventID.
    private List<ColumnDef> getInsertedColumns() {
        return getAllTableColumns(logger).stream()
            .filter(col -> !col.isAssignedByDatabase())
            .collect(Collectors.toList());
    }

    public String generateInsertSQL() {
        List<ColumnDef> columns = getInsertedColumns();

        // Join the column names for the INSERT clause
        String columnList = columns.stream()
            .map(ColumnDef::getName)
            .collect(Collectors.joining(", "));

        // Generate a placeholder for each column
        String placeholders = columns.stream()
            .map(c -> "?")
            .collect(Collectors.joining(", "));

        return "INSERT INTO " + logger.getLoggerName() + " (" + columnList + ") VALUES (" + placeholders + ")";
    }

    // This is responsible for saving the actual events to a database. It seems rather convoluted,
    // but is trying to optimise and minimise the impact of heavy reflection usage.
    public <EventInfo extends GenericEventInfo> void insertBatch(List<EventInfo> eventInfoQueue) throws SQLException {
        if (dbConnection == null) throw new SQLException("Database connection is null for " + logger.getLoggerName());
        if (eventInfoQueue == null || eventInfoQueue.isEmpty()) {
            return;
        }

        if (addDataInjectionStatement == null || insertedColumns == null) {
            final String sql = generateInsertSQL();

            insertedColumns = getInsertedColumns();
            addDataInjectionStatement = dbConnection.prepareStatement(sql);
        }

        for (EventInfo eventInfo : eventInfoQueue) {
            int index = 1;

            for (ColumnDef columnDef : insertedColumns) {
                Object value = columnDef.columnAccessor.get(eventInfo);

                columnDef.columnAccessor.binder.bind(addDataInjectionStatement, index++, value);
            }

            addDataInjectionStatement.addBatch();
        }

        try {
            addDataInjectionStatement.executeBatch();
            dbConnection.commit();
        } catch (SQLException e) {
            // Leave the connection/statement usable for the next batch instead of abandoning them
            // half-committed.
            try {
                addDataInjectionStatement.clearBatch();
                dbConnection.rollback();
            } catch (SQLException rollbackFailure) {
                LOG.error(
                    "Failed to roll back {} after a failed batch insert.",
                    logger.getLoggerName(),
                    rollbackFailure);
            }
            throw e;
        }
    }

    public void shutdownDatabase() throws SQLException {
        if (dbConnection != null && !dbConnection.isClosed()) {
            closeDbConnection();
        } else {
            throw new IllegalStateException("Could not shutdown database connection");
        }
    }

    public void rollback() throws SQLException {
        dbConnection.rollback();
    }
}
