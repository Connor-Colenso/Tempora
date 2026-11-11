package com.colen.tempora.gametests;

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
import java.util.List;
import java.util.Random;

import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.common.config.Configuration;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.colen.tempora.Tempora;
import com.colen.tempora.enums.LoggerEventType;
import com.colen.tempora.gametests.DatabaseTests.CheckedTestEvent;
import com.colen.tempora.gametests.DatabaseTests.CheckedTestLogger;
import com.colen.tempora.loggers.generic.GenericLogger;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.LoggerDatabase;
import com.colen.tempora.loggers.generic.PositionalEventInfo;
import com.colen.tempora.loggers.generic.column.Column;
import com.colen.tempora.loggers.generic.column.ColumnType;
import com.colen.tempora.loggers.generic.column.names.NamePart;
import com.colen.tempora.loggers.generic.column.names.NameRole;
import com.colen.tempora.loggers.generic.column.names.NameRoles;
import com.colen.tempora.loggers.generic.column.names.ShownNames;
import com.colen.tempora.loggers.generic.column.names.lookups.ItemNames;
import com.colen.tempora.loggers.generic.column.names.lookups.MobNames;
import com.colen.tempora.utils.DatabaseUtils;
import com.colen.tempora.utils.ResolvedNames;
import com.colen.tempora.utils.TextFilter;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

// Name role columns pushed hard: roles and loggers built wrong in every way that should be refused at register, and
// big or odd ones that have to keep working. Overrides of textFilterCondition get tried at register too
@GameTestHolder(Tempora.MODID)
public class NameRoleTests {

    // Registration

    @GameTest
    public static void partWithoutRoleIsRefused(GameTestHelper helper) {
        refused(helper, new CheckedTestLogger<>("TemporaTestRolePartNoRole", PartNoRoleEvent::new));
    }

    @GameTest
    public static void groupWithoutRoleIsRefused(GameTestHelper helper) {
        refused(helper, new CheckedTestLogger<>("TemporaTestRoleGroupNoRole", GroupNoRoleEvent::new));
    }

    @GameTest
    public static void enumRoleWorks(GameTestHelper helper) {
        GenericLogger<?> logger = new CheckedTestLogger<>("TemporaTestRoleEnumRole", EnumRoleEvent::new);
        try {
            logger.checkColumns();
        } catch (RuntimeException e) {
            helper.fail("an enum singleton role was refused: " + causes(e));
            return;
        }
        helper.succeed();
    }

    @GameTest
    public static void abstractRoleIsRefused(GameTestHelper helper) {
        refused(helper, new CheckedTestLogger<>("TemporaTestRoleAbstractRole", AbstractRoleEvent::new));
    }

    @GameTest
    public static void throwingConstructorIsRefused(GameTestHelper helper) {
        refused(helper, new CheckedTestLogger<>("TemporaTestRoleThrowingCtor", ThrowingCtorEvent::new));
    }

    @GameTest
    public static void throwingPartsIsRefused(GameTestHelper helper) {
        refused(helper, new CheckedTestLogger<>("TemporaTestRoleThrowingParts", ThrowingPartsEvent::new));
    }

    @GameTest
    public static void innerClassRoleIsRefused(GameTestHelper helper) {
        refused(helper, new CheckedTestLogger<>("TemporaTestRoleInnerRole", InnerRoleEvent::new));
    }

    @GameTest
    public static void seventeenPartsIsRefused(GameTestHelper helper) {
        refused(helper, new CheckedTestLogger<>("TemporaTestRoleSeventeen", SeventeenEvent::new));
    }

    @GameTest
    public static void duplicatePartNamesIsRefused(GameTestHelper helper) {
        refused(helper, new CheckedTestLogger<>("TemporaTestRoleDupParts", DupPartsEvent::new));
    }

    @GameTest
    public static void nullPartIsRefused(GameTestHelper helper) {
        refused(helper, new CheckedTestLogger<>("TemporaTestRoleNullPart", NullPartEvent::new));
    }

    @GameTest
    public static void longColumnIsRefused(GameTestHelper helper) {
        refused(helper, new CheckedTestLogger<>("TemporaTestRoleLongColumn", LongColumnEvent::new));
    }

