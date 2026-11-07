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
import org.sqlite.SQLiteErrorCode;

import com.colen.tempora.Tempora;
import com.colen.tempora.loggers.generic.column.ColumnDef;
import com.colen.tempora.utils.TimeUtils;

public class LoggerDatabase {

    private static final String OLDEST_DATA_DEFAULT = "4months";
    private static final int CACHE_SIZE_MEGABYTES_DEFAULT = 16;

    // Old events get deleted this many at a time so pruning never holds up writes for long
    private static final int PRUNE_BATCH_SIZE = 5_000;

    private String oldestDataCutoff;
    private long oldestDataMilliseconds; // From oldestDataCutoff when the db opens
    private int cacheSizeMegabytes;

    private Connection dbConnection;
    private final GenericLogger<?> logger;
    private PreparedStatement addDataInjectionStatement;
    private List<ColumnDef> insertedColumns; // Made on first insert

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

        // Cheap if they already exist, adds any index a newer version of the logger needs
        createIndexes();
        logger.createExtraTables(dbConnection);

        dbConnection.commit();
    }

    private void initDbConnection() throws SQLException {
        String dbUrl = jdbcUrl(logger.getLoggerName() + ".db");

        SQLiteConfig config = new SQLiteConfig();
        // WAL lets lookups read while events are written, and with synchronous=NORMAL a commit doesn't wait for the
        // disk. A server crash loses nothing committed, a power cut at most the last few commits, neither corrupts the
        // db
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
        // Negative means KiB not pages
        config.setCacheSize(-cacheSizeMegabytes * 1024);
        // Otherwise the -wal file stays the size of the biggest burst ever written (e.g. a table rebuild)
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
        // Belonged to the closed connection, remade for the next world
        addDataInjectionStatement = null;
    }

    private void initTable() throws SQLException {
        String tableName = logger.getLoggerName();
        List<ColumnDef> columns = getAllTableColumns(logger);

        try (PreparedStatement stmt = dbConnection.prepareStatement(createTableSQL(tableName, columns))) {
            stmt.execute();
        }

        // Existing columns and their declared types
        Map<String, String> existingColumnTypes = new HashMap<>();
        try (PreparedStatement stmt = dbConnection.prepareStatement("PRAGMA table_info(" + tableName + ");")) {
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                existingColumnTypes.put(rs.getString("name"), rs.getString("type"));
            }
        }

        // Rename columns that got renamed in code and keep their data, instead of them falling through to "add as new"
        // and orphaning the old column
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

        // Add columns that are actually new (no old column matched above)
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

        // SQLite can't change a column's type in place, so any table with a column whose type no longer matches gets
        // rebuilt. E.g. eventID was TEXT before it became the rowid, left alone SQLite never fills it in and every new
        // event reads back as eventID 0
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

    // Remakes the table with the code's column types keeping every event. Columns SQLite assigns (eventID) aren't
    // copied so each event gets a new one. Indexes go with the old table and createIndexes remakes them. Inside the
    // opening transaction so a failure leaves the old table alone
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
            // Oldest first so new eventIDs count up in time order like ones logged from now on
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

    // Deletes up to PRUNE_BATCH_SIZE of the oldest events past oldestDataCutoff, returns whether there might be more.
    // Run by the worker between writes so it never holds up the server. SQLite reuses the freed space, so the file
    // stops growing once it holds oldestDataCutoff's worth
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
            // Whatever only the deleted events pointed at. Only if something got deleted since it checks every row
            if (deleted > 0) logger.pruneExtras(dbConnection);
            dbConnection.commit();
            return deleted == PRUNE_BATCH_SIZE;
        } catch (SQLException | RuntimeException e) {
            // Caught here, RuntimeExceptions too (e.g. a logger's pruneExtras), so the worker carries on. Rolled back
            // either way so a half done prune doesn't get committed with the next batch
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

    // Only TemporaQueries opens these, one per logger for its query thread, so lookups never read on the server thread
    @NotNull
    Connection getReadOnlyConnection() {
        try {
            String dbUrl = jdbcUrl(logger.getLoggerName() + ".db");

            SQLiteConfig config = new SQLiteConfig();
            config.setReadOnly(true);

            Connection conn = DriverManager.getConnection(dbUrl, config.toProperties());
            conn.setReadOnly(true);

            // Longer timeout, helps avoid busy errors
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA busy_timeout = 5000;");
            }
            // For /tempora sql, which matches players by number
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
            for (String sql : indexSQL()) {
                stmt.execute(sql);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create index for " + tableName, e);
        }
    }

    private List<String> indexSQL() {
        String tableName = logger.getLoggerName();
        return logger.tableIndexes()
            .entrySet()
            .stream()
            .map(
                index -> String.format(
                    "CREATE INDEX IF NOT EXISTS idx_%s_%s ON %s (%s);",
                    tableName,
                    index.getKey(),
                    tableName,
                    index.getValue()))
            .collect(Collectors.toList());
    }

    // Makes the table and indexes in an empty in-memory db and prepares the insert, so SQL that SQLite refuses (column
    // named after a keyword, duplicate names) fails at registration not on server start. Skipped if SQLite can't load
    // here, then no logger can start and that gets reported anyway
    void checkSql() {
        Connection memory;
        try {
            memory = new SQLiteConfig().createConnection("jdbc:sqlite::memory:");
        } catch (SQLException | LinkageError e) {
            LOG.warn(
                "Could not open SQLite to check {}'s columns, so they are checked when the server starts instead.",
                logger.getLoggerName(),
                e);
            return;
        }

        String sql = null;
        try (Connection conn = memory; Statement st = conn.createStatement()) {
            sql = createTableSQL(logger.getLoggerName(), getAllTableColumns(logger));
            st.execute(sql);
            for (String index : indexSQL()) {
                sql = index;
                st.execute(sql);
            }
            sql = generateInsertSQL();
            conn.prepareStatement(sql)
                .close();
        } catch (SQLException e) {
            throw new IllegalStateException(
                "SQLite refuses " + logger.getLoggerName()
                    + "'s table: "
                    + e.getMessage()
                    + ", in "
                    + sql
                    + " Check that no column is named after an SQL keyword, such as from or order, and that no two"
                    + " columns share a name, ignoring case.",
                e);
        }
    }

    // How long events are kept, from oldestDataCutoff. Known once the db has opened
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

    // Every column except ones SQLite fills in itself (eventID)
    private List<ColumnDef> getInsertedColumns() {
        return getAllTableColumns(logger).stream()
            .filter(col -> !col.isAssignedByDatabase())
            .collect(Collectors.toList());
    }

    public String generateInsertSQL() {
        List<ColumnDef> columns = getInsertedColumns();

        String columnList = columns.stream()
            .map(ColumnDef::getName)
            .collect(Collectors.joining(", "));

        String placeholders = columns.stream()
            .map(c -> "?")
            .collect(Collectors.joining(", "));

        return "INSERT INTO " + logger.getLoggerName() + " (" + columnList + ") VALUES (" + placeholders + ")";
    }

    // Writes a batch in one transaction. If SQLite refuses an event (e.g. NaN in a NOT NULL column) they're written
    // again one at a time so only the refused ones are lost, each noted in the log and to admins. Throws if the db
    // itself is failing (disk full, I/O error, locked etc) since every event would fail the same way
    public <EventInfo extends GenericEventInfo> void insertBatch(List<EventInfo> eventInfoQueue) throws SQLException {
        try {
            insertInOneTransaction(eventInfoQueue);
        } catch (SQLException | RuntimeException batchFailure) {
            if (!isRefusedEvent(batchFailure)) throw batchFailure;

            int written = 0, dropped = 0;
            for (EventInfo event : eventInfoQueue) {
                try {
                    insertInOneTransaction(Collections.singletonList(event));
                    written++;
                } catch (SQLException | RuntimeException eventFailure) {
                    // Db started failing part way through
                    if (!isRefusedEvent(eventFailure)) throw eventFailure;
                    dropped++;
                    logger.noteDroppedEvent(event, String.valueOf(eventFailure.getMessage()));
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

    // Whether a failed insert was SQLite refusing the event rather than the db failing. A constraint (NOT NULL refusing
    // a NaN), a value too big or the wrong type, or one Tempora couldn't bind
    private static boolean isRefusedEvent(Exception failure) {
        if (failure instanceof RuntimeException) return true;
        int code = ((SQLException) failure).getErrorCode();
        return code == SQLiteErrorCode.SQLITE_CONSTRAINT.code || code == SQLiteErrorCode.SQLITE_TOOBIG.code
            || code == SQLiteErrorCode.SQLITE_MISMATCH.code
            || code == SQLiteErrorCode.SQLITE_RANGE.code;
    }

    // Saves the actual events. Looks convoluted but it's keeping the reflection cost down
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
            // First, so nothing the events refer to is missing once they're stored, and references it fills in (e.g. a
            // mob's number) are set before columns get read
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
            // Includes a value that couldn't be bound, so events bound before it don't linger in the statement and go
            // out with the next batch. Keeps the connection/statement usable instead of leaving them half committed
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
        // Events are stored by now so a failure here isn't the batch failing
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
