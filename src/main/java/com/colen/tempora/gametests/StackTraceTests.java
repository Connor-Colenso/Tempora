package com.colen.tempora.gametests;

import static com.colen.tempora.gametests.TestSupport.assertWellFormed;
import static com.colen.tempora.gametests.TestSupport.eventsNear;
import static com.colen.tempora.gametests.TestSupport.region;
import static com.gtnewhorizons.horizonqa.api.TestPos.at;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.init.Blocks;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.common.config.Configuration;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.Tempora;
import com.colen.tempora.TemporaEvents;
import com.colen.tempora.chat.ChatComponentStackTrace;
import com.colen.tempora.loggers.block_change.BlockChangeEventInfo;
import com.colen.tempora.loggers.block_change.BlockChangeLogger;
import com.colen.tempora.loggers.generic.GenericEventInfo;
import com.colen.tempora.loggers.generic.LoggerDatabase;
import com.colen.tempora.loggers.generic.PositionalEventInfo;
import com.colen.tempora.regions.LogRule;
import com.colen.tempora.utils.DatabaseUtils;
import com.colen.tempora.utils.StackTraceUtils;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

/**
 * Block changes store the stack trace that made them, once per distinct trace, under an ID that is a hash of the
 * trace, so it can be shown however long ago and through any number of restarts. [Trace] carries the whole trace to
 * the client, which prints it when clicked.
 */
@GameTestHolder(Tempora.MODID)
public class StackTraceTests {

    // Two blocks changed by the same code have the same trace, stored once, and both read it back. Their chat lines
    // carry it in [Trace], and survive the trip to the client.
    @GameTest(timeoutTicks = 200)
    public static void blockChangesStoreTheirTraceOnce(GameTestHelper helper) {
        BlockChangeLogger logger = TemporaEvents.blockChangeLogger;
        TestPos centre = helper.absolute(at(2, 1, 2));
        Probe<List<PositionalEventInfo>> stored = new Probe<>(helper, "blockChangesStoreTheirTraceOnce");

        helper.startSequence()
            .thenExecute("change two blocks from the same line of code", () -> {
                region(helper, "test_traces", at(1, 1, 2), at(3, 1, 2), TemporaEvents.BLOCK_CHANGE, LogRule.ON);
                for (int x = 1; x <= 3; x += 2) helper.setBlock(at(x, 1, 2), Blocks.gold_block);
            })
            .thenWaitUntil(
                "both changes are stored",
                100,
                () -> helper.assertEquals(
                    2L,
                    stored.poll(() -> eventsNear(helper, logger, centre, 1))
                        .size(),
                    "events"))
            .thenExecute("they share one stored trace, which their chat lines carry", () -> {
                BlockChangeEventInfo first = (BlockChangeEventInfo) stored.latest()
                    .get(0);
                BlockChangeEventInfo second = (BlockChangeEventInfo) stored.latest()
                    .get(1);

                helper.assertNotNull(first.getStackTrace(), "the trace wasn't read back with the event");
                helper.assertEquals(first.stackTraceID, second.stackTraceID, "the same code should give the same ID");
                helper.assertEquals(first.getStackTrace(), second.getStackTrace(), "the same trace");
                helper.assertEquals(
                    StackTraceUtils.getID(first.getStackTrace()),
                    first.stackTraceID,
                    "the ID is the trace's hash");

                ChatComponentStackTrace trace = findTrace(first.localiseText());
                helper.assertNotNull(trace, "the chat line has no [Trace]");
                helper.assertEquals(first.getStackTrace(), trace.getTrace(), "[Trace] carries the stored trace");

                // Chat reaches the client as JSON.
                IChatComponent received = IChatComponent.Serializer
                    .func_150699_a(IChatComponent.Serializer.func_150696_a(first.localiseText()));
                ChatComponentStackTrace receivedTrace = findTrace(received);
                helper.assertNotNull(receivedTrace, "[Trace] didn't survive the trip to the client");
                helper.assertEquals(first.getStackTrace(), receivedTrace.getTrace(), "[Trace] after JSON");

                assertWellFormed(helper, logger, stored.latest());
            })
            .thenSucceed();
    }