    @GameTest
    public static void playerColumnIsRefused(GameTestHelper helper) {
        refused(helper, new CheckedTestLogger<>("TemporaTestRolePlayerColumn", PlayerColumnEvent::new));
    }

    @GameTest
    public static void nbtColumnIsRefused(GameTestHelper helper) {
        refused(helper, new CheckedTestLogger<>("TemporaTestRoleNbtColumn", NbtColumnEvent::new));
    }

    // Columns with a role in a superclass event and the subclass both count
    @GameTest
    public static void inheritedRoleColumnsGroup(GameTestHelper helper) {
        GenericLogger<?> logger = new CheckedTestLogger<>("TemporaTestRoleInherited", InheritedEvent::new);
        logger.checkColumns();
        helper.assertEquals(
            Arrays.asList("baseMob", "subMob"),
            logger.nameColumns()
                .indexColumns(),
            "base and sub columns");
        helper.succeed();
    }

    // A role a logger only uses in its own textFilterCondition, never on a column. The client only lists names of
    // known roles and the server drops answers for unknown ones, so it has to be known once the logger registers
    @GameTest
    public static void overrideOnlyRoleIsKnownAfterRegister(GameTestHelper helper) {
        GenericLogger<?> logger = new OverrideRoleLogger("TemporaTestRoleOverrideRole");
        logger.checkColumns();
        helper.assertTrue(
            NameRoles.byName(OverrideOnlyNames.class.getName()) != null,
            "a role only used by textFilterCondition isn't known after register, so f: can never match it");
        helper.succeed();
    }

    // An override giving the wrong number of columns should fail at register, not on the first f: lookup
    @GameTest
    public static void overrideWithWrongColumnsIsRefused(GameTestHelper helper) {
        refused(helper, new WrongColumnsLogger("TemporaTestRoleWrongColumns"));
    }

    @GameTest
    public static void overrideWithTypoColumnIsRefused(GameTestHelper helper) {
        refused(helper, new TypoColumnLogger("TemporaTestRoleTypoColumn"));
    }

    @GameTest
    public static void enumWithTwoConstantsIsRefused(GameTestHelper helper) {
        refused(helper, new CheckedTestLogger<>("TemporaTestRoleTwoConstants", TwoConstantEvent::new));
    }

    // The enum role's answers have to survive the packet under the enum's name, not its constant's class
    @GameTest
    public static void enumRoleSurvivesThePacket(GameTestHelper helper) {
        new CheckedTestLogger<>("TemporaTestRoleEnumPacket", EnumRoleEvent::new).checkColumns();
        ResolvedNames.Builder builder = new ResolvedNames.Builder();
        builder.add(EnumNames.class, "enum_thing");
        ResolvedNames back = roundTrip(builder.build());
        helper.assertEquals(
            Collections.singleton(Collections.singletonList("enum_thing")),
            back.matches(EnumNames.class),
            "enum role after the packet");
        helper.succeed();
    }

    // Two roles with the same simple name stay apart through the packet
    @GameTest
    public static void sameSimpleNameRolesStayApart(GameTestHelper helper) {
        ResolvedNames.Builder builder = new ResolvedNames.Builder();
        builder.add(A.Names.class, "apple");
        builder.add(B.Names.class, "banana");
        ResolvedNames back = roundTrip(builder.build());
        helper.assertEquals(
            Collections.singleton(Collections.singletonList("apple")),
            back.matches(A.Names.class),
            "A's names");
        helper.assertEquals(
            Collections.singleton(Collections.singletonList("banana")),
            back.matches(B.Names.class),
            "B's names");
        helper.succeed();
    }

    // SQL

