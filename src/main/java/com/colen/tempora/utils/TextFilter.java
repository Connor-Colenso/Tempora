package com.colen.tempora.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.command.CommandException;

import org.jetbrains.annotations.Nullable;

import com.colen.tempora.loggers.generic.NameColumns;
import com.colen.tempora.loggers.generic.column.names.NamePart;
import com.colen.tempora.loggers.generic.column.names.NameRole;
import com.colen.tempora.loggers.generic.column.names.NameRoles;
import com.gtnewhorizon.gtnhlib.chat.customcomponents.ChatComponentNumber;

// The f: filter of /tempora lookup, text matched against the parts of an event a player reads in its line: item and
// block names, mobs, achievements, commands. Names of things are only known in the player's language on their client,
// so the server asks the client which items, mobs and achievements the text names (NameRequests, ClientNameIndex) and
// searches by the values it answers with (ResolvedNames). Text stored as is, like commands, gets matched on the server
// (textCondition). Both compare text through TextFold
public final class TextFilter {

    private static final TextFilter ANY = new TextFilter("", ResolvedNames.nothing());

    // As the player typed it, never what a client sent back
    private final String text;
    private final ResolvedNames names;

    private TextFilter(String text, ResolvedNames names) {
        this.text = text;
        this.names = names;
    }

    public static TextFilter any() {
        return ANY;
    }

    /**
     * Names one made up thing of every role made so far, so a logger's f: SQL can be tried when it registers. See
     * GenericLogger.checkColumns
     */
    public static TextFilter probe() {
        return new TextFilter("probe", ResolvedNames.probe());
    }

    public boolean isAny() {
        return this == ANY;
    }

    public String getText() {
        return text;
    }

    // The filter for text the server asked a client about and what it answered. Naming nothing is fine, the text can
    // still match commands. Throws a CommandException to show the player if it named too much
    public static TextFilter of(String text, ResolvedNames names) {
        if (names.tooMany() > 0) {
            throw new CommandException(
                "tempora.command.lookup.name_too_many",
                text,
                new ChatComponentNumber(names.tooMany()),
                new ChatComponentNumber(ResolvedNames.MAX_MATCHES));
        }
        return new TextFilter(text, names);
    }

    // Building blocks for GenericLogger.textFilterCondition. Values go in as literals since there can be more than
    // SQLite allows as parameters. They're numbers and text, the text gets quoted

    /**
     * SQL true when an event names something this matched in any of these columns, null if none of them could. What
     * {@link com.colen.tempora.loggers.generic.GenericLogger#textFilterCondition} does by default
     */
    public @Nullable String condition(NameColumns columns) {
        List<String> conditions = new ArrayList<>();
        for (NameColumns.Group group : columns.groups()) addIfAny(conditions, condition(group.role, group.columns));
        return anyOf(conditions);
    }

    /**
     * SQL true when the thing in these columns, one per part of the role in part order, is one this matched. E.g. for
     * items {@code (itemID IN (1, 2) OR (itemID, itemMetadata) IN (VALUES (3, 0)))}. Null if this matched nothing of
     * that role. The columns can be any SQL, e.g. a joined table's
     */
    public @Nullable String condition(Class<? extends NameRole> role, String... columns) {
        return condition(NameRoles.of(role), Arrays.asList(columns));
    }

    public @Nullable String condition(NameRole role, List<String> columns) {
        int parts = role.parts()
            .size();
        if (columns.size() != parts) {
            throw new IllegalArgumentException(
                role.getClass()
                    .getSimpleName() + " has "
                    + parts
                    + " parts but got columns "
                    + columns
                    + ".");
        }

        // Things with ANY in the same parts get compared on the other parts together
        Map<List<Integer>, List<List<Object>>> byFixedParts = new LinkedHashMap<>();
        for (List<Object> thing : names.matches(role)) {
            List<Integer> fixed = new ArrayList<>();
            for (int i = 0; i < parts; i++) if (thing.get(i) != NamePart.ANY) fixed.add(i);
            byFixedParts.computeIfAbsent(fixed, k -> new ArrayList<>())
                .add(thing);
        }

        List<String> ors = new ArrayList<>();
        for (Map.Entry<List<Integer>, List<List<Object>>> entry : byFixedParts.entrySet()) {
            List<Integer> fixed = entry.getKey();
            // Any value in every part, so anything of this role
            if (fixed.isEmpty()) return "1";

            List<String> rows = new ArrayList<>();
            for (List<Object> thing : entry.getValue()) {
                List<String> values = new ArrayList<>();
                for (int i : fixed) values.add(sqlValue(thing.get(i)));
                rows.add(fixed.size() == 1 ? values.get(0) : "(" + String.join(", ", values) + ")");
            }

            if (fixed.size() == 1) {
                ors.add(columns.get(fixed.get(0)) + " IN (" + String.join(", ", rows) + ")");
            } else {
                List<String> fixedColumns = new ArrayList<>();
                for (int i : fixed) fixedColumns.add(columns.get(i));
                ors.add("(" + String.join(", ", fixedColumns) + ") IN (VALUES " + String.join(", ", rows) + ")");
            }
        }
        return anyOf(ors);
    }

    /**
     * SQL true when the text this expression gives has the filter's text in it, compared like names are (TextFold),
     * e.g. {@code "'/' || commandName || ' ' || arguments"}. For text stored as players read it, like commands. Matched
     * on the server so it needs no client. Can't use an index, so it reads the table for every event in range. Null if
     * there's no text
     */
    public @Nullable String textCondition(String expression) {
        String folded = TextFold.sql(expression);
        Set<String> forms = new LinkedHashSet<>();
        for (String term : TextFold.terms(text)) {
            forms.add(term);
            forms.add(TextFold.spaced(term));
        }
        List<String> ors = new ArrayList<>();
        for (String form : forms) ors.add("instr(" + folded + ", " + sqlString(form) + ") > 0");
        return anyOf(ors);
    }

    /**
     * SQL true unless this expression has an f: search for this same text in it, e.g. a logged command's arguments. The
     * lookup being run gets logged before it searches, so without this a command search would always find itself
     */
    public String notSearchedIn(String expression) {
        return "instr(" + TextFold.sql(expression) + ", " + sqlString("f:" + TextFold.fold(text)) + ") = 0";
    }

    /** SQL true if any of these are, null if there are none */
    public static @Nullable String anyOf(List<String> conditions) {
        if (conditions.isEmpty()) return null;
        if (conditions.size() == 1) return conditions.get(0);
        return "(" + String.join(" OR ", conditions) + ")";
    }

    private static void addIfAny(List<String> conditions, @Nullable String condition) {
        if (condition != null) conditions.add(condition);
    }

    private static String sqlValue(Object value) {
        return value instanceof Integer ? value.toString() : sqlString((String) value);
    }

    // As an SQL string literal
    private static String sqlString(String value) {
        // NUL would cut it short
        return "'" + value.replace("\0", "")
            .replace("'", "''") + "'";
    }
}
