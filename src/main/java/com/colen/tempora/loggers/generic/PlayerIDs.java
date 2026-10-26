package com.colen.tempora.loggers.generic;

import static com.colen.tempora.Tempora.LOG;
import static com.colen.tempora.utils.DatabaseUtils.jdbcUrl;
import static com.colen.tempora.utils.PlayerUtils.UNKNOWN_PLAYER_NAME;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.sqlite.Function;
import org.sqlite.SQLiteConfig;

import com.colen.tempora.loggers.generic.column.ColumnType;
import com.colen.tempora.utils.PlayerUtils;

/**
 * Gives each player a number, which player columns ({@link ColumnType#PLAYER}) store in place of their UUID. A number
 * takes 1 to 3 bytes in a row, against 36 for the UUID's text, which adds up in tables such as player positions. Each
 * UUID is stored once, in Players.db beside the loggers' databases, so a player has the same number in every logger's
 * table. The whole table is also kept in memory, as a server has at most a few thousand players.
 * <p>
 * Mobs' UUIDs are not stored this way: there is no end to them, and most mobs are seen only once or twice. The mob
 * position logger, which logs each mob many times, keeps a table of its own mobs, see EntityPositionLogger.
 */
@SuppressWarnings("SqlDialectInspection")
public final class PlayerIDs {

    public static final String TABLE = "Players";
    // PlayerUtils.UNKNOWN_PLAYER_NAME, for events with no player. Every player column's default.
    public static final int UNKNOWN_ID = 0;
    // What a lookup matches for a player who has never been stored, so no event has it.
    private static final int NEVER_STORED_ID = -1;

    private static final Map<String, Integer> IDS = new ConcurrentHashMap<>();
    private static final Map<Integer, String> UUIDS = new ConcurrentHashMap<>();

    // Open while a server runs. Written under the class lock, as every logger's worker may store new players.
    private static Connection connection;
    private static PreparedStatement insert;
    private static int nextID;

    private PlayerIDs() {}

    // As the server starts, before any logger. Loads every player stored so far.
    static synchronized void open() throws SQLException {
        close();

        SQLiteConfig config = new SQLiteConfig();
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
        connection = DriverManager.getConnection(jdbcUrl(TABLE + ".db"), config.toProperties());

        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS " + TABLE + " (id INTEGER PRIMARY KEY, uuid TEXT NOT NULL UNIQUE)");
        }
        // Stored too, so the table explains every number in the loggers' tables.
        try (PreparedStatement ps = connection
            .prepareStatement("INSERT OR IGNORE INTO " + TABLE + " (id, uuid) VALUES (?, ?)")) {
            ps.setInt(1, UNKNOWN_ID);
            ps.setString(2, UNKNOWN_PLAYER_NAME);
            ps.executeUpdate();
        }

        nextID = UNKNOWN_ID + 1;
        try (Statement st = connection.createStatement();
            ResultSet rs = st.executeQuery("SELECT id, uuid FROM " + TABLE)) {
            while (rs.next()) {
                int id = rs.getInt(1);
                remember(id, rs.getString(2));
                nextID = Math.max(nextID, id + 1);
            }
        }

        insert = connection.prepareStatement("INSERT INTO " + TABLE + " (id, uuid) VALUES (?, ?)");
        LOG.info("Loaded {} players from {}.db.", UUIDS.size() - 1, TABLE);
    }

    // As the server stops, once the loggers and lookups have. A singleplayer world opened next has its own players.
    static synchronized void close() {
        try {
            if (connection != null) connection.close();
        } catch (SQLException e) {
            LOG.error("Could not close {}.db.", TABLE, e);
        }
        connection = null;
        insert = null;
        IDS.clear();
        UUIDS.clear();
    }

    /**
     * The number to store for a player, from their UUID. A player seen for the first time is stored in Players.db
     * before this returns, so an event is never stored with a number Players.db doesn't have. On the loggers' worker
     * threads.
     */
    public static int idFor(String uuid) throws SQLException {
        if (uuid == null) throw new SQLException("A player column was null. Use PlayerUtils.UNKNOWN_PLAYER_NAME.");
        String key = normalise(uuid);
        Integer id = IDS.get(key);
        return id != null ? id : store(key);
    }

    private static synchronized int store(String key) throws SQLException {
        // Another worker may have stored them while this one waited.
        Integer stored = IDS.get(key);
        if (stored != null) return stored;
        if (insert == null) throw new SQLException(TABLE + ".db is not open, so no player can be stored.");

        int id = nextID;
        insert.setInt(1, id);
        insert.setString(2, key);
        insert.executeUpdate();
        nextID++;
        remember(id, key);
        return id;
    }

    // The UUID a stored number stands for, as events read back. UNKNOWN_PLAYER_NAME for a number Tempora doesn't know.
    public static String uuidFor(int id) {
        return UUIDS.getOrDefault(id, UNKNOWN_PLAYER_NAME);
    }

    /**
     * A player's number, for matching them in a query, without storing them. For a player never stored, a number no
     * event has, so they match nothing.
     */
    public static int lookupID(String uuid) {
        return IDS.getOrDefault(normalise(uuid), NEVER_STORED_ID);
    }

    /**
     * Adds {@code player('<name or UUID>')} to a connection's SQL: the number a player is stored as, so /tempora sql
     * can
     * match them, e.g. {@code WHERE playerUUID = player('Notch')}. An error for anyone who has never joined.
     */
    public static void addSqlFunction(Connection conn) throws SQLException {
        Function.create(conn, "player", new Function() {

            @Override
            protected void xFunc() throws SQLException {
                String nameOrUUID = value_text(0);
                String uuid = nameOrUUID == null ? null : PlayerUtils.uuidFromName(nameOrUUID);
                if (uuid == null) {
                    error("No player called " + nameOrUUID + " has joined this server.");
                    return;
                }
                result(lookupID(uuid));
            }
        }, 1, Function.FLAG_DETERMINISTIC);
    }

    private static void remember(int id, String uuid) {
        IDS.put(uuid, id);
        UUIDS.put(id, uuid);
    }

    // UUIDs typed by a person may be in upper case. Players' own are always lower case.
    private static String normalise(String uuid) {
        return PlayerUtils.isUUID(uuid) ? uuid.toLowerCase(Locale.ROOT) : uuid;
    }
}
