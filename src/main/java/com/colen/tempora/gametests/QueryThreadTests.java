package com.colen.tempora.gametests;

import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.config.Configuration;

import com.colen.tempora.Tempora;
import com.colen.tempora.TemporaEvents;
import com.colen.tempora.loggers.generic.TemporaQueries;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.AfterBatch;
import com.gtnewhorizons.horizonqa.api.annotation.BeforeBatch;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

/**
 * How lookups behave when they go wrong: too slow, failing, or sent again before the last one finished. Runs in a
 * batch of its own with a short time limit, as a slow query holds up the one query thread every lookup shares.
 */
@GameTestHolder(Tempora.MODID)
public class QueryThreadTests {

    private static final String BATCH = "tempora.queries";
    private static final int TIME_LIMIT_SECONDS = 2;

    // Counts up forever, so it only ends when the time limit stops it.
    private static final String ENDLESS_SQL = "WITH RECURSIVE n(x) AS (SELECT 1 UNION ALL SELECT x + 1 FROM n)"
        + " SELECT max(x) FROM n";

    @BeforeBatch(BATCH)
    public static void shortenTimeLimit() {
        // Never saved, only read for this one setting. Forge creates the file empty, so it is deleted again.
        File file = configFile();
        Configuration config = new Configuration(file);
        config.get("Lookups", "queryTimeLimitSeconds", TIME_LIMIT_SECONDS);
        TemporaQueries.loadConfig(config);
        file.delete();
    }

    @AfterBatch(BATCH)
    public static void restoreTimeLimit() {
        TemporaQueries.loadConfig(Tempora.config);
    }

    private static File configFile() {
        return new File(DimensionManager.getCurrentSaveRootDirectory(), "tempora_query_tests.cfg");
    }

    /**
     * A query that runs too long is stopped at the time limit, and the sender is told it is still searching and then
     * that it was stopped. While it runs, the sender can't start another; once it is stopped, they can.
     */
    @GameTest(batch = BATCH, timeoutTicks = 300)
    public static void slowLookupIsStoppedAtTimeLimit(GameTestHelper helper) {
        CapturingSender sender = new CapturingSender("slowLookupIsStoppedAtTimeLimit", helper.getWorld());
        boolean[] slowShown = new boolean[1];
        boolean[] nextShown = new boolean[1];
        long[] startedAt = new long[1];
        long[] stoppedAt = new long[1];

        helper.startSequence()
            .thenExecute("start an endless query, then try another straight away", () -> {
                startedAt[0] = System.nanoTime();
                TemporaQueries
                    .submit(sender, QueryThreadTests::runEndlessQuery, (target, result) -> slowShown[0] = true);
                TemporaQueries.submit(sender, () -> 1, (target, result) -> slowShown[0] = true);

                helper.assertIterableEquals(
                    listOf("tempora.query.already_running"),
                    keys(sender),
                    "the second lookup should be refused");
            })
            .thenWaitUntil(
                "the sender is told it is still searching",
                40,
                () -> assertSent(helper, sender, "tempora.query.searching"))
            .thenWaitUntil("the query is stopped", 200, () -> {
                assertSent(helper, sender, "tempora.query.timed_out");
                if (stoppedAt[0] == 0) stoppedAt[0] = System.nanoTime();
            })
            .thenExecute("it ran until the limit, and showed nothing", () -> {
                long ranMillis = TimeUnit.NANOSECONDS.toMillis(stoppedAt[0] - startedAt[0]);
                helper.assertTrue(
                    ranMillis >= TimeUnit.SECONDS.toMillis(TIME_LIMIT_SECONDS) - 100,
                    "stopped after " + ranMillis + " ms, before the " + TIME_LIMIT_SECONDS + " s limit");
                helper.assertFalse(slowShown[0], "a stopped query's result was shown");

                TemporaQueries.submit(sender, () -> 1, (target, result) -> nextShown[0] = true);
            })
            .thenWaitUntil(
                "the sender can look up again",
                100,
                () -> helper.assertTrue(nextShown[0], "the next lookup never finished"))
            .thenSucceed();
    }

    // A query that fails tells the sender why, and leaves them free to look up again.
    @GameTest(batch = BATCH, timeoutTicks = 300)
    public static void failedLookupTellsSender(GameTestHelper helper) {
        CapturingSender sender = new CapturingSender("failedLookupTellsSender", helper.getWorld());
        boolean[] failedShown = new boolean[1];
        boolean[] nextShown = new boolean[1];

        helper.startSequence()
            .thenExecute("run a query on a table that doesn't exist", () -> TemporaQueries.submit(sender, () -> {
                try (Statement st = TemporaQueries.connection(TemporaEvents.playerBlockBreakLogger)
                    .createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM NoSuchTable")) {
                    return rs.next();
                }
            }, (target, result) -> failedShown[0] = true))
            .thenWaitUntil(
                "the sender is told it failed",
                200,
                () -> assertSent(helper, sender, "tempora.query.failed"))
            .thenExecute("and can look up again", () -> {
                helper.assertFalse(failedShown[0], "a failed query's result was shown");
                TemporaQueries.submit(sender, () -> 1, (target, result) -> nextShown[0] = true);
            })
            .thenWaitUntil(
                "the next lookup finishes",
                200,
                () -> helper.assertTrue(nextShown[0], "the next lookup never finished"))
            .thenSucceed();
    }

    // Reading the databases is for the query thread only, so no lookup can run SQL on the server thread by mistake.
    @GameTest(batch = BATCH)
    public static void connectionsAreRefusedOffQueryThread(GameTestHelper helper) {
        helper.assertThrows(IllegalStateException.class, () -> {
            try {
                TemporaQueries.connection(TemporaEvents.playerBlockBreakLogger);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, "a connection was handed out on the server thread");
        helper.succeed();
    }

    // ---------- Helpers ----------

    // On the query thread. Reports the time limit stopping it the same way Tempora's own lookups do.
    private static long runEndlessQuery() throws SQLException {
        try (Statement st = TemporaQueries.connection(TemporaEvents.playerBlockBreakLogger)
            .createStatement(); ResultSet rs = st.executeQuery(ENDLESS_SQL)) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            TemporaQueries.throwIfTimedOut();
            throw e;
        }
    }

    // The translation keys of everything sent to the sender, in order.
    private static List<String> keys(CapturingSender sender) {
        List<String> keys = new ArrayList<>();
        for (IChatComponent message : sender.getMessages()) {
            keys.add(
                message instanceof ChatComponentTranslation translation ? translation.getKey()
                    : message.getUnformattedText());
        }
        return keys;
    }

    private static void assertSent(GameTestHelper helper, CapturingSender sender, String key) {
        helper.assertTrue(keys(sender).contains(key), "expected " + key + ", sent so far: " + keys(sender));
    }

    private static List<String> listOf(String... items) {
        List<String> list = new ArrayList<>();
        for (String item : items) list.add(item);
        return list;
    }
}