    // 16 parts, the most allowed, with ANY in all sorts of places, through the packet and into SQLite
    @GameTest
    public static void sixteenPartsWorkEndToEnd(GameTestHelper helper) throws Exception {
        GenericLogger<?> logger = new CheckedTestLogger<>("TemporaTestRoleSixteen", SixteenEvent::new);
        logger.checkColumns();
        List<String> columns = new ArrayList<>();
        for (int i = 0; i < 16; i++) columns.add("p" + i);
        helper.assertEquals(
            columns,
            logger.nameColumns()
                .indexColumns(),
            "columns in part order, not alphabetical");

        Random random = new Random(16);
        List<Object[]> things = new ArrayList<>();
        ResolvedNames.Builder builder = new ResolvedNames.Builder();
        for (int t = 0; t < 400; t++) {
            Object[] values = new Object[16];
            for (int i = 0; i < 16; i++) values[i] = random.nextInt(4) == 0 ? NamePart.ANY : (Object) random.nextInt(3);
            things.add(values);
            builder.add(SixteenNames.class, values);
        }
        ResolvedNames back = roundTrip(builder.build());
        helper.assertEquals(0, back.tooMany(), "too many");

        String condition = logger.textFilterCondition(TextFilter.of("t", back));
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:");
            Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE t (" + String.join(" INTEGER, ", columns) + " INTEGER)");
            List<int[]> rows = new ArrayList<>();
            try (PreparedStatement ps = conn
                .prepareStatement("INSERT INTO t VALUES (" + String.join(", ", Collections.nCopies(16, "?")) + ")")) {
                for (int r = 0; r < 3000; r++) {
                    int[] row = new int[16];
                    for (int i = 0; i < 16; i++) {
                        row[i] = random.nextInt(3);
                        ps.setInt(i + 1, row[i]);
                    }
                    rows.add(row);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            int expected = 0;
            for (int[] row : rows) if (matchesAny(things, row)) expected++;
            helper.assertEquals(expected, count(st, "t", condition), "16 part rows matched");
        }
        helper.succeed();
    }

    // Text that would break SQL if it weren't quoted right, and non BMP text through the packet
    @GameTest
    public static void oddTextOnlyMatchesItself(GameTestHelper helper) throws Exception {
        List<String> odd = Arrays.asList(
            "x') OR 1=1 --",
            "O'Brien",
            "''",
            "a\\b",
            "100%_",
            "🐉 dragon",
            "line\nbreak",
            "tab\there",
            " padded ",
            "ÄÖÜ",
            "İstanbul");
        ResolvedNames.Builder builder = new ResolvedNames.Builder();
        for (String name : odd) builder.entity(name);
        ResolvedNames back = roundTrip(builder.build());

        String condition = TextFilter.of("t", back)
            .condition(MobNames.class, "name");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:");
            Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE mobs (name TEXT)");
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO mobs VALUES (?)")) {
                for (String name : odd) {
                    ps.setString(1, name);
                    ps.addBatch();
                }
                for (String decoy : Arrays
                    .asList("x", "OBrien", "'", "a\\\\b", "100", "dragon", "padded", "istanbul")) {
                    ps.setString(1, decoy);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            helper.assertEquals(odd.size(), count(st, "mobs", condition), "only the odd names themselves");
        }
        helper.succeed();
    }

    // A logger with 100 item groups and a filter at the 1000 thing cap, as one query
    @GameTest(timeoutTicks = 400)
    public static void hundredGroupsAtTheCap(GameTestHelper helper) throws Exception {
        ResolvedNames.Builder builder = new ResolvedNames.Builder();
        List<int[]> exact = new ArrayList<>();
        List<Integer> anyMeta = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            exact.add(new int[] { i, i % 16 });
            builder.item(i, i % 16);
            anyMeta.add(10_000 + i);
            builder.item(10_000 + i, ResolvedNames.ANY_META);
        }
        ResolvedNames names = roundTrip(builder.build());
        helper.assertEquals(0, names.tooMany(), "at the cap is fine");
        TextFilter filter = TextFilter.of("t", names);

        List<String> conditions = new ArrayList<>();
        List<String> defs = new ArrayList<>();
        for (int g = 0; g < 100; g++) {
            conditions.add(filter.condition(ItemNames.class, "id" + g, "meta" + g));
            defs.add("id" + g + " INTEGER, meta" + g + " INTEGER");
        }
        String condition = TextFilter.anyOf(conditions);

        Random random = new Random(100);
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:");
            Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE wide (" + String.join(", ", defs) + ")");
            int expected = 0;
            try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO wide VALUES (" + String.join(", ", Collections.nCopies(200, "?")) + ")")) {
                for (int r = 0; r < 2000; r++) {
                    boolean hit = false;
                    for (int g = 0; g < 100; g++) {
                        int id = random.nextInt(25_000);
                        int meta = random.nextInt(16);
                        ps.setInt(g * 2 + 1, id);
                        ps.setInt(g * 2 + 2, meta);
                        if ((id < 500 && meta == id % 16) || (id >= 10_000 && id < 10_500)) hit = true;
                    }
                    if (hit) expected++;
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            long start = System.nanoTime();
            int found = count(st, "wide", condition);
            long millis = (System.nanoTime() - start) / 1_000_000;
            Tempora.LOG.info(
                "f: over 100 groups x 1000 things, {} chars of SQL, {} of 2000 rows in {} ms",
                condition.length(),
                found,
                millis);
            helper.assertEquals(expected, found, "rows matched over 100 groups");
        }
        helper.succeed();
    }

