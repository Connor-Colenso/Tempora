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

import com.colen.tempora.utils.PlayerUtils;

// Gives each player a number which player columns (ColumnType.PLAYER) store instead of their UUID. 1 to 3 bytes a row
// against 36 for the UUID text, which adds up in tables like player positions. Each UUID is stored once in Players.db
// next to the logger dbs so a player has the same number everywhere. Whole table is kept in memory too, a server only
// has a few thousand players at most
//
// Mob UUIDs aren't stored like this, there's no end to them and most mobs get seen once or twice. The mob position
// logger logs each mob loads of times so it keeps its own table, see EntityPositionLogger
@SuppressWarnings("SqlDialectInspection")
public final class PlayerIDs {

    public static final String TABLE = "Players";
    // PlayerUtils.UNKNOWN_PLAYER_NAME, for events with no player. Default for every player column
    public static final int UNKNOWN_ID = 0;
    // What a lookup matches for a player that was never stored, so no event has it
    private static final int NEVER_STORED_ID = -1;

    private static final Map<String, Integer> IDS = new ConcurrentHashMap<>();
    private static final Map<Integer, String> UUIDS = new ConcurrentHashMap<>();

    // Open while a server runs. Written under the class lock since every logger's worker can store new players
    private static Connection connection;
    private static PreparedStatement insert;
    private static int nextID;

    private PlayerIDs() {}

    // Server start, before any logger. Loads every player stored so far
    static synchronized void open() throws SQLException {
        close();

        SQLiteConfig config = new SQLiteConfig();
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
        connection = DriverManager.getConnection(jdbcUrl(TABLE + ".db"), config.toProperties());

        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS " + TABLE + " (id INTEGER PRIMARY KEY, uuid TEXT NOT NULL UNIQUE)");
        }
        // Stored too so the table explains every number in the logger tables
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

    // Server stop, after the loggers and lookups. A singleplayer world opened next has its own players
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

    // Number to store for a player's UUID. New players get written to Players.db before this returns, so an event never
    // has a number Players.db doesn't. Loggers' worker threads
    public static int idFor(String uuid) throws SQLException {
        if (uuid == null) throw new SQLException("A player column was null. Use PlayerUtils.UNKNOWN_PLAYER_NAME.");
        String key = normalise(uuid);
        Integer id = IDS.get(key);
        return id != null ? id : store(key);
    }

    private static synchronized int store(String key) throws SQLException {
        // Another worker might have stored them while this one waited
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

    // UUID behind a stored number as events read back, UNKNOWN_PLAYER_NAME for a number Tempora doesn't know
    public static String uuidFor(int id) {
        return UUIDS.getOrDefault(id, UNKNOWN_PLAYER_NAME);
    }

    // Player's number for matching in a query, without storing them. Players never stored get a number no event has so
    // they match nothing
    public static int lookupID(String uuid) {
        return IDS.getOrDefault(normalise(uuid), NEVER_STORED_ID);
    }

    // Adds player('<name or UUID>') to a connection's SQL, the number a player is stored as, so /tempora sql can match
    // them, e.g. WHERE playerUUID = player('Notch'). Errors for anyone who's never joined
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

    // UUIDs typed by a person might be upper case, players' own are always lower
    private static String normalise(String uuid) {
        return PlayerUtils.isUUID(uuid) ? uuid.toLowerCase(Locale.ROOT) : uuid;
    }
}
