package com.colen.tempora.gametests;

import static com.colen.tempora.gametests.PositionalLoggerTests.assertCount;
import static com.colen.tempora.gametests.TestSupport.eventsNear;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.common.config.Configuration;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.Tempora;
import com.colen.tempora.TemporaEvents;
import com.colen.tempora.enums.LoggerEventType;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.LoggerDatabase;
import com.colen.tempora.loggers.generic.PositionalEventInfo;
import com.colen.tempora.loggers.generic.column.Column;
import com.colen.tempora.loggers.player_block_break.PlayerBlockBreakEventInfo;
import com.colen.tempora.utils.DatabaseUtils;
import com.colen.tempora.utils.PlayerUtils;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

/** What happens to the data already in a database: schema migration when a logger opens it, and pruning. */
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
        public String note;

        @Column(constraints = "NOT NULL", renamedFrom = "oldCount")
        public int count;

        @Column(constraints = "NOT NULL DEFAULT 7")
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
