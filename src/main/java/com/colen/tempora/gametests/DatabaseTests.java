package com.colen.tempora.gametests;

import static com.colen.tempora.gametests.PositionalLoggerTests.assertCount;
import static com.colen.tempora.gametests.TestSupport.eventsNear;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.common.config.Configuration;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.Tempora;
import com.colen.tempora.TemporaEvents;
import com.colen.tempora.commands.LookupCommand;
import com.colen.tempora.enums.LoggerEventType;
import com.colen.tempora.loggers.entity_position.EntityPositionEventInfo;
import com.colen.tempora.loggers.entity_position.EntityPositionLogger;
import com.colen.tempora.loggers.generic.FoundEvents;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.LoggerDatabase;
import com.colen.tempora.loggers.generic.PlayerIDs;
import com.colen.tempora.loggers.generic.PositionalEventInfo;
import com.colen.tempora.loggers.generic.column.Column;
import com.colen.tempora.loggers.generic.column.ColumnDef;
import com.colen.tempora.loggers.generic.column.ColumnType;
import com.colen.tempora.loggers.player_block_break.PlayerBlockBreakEventInfo;
import com.colen.tempora.utils.DatabaseUtils;
import com.colen.tempora.utils.PlayerFilter;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.ReflectionUtils;
import com.colen.tempora.utils.TimeWindow;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

/**
 * What happens to the data already in a database: schema migration when a logger opens it, and pruning. Also how
 * players and mobs are stored, as numbers from Players.db and the Mobs table.
 */
@GameTestHolder(Tempora.MODID)
public class DatabaseTests {

    // ---------- Migration ----------

