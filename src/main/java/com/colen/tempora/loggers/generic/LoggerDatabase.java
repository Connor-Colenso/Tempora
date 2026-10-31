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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        logger.createExtraTables(dbConnection);

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
        // Otherwise the -wal file keeps the size of the biggest burst ever written (e.g. a table rebuild).
        config.setJournalSizeLimit(64 * 1024 * 1024);

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
        try (PreparedStatement stmt = dbConnection.prepareStatement(createTableSQL(tableName, columns))) {
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

        // Step 5: SQLite cannot change a column's type in place, so a table with any column whose type no longer
        // matches the code is rebuilt. E.g. eventID was TEXT before it became the rowid: left as it was, SQLite never
        // fills it in, and every new event reads back with the same eventID of 0.
        List<String> drifted = columns.stream()
            .filter(col -> !col.type.equalsIgnoreCase(existingColumnTypes.get(col.name)))
            .map(col -> col.name + " (" + existingColumnTypes.get(col.name) + " -> " + col.type + ")")
            .collect(Collectors.toList());
        if (!drifted.isEmpty()) {
            rebuildTable(columns, existingColumnTypes.keySet(), drifted);
        }
    }

    private static String createTableSQL(String tableName, List<ColumnDef> columns) {
        String columnsSQL = columns.stream()
            .map(col -> {
                String sql = col.name + " " + col.type;
                if (!col.extraCondition.isEmpty()) {
                    sql += " " + col.extraCondition;
                }
                return sql;
            })
            .collect(Collectors.joining(", "));

        return "CREATE TABLE IF NOT EXISTS " + tableName + " (" + columnsSQL + ");";
    }

    /**
     * Remakes the table with the code's column types, keeping every event. Columns SQLite assigns (the eventID) are
     * not copied, so each event gets a new one. Indexes go with the old table and are made again by createIndexes.
     * Runs inside the opening transaction, so a failure leaves the old table as it was.
     */
    private void rebuildTable(List<ColumnDef> columns, Set<String> existingColumns, List<String> drifted)
        throws SQLException {
        String tableName = logger.getLoggerName();
        String rebuiltName = tableName + "_rebuild";

        LOG.warn(
            "Rebuilding table '{}' as these columns changed type: {}. This may take a while for a large database.",
            tableName,
            String.join(", ", drifted));

        String copiedColumns = columns.stream()
            .filter(col -> !col.isAssignedByDatabase())
            .map(col -> col.name)
            .collect(Collectors.joining(", "));

        try (Statement st = dbConnection.createStatement()) {
            st.execute("DROP TABLE IF EXISTS " + rebuiltName + ";");
            st.execute(createTableSQL(rebuiltName, columns));
            // Oldest first, so the new eventIDs count up in time order like those of events logged from now on.
            int copied = st.executeUpdate(
                "INSERT INTO " + rebuiltName
                    + " ("
                    + copiedColumns
                    + ") SELECT "
                    + copiedColumns
                    + " FROM "
                    + tableName
                    + " ORDER BY timestamp;");
            st.execute("DROP TABLE " + tableName + ";");
            st.execute("ALTER TABLE " + rebuiltName + " RENAME TO " + tableName + ";");

            LOG.info("Rebuilt table '{}', keeping all {} events.", tableName, copied);
        }

        List<String> dropped = existingColumns.stream()
            .filter(
                name -> columns.stream()
                    .noneMatch(col -> col.name.equals(name)))
            .collect(Collectors.toList());
        if (!dropped.isEmpty()) {
            LOG.warn(
                "Columns no longer used by '{}' were dropped while rebuilding it: {}.",
                tableName,
                String.join(", ", dropped));
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
            // Whatever only the deleted events referred to. Only when something was deleted, as it checks every row.
            if (deleted > 0) logger.pruneExtras(dbConnection);
            dbConnection.commit();
            return deleted == PRUNE_BATCH_SIZE;
        } catch (SQLException | RuntimeException e) {
            // Caught here, RuntimeExceptions too (e.g. from a logger's pruneExtras), so the worker carries on. Rolled
            // back either way, so a half done prune isn't committed with the next batch of events.
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

    // Only TemporaQueries opens these, one per logger for its query thread, so lookups never read on the server thread.
    @NotNull
    Connection getReadOnlyConnection() {
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
            // For /tempora sql, which matches players by their number.
            PlayerIDs.addSqlFunction(conn);

            return conn;
        } catch (Exception e) {
            LOG.error("Could not establish readonly connection to {} database.", logger.getLoggerName(), e);
            throw new RuntimeException("Failed to open readonly connection");
        }
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

    // How long events are kept, from oldestDataCutoff. Known once the database has opened.
    public long getOldestDataMilliseconds() {
        return oldestDataMilliseconds;
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

    // Single events tried after a batch fails, before deciding the database itself is failing (disk full, locked...)
    // rather than one of the events.
    private static final int RETRIES_BEFORE_GIVING_UP = 3;

    /**
     * Writes a batch of events in one transaction. If SQLite refuses it, e.g. one event has a NaN position, which a
     * NOT NULL column refuses, the events are written again one at a time, so only the ones SQLite refuses are lost.
     * Throws if the database refuses even single events.
     */
    public <EventInfo extends GenericEventInfo> void insertBatch(List<EventInfo> eventInfoQueue) throws SQLException {
        try {
            insertInOneTransaction(eventInfoQueue);
        } catch (SQLException | RuntimeException batchFailure) {
            if (eventInfoQueue.size() <= 1) throw batchFailure;

            int written = 0, dropped = 0;
            for (EventInfo event : eventInfoQueue) {
                try {
                    insertInOneTransaction(Collections.singletonList(event));
                    written++;
                } catch (SQLException | RuntimeException eventFailure) {
                    dropped++;
                    if (written == 0 && dropped >= RETRIES_BEFORE_GIVING_UP) throw batchFailure;
                    LOG.error(
                        "{} could not store event at {}, {}, {} (timestamp {}), it is dropped: {}",
                        logger.getLoggerName(),
                        event instanceof PositionalEventInfo positional ? positional.x : "-",
                        event instanceof PositionalEventInfo positional ? positional.y : "-",
                        event instanceof PositionalEventInfo positional ? positional.z : "-",
                        event.timestamp,
                        eventFailure.getMessage());
                }
            }
            LOG.warn(
                "A batch of {} events for {} failed ({}), so they were written one at a time: {} stored, {} dropped.",
                eventInfoQueue.size(),
                logger.getLoggerName(),
                batchFailure.getMessage(),
                written,
                dropped);
        }
    }

    // This is responsible for saving the actual events to a database. It seems rather convoluted,
    // but is trying to optimise and minimise the impact of heavy reflection usage.
    private <EventInfo extends GenericEventInfo> void insertInOneTransaction(List<EventInfo> eventInfoQueue)
        throws SQLException {
        if (dbConnection == null) throw new SQLException("Database connection is null for " + logger.getLoggerName());
        if (eventInfoQueue == null || eventInfoQueue.isEmpty()) {
            return;
        }

        if (addDataInjectionStatement == null || insertedColumns == null) {
            final String sql = generateInsertSQL();

            insertedColumns = getInsertedColumns();
            addDataInjectionStatement = dbConnection.prepareStatement(sql);
        }

        Runnable afterCommit;
        try {
            // First, so nothing the events refer to is missing once they are stored, and so references it fills in
            // (e.g. a mob's number) are in the events before their columns are read.
            afterCommit = logger.writeExtras(eventInfoQueue, dbConnection);

            for (EventInfo eventInfo : eventInfoQueue) {
                int index = 1;

                for (ColumnDef columnDef : insertedColumns) {
                    Object value = columnDef.columnAccessor.get(eventInfo);

                    columnDef.columnAccessor.binder.bind(addDataInjectionStatement, index++, value);
                }

                addDataInjectionStatement.addBatch();
            }

            addDataInjectionStatement.executeBatch();
            dbConnection.commit();
        } catch (SQLException | RuntimeException e) {
            // Includes a value that couldn't be bound, so the events bound before it don't stay in the statement and
            // go out with the next batch.
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
        // The events are stored by now, so a failure here must not be reported as the batch failing.
        if (afterCommit != null) {
            try {
                afterCommit.run();
            } catch (RuntimeException e) {
                LOG.error("{} stored a batch, but failed to note what it wrote.", logger.getLoggerName(), e);
            }
        }
    }

    public void shutdownDatabase() throws SQLException {
        if (dbConnection != null && !dbConnection.isClosed()) {
            closeDbConnection();
        } else {
            throw new IllegalStateException("Could not shutdown database connection");
        }
    }
}