    /**
     * A trace is read from the database, so a restart loses nothing: a new logger on the same database (as after a
     * restart) reads the trace back. Once no event refers to it, pruning deletes it.
     */
    @GameTest
    public static void tracesSurviveRestartAndArePruned(GameTestHelper helper) throws Exception {
        String trace = "com.example.Machine#tick:42 -> com.example.Machine#breakBlock:7";
        deleteDatabaseFiles(TraceTestLogger.NAME);
        helper.afterTest(() -> deleteDatabaseFiles(TraceTestLogger.NAME));

        // Before the restart: a block change and its trace are written.
        TraceTestLogger before = new TraceTestLogger();
        LoggerDatabase beforeDatabase = before.getDatabaseManager();
        beforeDatabase.genericConfig(throwawayConfig(helper));
        beforeDatabase.initialiseDatabase();
        BlockChangeEventInfo change = new BlockChangeEventInfo();
        change.timestamp = 1000;
        change.stackTraceID = before.traceID(trace);
        try {
            beforeDatabase.insertBatch(Collections.singletonList(change));
        } finally {
            beforeDatabase.shutdownDatabase();
        }

        // After it: a new logger, with nothing in memory, reads the trace back from the database.
        TraceTestLogger after = new TraceTestLogger();
        LoggerDatabase afterDatabase = after.getDatabaseManager();
        afterDatabase.genericConfig(throwawayConfig(helper));
        afterDatabase.initialiseDatabase();
        try {
            Connection conn = afterDatabase.getDBConn();
            List<? extends GenericEventInfo> events;
            try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("SELECT * FROM " + TraceTestLogger.NAME)) {
                events = after.generateQueryResults(rs);
            }
            helper.assertEquals(1L, events.size(), "events stored");
            after.loadExtras(events, conn);
            helper.assertEquals(
                trace,
                ((BlockChangeEventInfo) events.get(0)).getStackTrace(),
                "the trace after a restart");

            // Once the event is gone, its trace goes too.
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("DELETE FROM " + TraceTestLogger.NAME);
            }
            after.pruneTraces(conn);
            conn.commit();
            try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM StackTraces")) {
                rs.next();
                helper.assertEquals(0L, rs.getLong(1), "traces left once nothing refers to them");
            }
        } finally {
            afterDatabase.shutdownDatabase();
        }
        helper.succeed();
    }

    // A block change logger of its own, with its own database, for the restart test. Never registered.
    public static class TraceTestLogger extends BlockChangeLogger {

        static final String NAME = "TemporaTestTraceLogger";

        @Override
        public @NotNull String getLoggerName() {
            return NAME;
        }

        @Override
        public @NotNull String getShortName() {
            return "test_trace";
        }

        void pruneTraces(Connection conn) throws SQLException {
            pruneExtras(conn);
        }
    }

    // ---------- Helpers ----------

    // The [Trace] in a chat line, wherever it is: as a sibling or a translation argument.
    private static ChatComponentStackTrace findTrace(IChatComponent component) {
        if (component instanceof ChatComponentStackTrace trace) return trace;
        List<Object> children = new ArrayList<>(component.getSiblings());
        if (component instanceof net.minecraft.util.ChatComponentTranslation translation) {
            Collections.addAll(children, translation.getFormatArgs());
        }
        for (Object child : children) {
            if (child instanceof IChatComponent childComponent) {
                ChatComponentStackTrace found = findTrace(childComponent);
                if (found != null) return found;
            }
        }
        return null;
    }

    // Config for a logger the test makes: its defaults, never saved. Forge creates the file empty, so it is deleted.
    private static Configuration throwawayConfig(GameTestHelper helper) {
        File file = DatabaseUtils.databaseDir()
            .resolve("tempora_trace_test_" + helper.getOriginX() + "_" + helper.getOriginZ() + ".cfg")
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