    // A real positional logger with role columns on a db written before they existed. The position index gets rebuilt
    // with them on the end, and queries on it pick the right rows
    @GameTest(timeoutTicks = 300)
    public static void realLoggerIndexGetsNameColumns(GameTestHelper helper) throws Exception {
        RolePositionalLogger logger = new RolePositionalLogger();
        String table = logger.getLoggerName();
        String index = "idx_" + table + "_xyz_dimension_time";
        deleteDatabaseFiles(table);
        helper.afterTest(() -> deleteDatabaseFiles(table));

        try (Connection conn = DriverManager.getConnection(DatabaseUtils.jdbcUrl(table + ".db"));
            Statement st = conn.createStatement()) {
            st.execute(
                "CREATE TABLE " + table
                    + " (eventID INTEGER PRIMARY KEY, timestamp INTEGER NOT NULL DEFAULT 0,"
                    + " dimensionID INTEGER NOT NULL DEFAULT 0, x REAL NOT NULL DEFAULT 0, y REAL NOT NULL DEFAULT 0,"
                    + " z REAL NOT NULL DEFAULT 0)");
            st.execute("CREATE INDEX " + index + " ON " + table + " (dimensionID, x, y, z, timestamp)");
        }

        logger.checkColumns();
        LoggerDatabase database = logger.getDatabaseManager();
        database.genericConfig(throwawayConfig(helper));
        database.initialiseDatabase();
        try {
            List<RolePositionalEvent> events = new ArrayList<>();
            String[] mobs = { "Zombie", "Creeper", "O'Brien" };
            for (int i = 0; i < 300; i++) {
                RolePositionalEvent event = new RolePositionalEvent();
                event.timestamp = 1000 + i;
                event.x = i % 10;
                event.y = 64;
                event.z = 0;
                event.kind = mobs[i % 3];
                event.heldID = i % 7;
                event.heldMeta = i % 2;
                events.add(event);
            }
            database.insertBatch(events);
        } finally {
            database.shutdownDatabase();
        }

        ResolvedNames.Builder builder = new ResolvedNames.Builder();
        builder.entity("O'Brien");
        builder.item(3, 1);
        String condition = logger.textFilterCondition(TextFilter.of("t", builder.build()));

        try (Connection conn = DriverManager.getConnection(DatabaseUtils.jdbcUrl(table + ".db"));
            Statement st = conn.createStatement()) {
            List<String> indexed = new ArrayList<>();
            try (ResultSet rs = st.executeQuery("PRAGMA index_info(" + index + ")")) {
                while (rs.next()) indexed.add(rs.getString("name"));
            }
            helper.assertEquals(
                Arrays.asList("dimensionID", "x", "y", "z", "timestamp", "heldID", "heldMeta", "mobKind"),
                indexed,
                "position index columns");

            int expected = 0;
            for (int i = 0; i < 300; i++) {
                if (i % 10 >= 2 && i % 10 <= 5 && (i % 3 == 2 || (i % 7 == 3 && i % 2 == 1))) expected++;
            }
            String where = "dimensionID = 0 AND x >= 2 AND x < 6 AND y >= 64 AND y < 65 AND z >= 0 AND z < 1 AND "
                + condition;
            helper.assertEquals(expected, count(st, table, where), "events near matching f:");

            StringBuilder plan = new StringBuilder();
            try (ResultSet rs = st.executeQuery("EXPLAIN QUERY PLAN SELECT * FROM " + table + " WHERE " + where)) {
                while (rs.next()) plan.append(rs.getString("detail"))
                    .append('\n');
            }
            helper.assertTrue(
                plan.toString()
                    .contains(index),
                "query should use the position index, plan was:\n" + plan);
        }
        helper.succeed();
    }

