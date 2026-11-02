package com.colen.tempora.utils;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.command.CommandException;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.chat.EventChat;
import com.colen.tempora.loggers.generic.PlayerIDs;

/**
 * Whose events a lookup shows, as a p: filter gives it: comma separated names, each with a leading ! to leave that
 * player out instead. "p:Player1,Player2" is only their events, "p:!Player1,!Player2" everyone's but theirs.
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

    // Whose events these are, to name in a header: "Player1, Player2", "everyone but Player1", or "Player1 except
    // Player2".
    public IChatComponent describe() {
        if (excludedUUIDs.isEmpty()) return names(includedUUIDs);
        if (includedUUIDs.isEmpty())
            return new ChatComponentTranslation("tempora.player_filter.everyone_but", names(excludedUUIDs));
        return new ChatComponentTranslation("tempora.player_filter.except", names(includedUUIDs), names(excludedUUIDs));
    }

    private static IChatComponent names(List<String> uuids) {
        IChatComponent names = new ChatComponentText("");
        for (int i = 0; i < uuids.size(); i++) {
            if (i > 0) names.appendText(", ");
            names.appendSibling(EventChat.player(uuids.get(i)));
        }
        return names;
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

    // Fills in sqlCondition's placeholders from index onwards, and returns the index after them. Player columns hold
    // each player's number, see PlayerIDs.
    public int bind(PreparedStatement ps, int index) throws SQLException {
        for (String uuid : includedUUIDs) ps.setInt(index++, PlayerIDs.lookupID(uuid));
        for (String uuid : excludedUUIDs) ps.setInt(index++, PlayerIDs.lookupID(uuid));
        return index;
    }

    private static String placeholders(int count) {
        return String.join(", ", Collections.nCopies(count, "?"));
    }
}