    /**
     * A database written by an older version of a logger opens without losing anything: a renamed column keeps its
     * data, a new column is added with its default, and a column that changed type (eventID was TEXT before it became
     * the rowid) makes the table be rebuilt, with fresh ids in time order. Events written afterwards get ids too,
     * which they didn't when eventID was left as TEXT.
     */
    @GameTest
    public static void oldDatabaseIsMigratedWithoutLosingEvents(GameTestHelper helper) throws Exception {
        MigrationTestLogger logger = new MigrationTestLogger();
        deleteDatabaseFiles(logger.getLoggerName());
        helper.afterTest(() -> deleteDatabaseFiles(logger.getLoggerName()));

        // Out of time order on purpose, to check the new ids follow time rather than insertion order.
        try (Connection conn = DriverManager.getConnection(DatabaseUtils.jdbcUrl(logger.getLoggerName() + ".db"));
            Statement st = conn.createStatement()) {
            st.execute(
                "CREATE TABLE " + logger.getLoggerName()
                    + " (eventID TEXT PRIMARY KEY, timestamp INTEGER NOT NULL, versionID INTEGER NOT NULL,"
                    + " dimensionID INTEGER NOT NULL, x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL,"
                    + " note TEXT NOT NULL, oldCount INTEGER NOT NULL)");
            st.execute(
                "INSERT INTO " + logger.getLoggerName()
                    + " VALUES ('c4b1e0d2-0000-4000-8000-000000000003', 300, 1, 0, 3, 64, 0, 'third', 30),"
                    + " ('c4b1e0d2-0000-4000-8000-000000000001', 100, 1, 0, 1, 64, 0, 'first', 10),"
                    + " ('c4b1e0d2-0000-4000-8000-000000000002', 200, 1, 0, 2, 64, 0, 'second', 20)");
        }

        LoggerDatabase database = logger.getDatabaseManager();
        database.genericConfig(throwawayConfig(helper));
        database.initialiseDatabase();
        try {
            MigrationTestEvent later = new MigrationTestEvent();
            later.timestamp = 400;
            later.note = "fourth";
            later.count = 40;
            database.insertBatch(Collections.singletonList(later));
        } finally {
            database.shutdownDatabase();
        }

        try (Connection conn = DriverManager.getConnection(DatabaseUtils.jdbcUrl(logger.getLoggerName() + ".db"));
            Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery(
                "SELECT type FROM pragma_table_info('" + logger.getLoggerName() + "') WHERE name = 'eventID'")) {
                helper.assertTrue(rs.next(), "eventID column is missing");
                helper.assertEquals("INTEGER", rs.getString(1), "eventID type after migrating");
            }

            List<String> rows = new ArrayList<>();
            try (ResultSet rs = st.executeQuery(
                "SELECT eventID, timestamp, note, count, addedLater FROM " + logger.getLoggerName()
                    + " ORDER BY eventID")) {
                while (rs.next()) {
                    rows.add(
                        rs.getLong(
                            1) + " " + rs.getLong(2) + " " + rs.getString(3) + " " + rs.getInt(4) + " " + rs.getInt(5));
                }
            }

            // Fresh ids in time order, renamed column kept, new column filled with its default, later event has an
            // id of its own.
            List<String> expected = new ArrayList<>();
            expected.add("1 100 first 10 7");
            expected.add("2 200 second 20 7");
            expected.add("3 300 third 30 7");
            expected.add("4 400 fourth 40 7");
            helper.assertIterableEquals(
                expected,
                rows,
                "events after migrating (eventID timestamp note count addedLater)");
        }
        helper.succeed();
    }

    // A logger that only exists for the migration test. Never registered, so it never logs anything by itself.
    public static class MigrationTestLogger extends GenericPositionalLogger<MigrationTestEvent> {

        @Override
        public @NotNull String getLoggerName() {
            return "TemporaTestMigrationLogger";
        }

        @Override
        public @NotNull String getShortName() {
            return "test_migration";
        }

        @Override
        public @NotNull MigrationTestEvent newEventInfo() {
            return new MigrationTestEvent();
        }

        @Override
        public @NotNull LoggerEventType getLoggerEventType() {
            return LoggerEventType.None;
        }
    }

    // As a logger's event looks after a few versions: one column renamed, one added since.
    public static class MigrationTestEvent extends PositionalEventInfo {

        @Column(constraints = "NOT NULL")
        public String note = "";

        @Column(constraints = "NOT NULL", renamedFrom = "oldCount")
        public int count;

        @Column(constraints = "NOT NULL")
        public int addedLater = 7;

        @Override
        public IChatComponent localiseText() {
            return new ChatComponentText(note);
        }

        @Override
        public String getLoggerName() {
            return "TemporaTestMigrationLogger";
        }
    }

    // ---------- Defaults ----------

    // Every column is NOT NULL with its field's value in a new event as its default; the annotation adds nothing more.
    @GameTest
    public static void columnDefaultsComeFromJavaValues(GameTestHelper helper) {
        Map<String, String> conditions = new HashMap<>();
        for (ColumnDef col : ReflectionUtils.getAllTableColumns(new MigrationTestLogger())) {
            conditions.put(col.name, col.extraCondition);
        }
        helper.assertEquals("NOT NULL DEFAULT ''", conditions.get("note"), "note");
        helper.assertEquals("NOT NULL DEFAULT 7", conditions.get("addedLater"), "addedLater");
        helper.assertEquals("PRIMARY KEY", conditions.get("eventID"), "eventID, which SQLite fills in itself");
        helper.succeed();
    }

    // A logger with a column that starts as null is refused, naming the field, so its author finds out straight away.
    @GameTest
    public static void columnWithNullDefaultIsRefused(GameTestHelper helper) {
        IllegalStateException refused = helper.assertThrows(
            IllegalStateException.class,
            () -> ReflectionUtils.getAllTableColumns(new NullDefaultTestLogger()),
            "a column starting as null was accepted");
        helper.assertTrue(
            String.valueOf(refused.getMessage())
                .contains("NullDefaultTestEvent.owner"),
            "the refusal should name the field, got: " + refused.getMessage());
        helper.succeed();
    }

    public static class NullDefaultTestLogger extends GenericPositionalLogger<NullDefaultTestEvent> {

        @Override
        public @NotNull String getLoggerName() {
            return "TemporaTestNullDefaultLogger";
        }

        @Override
        public @NotNull String getShortName() {
            return "test_null_default";
        }

        @Override
        public @NotNull NullDefaultTestEvent newEventInfo() {
            return new NullDefaultTestEvent();
        }

        @Override
        public @NotNull LoggerEventType getLoggerEventType() {
            return LoggerEventType.None;
        }
    }

    public static class NullDefaultTestEvent extends PositionalEventInfo {

        @Column(constraints = "NOT NULL")
        public String owner;

        @Override
        public IChatComponent localiseText() {
            return new ChatComponentText(owner);
        }

        @Override
        public String getLoggerName() {
            return "TemporaTestNullDefaultLogger";
        }
    }

    // ---------- One broken event ----------

    /**
     * An event whose chat line throws, e.g. from a bug in another mod's logger, shows as a placeholder, and the rest
     * of the lookup is shown as normal.
     */
    @GameTest(timeoutTicks = 200)
    public static void brokenEventShowsPlaceholder(GameTestHelper helper) throws Exception {
        BrokenEventTestLogger logger = new BrokenEventTestLogger();
        deleteDatabaseFiles(logger.getLoggerName());
        LoggerDatabase database = logger.getDatabaseManager();
        database.genericConfig(throwawayConfig(helper));
        database.initialiseDatabase();
        // Its files stay: the query thread keeps a connection to it open until the server stops.
        helper.afterTest(() -> {
            try {
                database.shutdownDatabase();
            } catch (Exception ignored) {}
        });

        TestPos pos = helper.absolute(TestPos.at(2, 1, 2));
        int dim = helper.getWorld().provider.dimensionId;
        List<MigrationTestEvent> events = new ArrayList<>();
        for (String note : new String[] { "fine", "boom" }) {
            MigrationTestEvent event = new MigrationTestEvent();
            event.timestamp = System.currentTimeMillis();
            event.dimensionID = dim;
            event.x = pos.x() + 0.5;
            event.y = pos.y();
            event.z = pos.z() + 0.5;
            event.note = note;
            events.add(event);
        }
        database.insertBatch(events);

        Probe<FoundEvents<?>> lookup = new Probe<>(helper, "brokenEventShowsPlaceholder");
        CapturingSender chat = new CapturingSender("brokenEventShowsPlaceholder.chat", helper.getWorld());

        helper.startSequence()
            .thenWaitUntil("the lookup shows both events", 100, () -> {
                chat.getMessages()
                    .clear();
                helper.assertEquals(
                    2L,
                    lookup
                        .poll(
                            () -> logger.findEventsNear(
                                dim,
                                pos.x(),
                                pos.y(),
                                pos.z(),
                                0,
                                TimeWindow.allTime(),
                                PlayerFilter.anyone(),
                                LookupCommand.DEFAULT_COUNT))
                        .show(chat),
                    "events shown");
            })
            .thenExecute("the broken one is a placeholder, the other is shown", () -> {
                List<String> lines = new ArrayList<>();
                for (IChatComponent message : chat.getMessages()) {
                    lines.add(
                        message instanceof ChatComponentTranslation translation ? translation.getKey()
                            : message.getUnformattedText());
                }
                helper.assertEquals(3L, lines.size(), "header and two lines, got " + lines);
                helper.assertTrue(lines.contains("fine"), "the working event is shown, got " + lines);
                helper.assertTrue(
                    lines.contains("tempora.command.lookup.event_failed"),
                    "the broken event is a placeholder, got " + lines);
            })
            .thenSucceed();
    }

    // The migration test's events, from a logger whose chat line throws for a note of "boom".
    public static class BrokenEventTestLogger extends MigrationTestLogger {

        @Override
        public @NotNull String getLoggerName() {
            return "TemporaTestBrokenEventLogger";
        }

        @Override
        public @NotNull String getShortName() {
            return "test_broken_event";
        }

        @Override
        public @NotNull MigrationTestEvent newEventInfo() {
            return new BrokenTestEvent();
        }
    }

    public static class BrokenTestEvent extends MigrationTestEvent {

        @Override
        public IChatComponent localiseText() {
            if ("boom".equals(note)) throw new IllegalStateException("A deliberately broken event");
            return new ChatComponentText(note);
        }

        @Override
        public String getLoggerName() {
            return "TemporaTestBrokenEventLogger";
        }
    }

    // Events SQLite refuses (here a NaN position, which its NOT NULL column refuses) cost only themselves, however
    // many of them lead the batch.
    @GameTest
    public static void refusedEventsDropOnlyThemselves(GameTestHelper helper) throws Exception {
        RefusedEventTestLogger logger = new RefusedEventTestLogger();
        String table = logger.getLoggerName();
        deleteDatabaseFiles(table);
        helper.afterTest(() -> deleteDatabaseFiles(table));

        LoggerDatabase database = logger.getDatabaseManager();
        database.genericConfig(throwawayConfig(helper));
        database.initialiseDatabase();
        try {
            List<MigrationTestEvent> events = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                MigrationTestEvent event = new MigrationTestEvent();
                event.timestamp = i + 1;
                // The first three are refused.
                event.x = i < 3 ? Double.NaN : i;
                event.note = "event " + i;
                events.add(event);
            }
            database.insertBatch(events);
        } finally {
            database.shutdownDatabase();
        }

        List<String> stored = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DatabaseUtils.jdbcUrl(table + ".db"));
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery("SELECT note FROM " + table + " ORDER BY timestamp")) {
            while (rs.next()) stored.add(rs.getString(1));
        }
        helper.assertIterableEquals(
            Arrays.asList("event 3", "event 4", "event 5", "event 6", "event 7"),
            stored,
            "events stored");
        helper.succeed();
    }

    public static class RefusedEventTestLogger extends MigrationTestLogger {

        @Override
        public @NotNull String getLoggerName() {
            return "TemporaTestRefusedEventLogger";
        }

        @Override
        public @NotNull String getShortName() {
            return "test_refused_event";
        }
    }

    // ---------- Pruning ----------

    // Events older than the logger's oldestDataCutoff are deleted, and newer ones kept.
    @GameTest(timeoutTicks = 300)
    public static void oldEventsArePruned(GameTestHelper helper) {
        GenericPositionalLogger<PlayerBlockBreakEventInfo> logger = TemporaEvents.playerBlockBreakLogger;
        TestPos pos = helper.absolute(TestPos.at(2, 1, 2));
        Probe<List<PositionalEventInfo>> before = new Probe<>(helper, "oldEventsArePruned.before");
        Probe<List<PositionalEventInfo>> after = new Probe<>(helper, "oldEventsArePruned.after");

        helper.startSequence()
            .thenExecute("log a break from 1970, older than any cutoff, and one from now", () -> {
                logger.queueEventInfo(breakEvent(helper, pos, 1));
                logger.queueEventInfo(breakEvent(helper, pos, System.currentTimeMillis()));
            })
            .thenWaitUntil(
                "both are stored",
                100,
                () -> assertCount(helper, before.poll(() -> eventsNear(helper, logger, pos, 0)), 2))
            .thenExecute("prune", logger::requestPrune)
            .thenWaitUntil("only the new one is left", 200, () -> {
                List<PositionalEventInfo> left = after.poll(() -> eventsNear(helper, logger, pos, 0));
                assertCount(helper, left, 1);
                helper.assertTrue(left.get(0).timestamp > 1, "the old event was kept and the new one deleted");
            })
            .thenSucceed();
    }

    // ---------- Players ----------

    /**
     * A player column stores the player's number from Players.db rather than their UUID, the same number however the
     * UUID is written, and reads back as the UUID. An event with no player stores the unknown player's 0.
     */
    @GameTest
    public static void playerColumnsStoreNumbers(GameTestHelper helper) throws Exception {
        PlayerTestLogger logger = new PlayerTestLogger();
        String table = logger.getLoggerName();
        deleteDatabaseFiles(table);
        helper.afterTest(() -> deleteDatabaseFiles(table));
        String uuid = UUID.randomUUID()
            .toString();

        LoggerDatabase database = logger.getDatabaseManager();
        database.genericConfig(throwawayConfig(helper));
        database.initialiseDatabase();
        try {
            List<PlayerTestEvent> events = new ArrayList<>();
            for (String player : new String[] { uuid, uuid.toUpperCase(Locale.ROOT),
                PlayerUtils.UNKNOWN_PLAYER_NAME }) {
                PlayerTestEvent event = new PlayerTestEvent();
                event.timestamp = events.size() + 1;
                event.playerUUID = player;
                events.add(event);
            }
            database.insertBatch(events);
        } finally {
            database.shutdownDatabase();
        }

        int id = PlayerIDs.lookupID(uuid);
        helper.assertTrue(id > PlayerIDs.UNKNOWN_ID, "the player should have a number of their own, got " + id);
        helper.assertEquals(id, PlayerIDs.idFor(uuid), "the number other loggers store for them");

        try (Connection conn = DriverManager.getConnection(DatabaseUtils.jdbcUrl(table + ".db"));
            Statement st = conn.createStatement()) {
            try (ResultSet rs = st
                .executeQuery("SELECT type FROM pragma_table_info('" + table + "') WHERE name = 'playerUUID'")) {
                helper.assertTrue(rs.next(), "playerUUID column is missing");
                helper.assertEquals("INTEGER", rs.getString(1), "playerUUID column type");
            }

            List<String> stored = new ArrayList<>();
            try (ResultSet rs = st
                .executeQuery("SELECT typeof(playerUUID), playerUUID FROM " + table + " ORDER BY timestamp")) {
                while (rs.next()) stored.add(rs.getString(1) + " " + rs.getLong(2));
            }
            helper.assertIterableEquals(
                Arrays.asList("integer " + id, "integer " + id, "integer " + PlayerIDs.UNKNOWN_ID),
                stored,
                "stored players (type number)");

            List<String> readBack = new ArrayList<>();
            try (ResultSet rs = st.executeQuery("SELECT * FROM " + table + " ORDER BY timestamp")) {
                for (PlayerTestEvent event : logger.generateQueryResults(rs)) readBack.add(event.playerUUID);
            }
            helper.assertIterableEquals(
                Arrays.asList(uuid, uuid, PlayerUtils.UNKNOWN_PLAYER_NAME),
                readBack,
                "players read back as events");
        }

        // Players.db explains the number, for anyone reading the databases outside the game.
        try (Connection conn = DriverManager.getConnection(DatabaseUtils.jdbcUrl(PlayerIDs.TABLE + ".db"));
            PreparedStatement ps = conn.prepareStatement("SELECT uuid FROM " + PlayerIDs.TABLE + " WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                helper.assertTrue(rs.next(), "Players.db has no row for number " + id);
                helper.assertEquals(uuid, rs.getString(1), "UUID stored in Players.db");
            }
        }

        Map<String, String> conditions = new HashMap<>();
        for (ColumnDef col : ReflectionUtils.getAllTableColumns(logger)) {
            conditions.put(col.name, col.extraCondition);
        }
        helper.assertEquals("NOT NULL DEFAULT 0", conditions.get("playerUUID"), "playerUUID, the unknown player");
        helper.succeed();
    }

    // /tempora sql matches players with player('<name or UUID>'), the number they are stored as.
    @GameTest
    public static void sqlPlayerFunctionGivesNumber(GameTestHelper helper) throws Exception {
        String uuid = UUID.randomUUID()
            .toString();
        int id = PlayerIDs.idFor(uuid);

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:");
            Statement st = conn.createStatement()) {
            PlayerIDs.addSqlFunction(conn);

            try (ResultSet rs = st.executeQuery("SELECT player('" + uuid.toUpperCase(Locale.ROOT) + "')")) {
                helper.assertTrue(rs.next(), "player() returned no row");
                helper.assertEquals(id, rs.getInt(1), "player() of a stored player");
            }

            String error = null;
            try (ResultSet rs = st.executeQuery("SELECT player('no_such_player_ever')")) {
                rs.next();
            } catch (SQLException e) {
                error = e.getMessage();
            }
            helper.assertTrue(
                error != null && error.contains("no_such_player_ever"),
                "player() of someone who never joined should be an error naming them, got " + error);
        }
        helper.succeed();
    }

    // A logger whose p: filter column isn't a PLAYER column could never match a player, so it is refused.
    @GameTest
    public static void textPlayerColumnIsRefused(GameTestHelper helper) {
        IllegalStateException refused = helper.assertThrows(
            IllegalStateException.class,
            () -> new TextPlayerColumnTestLogger().checkColumns(),
            "a TEXT player column was accepted");
        helper.assertTrue(
            String.valueOf(refused.getMessage())
                .contains("note"),
            "the refusal should name the column, got: " + refused.getMessage());
        helper.succeed();
    }

    public static class PlayerTestLogger extends GenericPositionalLogger<PlayerTestEvent> {

        @Override
        protected String playerColumn() {
            return "playerUUID";
        }

        @Override
        public @NotNull String getLoggerName() {
            return "TemporaTestPlayerLogger";
        }

        @Override
        public @NotNull String getShortName() {
            return "test_player";
        }

        @Override
        public @NotNull PlayerTestEvent newEventInfo() {
            return new PlayerTestEvent();
        }

        @Override
        public @NotNull LoggerEventType getLoggerEventType() {
            return LoggerEventType.None;
        }
    }

    public static class PlayerTestEvent extends PositionalEventInfo {

        @Column(type = ColumnType.PLAYER, constraints = "NOT NULL")
        public String playerUUID = PlayerUtils.UNKNOWN_PLAYER_NAME;

        @Override
        public IChatComponent localiseText() {
            return new ChatComponentText(playerUUID);
        }

        @Override
        public String getLoggerName() {
            return "TemporaTestPlayerLogger";
        }
    }

    // The migration test's logger, with its TEXT note column as the player column.
    public static class TextPlayerColumnTestLogger extends MigrationTestLogger {

        @Override
        protected String playerColumn() {
            return "note";
        }
    }

    // ---------- Mobs ----------

    /**
     * Mob positions store each mob's UUID and name once, in the Mobs table, and events read back with them. A mob
     * whose events have all aged out is pruned from Mobs, and stored again under a new number if it turns up later.
     */
    @GameTest
    public static void mobPositionsStoreEachMobOnce(GameTestHelper helper) throws Exception {
        MobTestLogger logger = new MobTestLogger();
        deleteDatabaseFiles(MobTestLogger.NAME);
        helper.afterTest(() -> deleteDatabaseFiles(MobTestLogger.NAME));
        String zombie = UUID.randomUUID()
            .toString();
        String cow = UUID.randomUUID()
            .toString();
        long now = System.currentTimeMillis();

        LoggerDatabase database = logger.getDatabaseManager();
        database.genericConfig(throwawayConfig(helper));
        database.initialiseDatabase();
        try {
            Connection conn = database.getDBConn();

            // The cow is last seen long before the oldest events kept, so pruning will delete it.
            database.insertBatch(
                Arrays.asList(
                    mobEvent(zombie, "Zombie", now),
                    mobEvent(zombie, "Zombie", now + 1),
                    mobEvent(cow, "Cow", 1)));
            helper.assertIterableEquals(
                Arrays.asList(zombie + " Zombie", cow + " Cow"),
                mobsStored(conn),
                "mobs stored once each (uuid name)");

            List<EntityPositionEventInfo> events = readMobEvents(logger, conn);
            List<String> readBack = new ArrayList<>();
            for (EntityPositionEventInfo event : events) readBack.add(event.entityUUID + " " + event.entityName);
            helper.assertIterableEquals(
                Arrays.asList(cow + " Cow", zombie + " Zombie", zombie + " Zombie"),
                readBack,
                "events read back (uuid name), oldest first");
            helper.assertEquals(events.get(1).mobID, events.get(2).mobID, "the zombie's two events share a number");
            long cowNumber = events.get(0).mobID;

            // An hour later lastSeen is brought up to date.
            database.insertBatch(Collections.singletonList(mobEvent(zombie, "Zombie", now + 3_600_001)));
            try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("SELECT lastSeen FROM Mobs WHERE uuid = '" + zombie + "'")) {
                helper.assertTrue(rs.next(), "the zombie is missing from Mobs");
                helper.assertEquals(now + 3_600_001, rs.getLong(1), "the zombie's lastSeen");
            }

            logger.pruneMobs(conn);
            conn.commit();
            helper.assertIterableEquals(
                Collections.singletonList(zombie + " Zombie"),
                mobsStored(conn),
                "mobs left after pruning");

            // The cow turns up again: it is stored again, although the logger still has it cached.
            database.insertBatch(Collections.singletonList(mobEvent(cow, "Cow", now)));
            helper.assertIterableEquals(
                Arrays.asList(zombie + " Zombie", cow + " Cow"),
                mobsStored(conn),
                "mobs once the cow is back");
            List<EntityPositionEventInfo> after = readMobEvents(logger, conn);
            // The last stored. Not the newest by time: the zombie's event an hour on is later.
            EntityPositionEventInfo newest = Collections.max(after, Comparator.comparingLong(event -> event.eventID));
            helper.assertEquals(cow, newest.entityUUID, "the cow's new event reads back as the cow");
            helper.assertTrue(newest.mobID != cowNumber, "the cow should have a new number, got " + newest.mobID);
        } finally {
            database.shutdownDatabase();
        }
        helper.succeed();
    }

    private static EntityPositionEventInfo mobEvent(String uuid, String name, long timestamp) {
        EntityPositionEventInfo event = new EntityPositionEventInfo();
        event.timestamp = timestamp;
        event.entityUUID = uuid;
        event.entityName = name;
        return event;
    }

    private static List<String> mobsStored(Connection conn) throws SQLException {
        List<String> mobs = new ArrayList<>();
        try (Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery("SELECT uuid, name FROM Mobs ORDER BY id")) {
            while (rs.next()) mobs.add(rs.getString(1) + " " + rs.getString(2));
        }
        return mobs;
    }

    // Oldest first, with their UUIDs and names filled in from Mobs, as a lookup does.
    private static List<EntityPositionEventInfo> readMobEvents(MobTestLogger logger, Connection conn)
        throws SQLException {
        List<EntityPositionEventInfo> events;
        try (Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery("SELECT * FROM " + MobTestLogger.NAME + " ORDER BY timestamp, eventID")) {
            events = logger.generateQueryResults(rs);
        }
        logger.loadExtras(events, conn);
        return events;
    }

    // A mob position logger of its own, with its own database. Never registered.
    public static class MobTestLogger extends EntityPositionLogger {

        static final String NAME = "TemporaTestMobLogger";

        @Override
        public @NotNull String getLoggerName() {
            return NAME;
        }

        @Override
        public @NotNull String getShortName() {
            return "test_mob";
        }

        void pruneMobs(Connection conn) throws SQLException {
            pruneExtras(conn);
        }
    }

    // ---------- Helpers ----------

    private static PlayerBlockBreakEventInfo breakEvent(GameTestHelper helper, TestPos pos, long timestamp) {
        PlayerBlockBreakEventInfo event = new PlayerBlockBreakEventInfo();
        event.timestamp = timestamp;
        event.dimensionID = helper.getWorld().provider.dimensionId;
        event.x = pos.x();
        event.y = pos.y();
        event.z = pos.z();
        event.blockID = Block.getIdFromBlock(Blocks.stone);
        event.pickBlockID = event.blockID;
        event.playerUUID = PlayerUtils.UNKNOWN_PLAYER_NAME;
        event.encodedNBT = "";
        return event;
    }

    // Config for a logger the test makes: its defaults, never saved. Forge creates the file empty, so it is deleted
    // once the test ends.
    private static Configuration throwawayConfig(GameTestHelper helper) {
        File file = DatabaseUtils.databaseDir()
            .resolve("tempora_test_" + helper.getOriginX() + "_" + helper.getOriginZ() + ".cfg")
            .toFile();
        helper.afterTest(file::delete);
        return new Configuration(file);
    }

    private static void deleteDatabaseFiles(String loggerName) {
        for (String suffix : new String[] { ".db", ".db-wal", ".db-shm" }) {
            File file = DatabaseUtils.databaseDir()
                .resolve(loggerName + suffix)
                .toFile();
            if (file.exists() && !file.delete()) {
                throw new IllegalStateException("Could not delete " + file);
            }
        }
    }
}