    // Helpers

    private static void refused(GameTestHelper helper, GenericLogger<?> logger) {
        try {
            logger.checkColumns();
        } catch (IllegalStateException e) {
            helper.succeed();
            return;
        } catch (RuntimeException e) {
            helper.fail(
                logger.getLoggerName() + " threw "
                    + e.getClass()
                        .getSimpleName()
                    + " not IllegalStateException: "
                    + causes(e));
            return;
        }
        helper.fail(logger.getLoggerName() + "'s columns were accepted");
    }

    private static String causes(Throwable t) {
        StringBuilder text = new StringBuilder();
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (text.length() > 0) text.append(" <- ");
            text.append(
                c.getClass()
                    .getSimpleName())
                .append(": ")
                .append(c.getMessage());
        }
        return text.toString();
    }

    private static ResolvedNames roundTrip(ResolvedNames names) {
        ByteBuf buf = Unpooled.buffer();
        names.encode(buf);
        return ResolvedNames.decode(buf);
    }

    private static boolean matchesAny(List<Object[]> things, int[] row) {
        for (Object[] thing : things) {
            boolean all = true;
            for (int i = 0; i < row.length && all; i++) {
                if (thing[i] != NamePart.ANY && (Integer) thing[i] != row[i]) all = false;
            }
            if (all) return true;
        }
        return false;
    }

    private static int count(Statement st, String table, String condition) throws SQLException {
        try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table + " WHERE " + condition)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static Configuration throwawayConfig(GameTestHelper helper) {
        File file = DatabaseUtils.databaseDir()
            .resolve("tempora_test_role_" + helper.getOriginX() + "_" + helper.getOriginZ() + ".cfg")
            .toFile();
        helper.afterTest(file::delete);
        return new Configuration(file);
    }

    private static void deleteDatabaseFiles(String loggerName) {
        for (String suffix : new String[] { ".db", ".db-wal", ".db-shm" }) {
            File file = DatabaseUtils.databaseDir()
                .resolve(loggerName + suffix)
                .toFile();
            if (file.exists() && !file.delete()) throw new IllegalStateException("Could not delete " + file);
        }
    }

    // Roles

    // Constant with a body, so its class is EnumNames$1 not EnumNames
    public enum EnumNames implements NameRole {

        INSTANCE {

            @Override
            public void shownNames(ShownNames add) {
                add.add("Enum Thing", "enum_thing");
            }
        };

        @Override
        public List<NamePart> parts() {
            return Collections.singletonList(NamePart.text("name"));
        }
    }

    public enum TwoConstantNames implements NameRole {

        ONE,
        TWO;

        @Override
        public List<NamePart> parts() {
            return Collections.singletonList(NamePart.text("name"));
        }

        @Override
        public void shownNames(ShownNames add) {}
    }

    public static class TwoConstantEvent extends CheckedTestEvent {

        @Column(nameRole = TwoConstantNames.class)
        public String name = "";
    }

    // Override with a column the table doesn't have
    public static class TypoColumnLogger extends CheckedTestLogger<PlainEvent> {

        TypoColumnLogger(String name) {
            super(name, PlainEvent::new);
        }

        @Override
        public @Nullable String textFilterCondition(TextFilter filter) {
            return filter.condition(MobNames.class, "knid");
        }
    }

    public abstract static class AbstractNames implements NameRole {

        @Override
        public List<NamePart> parts() {
            return Collections.singletonList(NamePart.text("name"));
        }
    }

    public static final class ThrowingCtorNames implements NameRole {

        public ThrowingCtorNames() {
            throw new RuntimeException("boom");
        }

        @Override
        public List<NamePart> parts() {
            return Collections.singletonList(NamePart.text("name"));
        }

        @Override
        public void shownNames(ShownNames add) {}
    }

    public static final class ThrowingPartsNames implements NameRole {

        @Override
        public List<NamePart> parts() {
            throw new UnsupportedOperationException("no parts for you");
        }

        @Override
        public void shownNames(ShownNames add) {}
    }

    // Not static, so it can't be made without an outer instance
    public final class InnerNames implements NameRole {

        @Override
        public List<NamePart> parts() {
            return Collections.singletonList(NamePart.text("name"));
        }

        @Override
        public void shownNames(ShownNames add) {}
    }

    public static final class SeventeenNames implements NameRole {

        @Override
        public List<NamePart> parts() {
            List<NamePart> parts = new ArrayList<>();
            for (int i = 0; i < 17; i++) parts.add(NamePart.integer("p" + i));
            return parts;
        }

        @Override
        public void shownNames(ShownNames add) {}
    }

    public static final class SixteenNames implements NameRole {

        @Override
        public List<NamePart> parts() {
            List<NamePart> parts = new ArrayList<>();
            for (int i = 0; i < 16; i++) parts.add(NamePart.integer("p" + i));
            return parts;
        }

        @Override
        public void shownNames(ShownNames add) {}
    }

    public static final class DupPartsNames implements NameRole {

        @Override
        public List<NamePart> parts() {
            return Arrays.asList(NamePart.integer("id"), NamePart.text("id"));
        }

        @Override
        public void shownNames(ShownNames add) {}
    }

    public static final class NullPartNames implements NameRole {

        @Override
        public List<NamePart> parts() {
            return Arrays.asList(NamePart.integer("id"), null);
        }

        @Override
        public void shownNames(ShownNames add) {}
    }

    public static final class OverrideOnlyNames implements NameRole {

        @Override
        public List<NamePart> parts() {
            return Collections.singletonList(NamePart.text("kind"));
        }

        @Override
        public void shownNames(ShownNames add) {
            add.add("Gizmo", "gizmo");
        }
    }

    public static final class A {

        public static final class Names implements NameRole {

            @Override
            public List<NamePart> parts() {
                return Collections.singletonList(NamePart.text("name"));
            }

            @Override
            public void shownNames(ShownNames add) {}
        }
    }

    public static final class B {

        public static final class Names implements NameRole {

            @Override
            public List<NamePart> parts() {
                return Collections.singletonList(NamePart.text("name"));
            }

            @Override
            public void shownNames(ShownNames add) {}
        }
    }

    // Events

    public static class PartNoRoleEvent extends CheckedTestEvent {

        @Column(part = ItemNames.ID)
        public int itemID;
    }

    public static class GroupNoRoleEvent extends CheckedTestEvent {

        @Column(group = "before")
        public String mob = "";
    }

    public static class EnumRoleEvent extends CheckedTestEvent {

        @Column(nameRole = EnumNames.class)
        public String name = "";
    }

    public static class AbstractRoleEvent extends CheckedTestEvent {

        @Column(nameRole = AbstractNames.class)
        public String name = "";
    }

    public static class ThrowingCtorEvent extends CheckedTestEvent {

        @Column(nameRole = ThrowingCtorNames.class)
        public String name = "";
    }

    public static class ThrowingPartsEvent extends CheckedTestEvent {

        @Column(nameRole = ThrowingPartsNames.class)
        public String name = "";
    }

    public static class InnerRoleEvent extends CheckedTestEvent {

        @Column(nameRole = InnerNames.class)
        public String name = "";
    }

    public static class SeventeenEvent extends CheckedTestEvent {

        @Column(nameRole = SeventeenNames.class, part = "p0")
        public int p0;
    }

    public static class DupPartsEvent extends CheckedTestEvent {

        @Column(nameRole = DupPartsNames.class, part = "id")
        public int id;
    }

    public static class NullPartEvent extends CheckedTestEvent {

        @Column(nameRole = NullPartNames.class, part = "id")
        public int id;
    }

    public static class LongColumnEvent extends CheckedTestEvent {

        @Column(nameRole = ItemNames.class, part = ItemNames.ID)
        public long itemID;

        @Column(nameRole = ItemNames.class, part = ItemNames.META)
        public int itemMeta;
    }

    public static class PlayerColumnEvent extends CheckedTestEvent {

        @Column(nameRole = MobNames.class, type = ColumnType.PLAYER)
        public String mob = "";
    }

    public static class NbtColumnEvent extends CheckedTestEvent {

        @Column(nameRole = MobNames.class, type = ColumnType.NBT)
        public String mob = "";
    }

    public static class InheritedBaseEvent extends CheckedTestEvent {

        @Column(nameRole = MobNames.class)
        public String baseMob = "";
    }

    public static class InheritedEvent extends InheritedBaseEvent {

        @Column(nameRole = MobNames.class, group = "sub")
        public String subMob = "";
    }

    public static class SixteenEvent extends CheckedTestEvent {

        @Column(nameRole = SixteenNames.class, part = "p0")
        public int p0;
        @Column(nameRole = SixteenNames.class, part = "p1")
        public int p1;
        @Column(nameRole = SixteenNames.class, part = "p2")
        public int p2;
        @Column(nameRole = SixteenNames.class, part = "p3")
        public int p3;
        @Column(nameRole = SixteenNames.class, part = "p4")
        public int p4;
        @Column(nameRole = SixteenNames.class, part = "p5")
        public int p5;
        @Column(nameRole = SixteenNames.class, part = "p6")
        public int p6;
        @Column(nameRole = SixteenNames.class, part = "p7")
        public int p7;
        @Column(nameRole = SixteenNames.class, part = "p8")
        public int p8;
        @Column(nameRole = SixteenNames.class, part = "p9")
        public int p9;
        @Column(nameRole = SixteenNames.class, part = "p10")
        public int p10;
        @Column(nameRole = SixteenNames.class, part = "p11")
        public int p11;
        @Column(nameRole = SixteenNames.class, part = "p12")
        public int p12;
        @Column(nameRole = SixteenNames.class, part = "p13")
        public int p13;
        @Column(nameRole = SixteenNames.class, part = "p14")
        public int p14;
        @Column(nameRole = SixteenNames.class, part = "p15")
        public int p15;
    }

    public static class PlainEvent extends CheckedTestEvent {

        @Column
        public String kind = "";
    }

    // Matches its plain kind column by a role no column has
    public static class OverrideRoleLogger extends CheckedTestLogger<PlainEvent> {

        OverrideRoleLogger(String name) {
            super(name, PlainEvent::new);
        }

        @Override
        public @Nullable String textFilterCondition(TextFilter filter) {
            return filter.condition(OverrideOnlyNames.class, "kind");
        }
    }

    // Gives items one column where they need two
    public static class WrongColumnsLogger extends CheckedTestLogger<PlainEvent> {

        WrongColumnsLogger(String name) {
            super(name, PlainEvent::new);
        }

        @Override
        public @Nullable String textFilterCondition(TextFilter filter) {
            return filter.condition(ItemNames.class, "kind");
        }
    }

    public static class RolePositionalLogger extends GenericPositionalLogger<RolePositionalEvent> {

        @Override
        public @NotNull String getLoggerName() {
            return "TemporaTestRoleNames";
        }

        @Override
        public @NotNull String getShortName() {
            return "test_role_names";
        }

        @Override
        public @NotNull RolePositionalEvent newEventInfo() {
            return new RolePositionalEvent();
        }

        @Override
        public @NotNull LoggerEventType getLoggerEventType() {
            return LoggerEventType.None;
        }
    }

    public static class RolePositionalEvent extends PositionalEventInfo {

        // Column name not the field name
        @Column(name = "mobKind", nameRole = MobNames.class)
        public String kind = "";

        @Column(nameRole = ItemNames.class, part = ItemNames.ID, group = "held")
        public int heldID;

        @Column(nameRole = ItemNames.class, part = ItemNames.META, group = "held")
        public int heldMeta;

        @Override
        public IChatComponent localiseText() {
            return new ChatComponentText(kind);
        }

        @Override
        public String getLoggerName() {
            return "TemporaTestRoleNames";
        }
    }

}
