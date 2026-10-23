package com.colen.tempora.utils;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.command.CommandException;

/**
 * Whose events a lookup shows, as a p: filter gives it: comma separated names, each with a leading ! to leave that
 * player out instead. "p:Colen,Kyium" is only their events, "p:!Colen,!Kyium" everyone's but theirs.
 */
public final class PlayerFilter {

    private static final PlayerFilter ANYONE = new PlayerFilter(Collections.emptyList(), Collections.emptyList());

    private final List<String> includedUUIDs;
    private final List<String> excludedUUIDs;

    private PlayerFilter(List<String> includedUUIDs, List<String> excludedUUIDs) {
        this.includedUUIDs = includedUUIDs;
        this.excludedUUIDs = excludedUUIDs;
    }

    public static PlayerFilter anyone() {
        return ANYONE;
    }

    // Names or UUIDs. Throws a CommandException, shown to the sender, for a player who has never joined.
    public static PlayerFilter parse(String filter) {
        List<String> included = new ArrayList<>();
        List<String> excluded = new ArrayList<>();
        for (String item : filter.split(",")) {
            boolean exclude = item.startsWith("!");
            String name = exclude ? item.substring(1) : item;

            String uuid = PlayerUtils.uuidFromName(name);
            if (uuid == null) throw new CommandException("tempora.command.unknown_player", name);
            (exclude ? excluded : included).add(uuid);
        }
        return new PlayerFilter(included, excluded);
    }

    public boolean isAnyone() {
        return includedUUIDs.isEmpty() && excludedUUIDs.isEmpty();
    }

    // E.g. " AND playerUUID IN (?, ?) AND playerUUID NOT IN (?)", to add to a WHERE. Empty for anyone.
    public String sqlCondition(String column) {
        StringBuilder sql = new StringBuilder();
        if (!includedUUIDs.isEmpty()) sql.append(" AND ")
            .append(column)
            .append(" IN (")
            .append(placeholders(includedUUIDs.size()))
            .append(")");
        if (!excludedUUIDs.isEmpty()) sql.append(" AND ")
            .append(column)
            .append(" NOT IN (")
            .append(placeholders(excludedUUIDs.size()))
            .append(")");
        return sql.toString();
    }

    // Fills in sqlCondition's placeholders from index onwards, and returns the index after them.
    public int bind(PreparedStatement ps, int index) throws SQLException {
        for (String uuid : includedUUIDs) ps.setString(index++, uuid);
        for (String uuid : excludedUUIDs) ps.setString(index++, uuid);
        return index;
    }

    private static String placeholders(int count) {
        return String.join(", ", Collections.nCopies(count, "?"));
    }
}
