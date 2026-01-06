package com.colen.tempora.commands;

import static com.colen.tempora.Tempora.LOG;
import static com.colen.tempora.TemporaEvents.PLAYER_MOVEMENT;
import static com.colen.tempora.loggers.generic.GenericQueueElement.generateTeleportChatComponent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.TemporaLoggerManager;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.TimeUtils;

/**
 * /homechunk <player> [<look‑back>] [<dim>]
 *
 * Finds the chunk with the most position samples for <player>
 * (optionally filtered by time‑window and/or dimension) and
 * teleports to the centre of that chunk.
 */
public class HomeChunkCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "homechunk";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getCommandUsage(ICommandSender s) {
        return "/homechunk <player> [<look-back>] [<dim>]";
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender s, String[] a) {
        return (a.length == 1) ? PlayerUtils.getTabCompletionForPlayerNames(a[0]) : Collections.emptyList();
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {

        // Validate
        if (args.length < 1) {
            sender.addChatMessage(
                new ChatComponentTranslation("tempora.command.averagehome.usage", getCommandUsage(sender)));
            return;
        }

        // Player to UUID.
        final String uuid = PlayerUtils.uuidForName(args[0]);
        if (uuid == null) {
            sender.addChatMessage(new ChatComponentTranslation("tempora.command.averagehome.unknown_player", args[0]));
            return;
        }

        // Optional params
        Long lookbackCutoffEpoch = null;
        Integer forcedDim = null;

        if (args.length >= 2) {
            String p = args[1];
            if (containsLetter(p)) {
                long seconds = TimeUtils.convertToSeconds(p);
                lookbackCutoffEpoch = System.currentTimeMillis() - seconds * 1000L;
            } else {
                forcedDim = parseDim(p);
            }
        }
        if (args.length >= 3) {
            forcedDim = parseDim(args[2]);
        }

        // Get read only db conn
        GenericPositionalLogger<?> movementLogger = TemporaLoggerManager.getLogger(PLAYER_MOVEMENT);
        if (movementLogger == null || movementLogger.getDatabaseManager()
            .getReadOnlyConnection() == null) {
            sender.addChatMessage(new ChatComponentTranslation("tempora.command.averagehome.no_db"));
            return;
        }
        Connection conn = movementLogger.getDatabaseManager()
            .getReadOnlyConnection();

        // This query finds the player's most visited chunk (home chunk)
        // First, we group all movement logs by chunk (x >> 4, z >> 4) and dimension
        // We count how many times the player was in each chunk
        // Then we sort by the number of visits and pick the top one (LIMIT 1)
        //
        // After that, we join this "hot chunk" back with the movement table
        // This gives us all the rows that were inside the most visited chunk
        //
        // From those rows, we calculate:
        // - home_x: center of the chunk in x (cx * 16 + 8)
        // - home_y: average y value in that chunk
        // - home_z: center of the chunk in z (cz * 16 + 8)
        // - dimensionID: which dimension this chunk was in
        //
        // Optional filters are used to:
        // - restrict to a single dimension (forcedDim)
        // - only include recent data (lookbackCutoffEpoch)
        //
        // The player's UUID is passed in twice, once for each part of the query
        final String tbl = movementLogger.getLoggerName();
        StringBuilder sql = new StringBuilder().append("WITH hot AS (")
            .append("  SELECT (x>>4) AS cx, (z>>4) AS cz, dimensionID, COUNT(*) AS hits ")
            .append("  FROM ")
            .append(tbl)
            .append(" ")
            .append("  WHERE playerUUID = ? ");

        if (forcedDim != null) sql.append("AND dimensionID = ? ");
        if (lookbackCutoffEpoch != null) sql.append("AND timestamp >= ? ");
        sql.append("  GROUP BY cx, cz, dimensionID ")
            .append("  ORDER BY hits DESC LIMIT 1")
            .append(") ")
            .append("SELECT (hot.cx*16 + 8)  AS home_x, ")
            .append("       AVG(m.y)         AS home_y, ")
            .append("       (hot.cz*16 + 8)  AS home_z, ")
            .append("       hot.dimensionID  AS dimensionID ")
            .append("FROM   hot ")
            .append("JOIN   ")
            .append(tbl)
            .append(" m ")
            .append("  ON   (m.x>>4)=hot.cx AND (m.z>>4)=hot.cz AND m.dimensionID=hot.dimensionID ")
            .append("WHERE  m.playerUUID = ? ");
        if (lookbackCutoffEpoch != null) sql.append("AND m.timestamp >= ? ");

        // Run query
        try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            int i = 1;

            // params for hot CTE
            stmt.setString(i++, uuid);
            if (forcedDim != null) stmt.setInt(i++, forcedDim);
            if (lookbackCutoffEpoch != null) stmt.setLong(i++, lookbackCutoffEpoch);

            // params for outer WHERE
            stmt.setString(i++, uuid);
            if (lookbackCutoffEpoch != null) stmt.setLong(i, lookbackCutoffEpoch);

            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next() || rs.getObject("home_x") == null) {
                    sender.addChatMessage(new ChatComponentTranslation("tempora.command.averagehome.no_data"));
                    return;
                }

                double homeX = rs.getDouble("home_x");
                double homeY = rs.getDouble("home_y");
                double homeZ = rs.getDouble("home_z");
                int dim = rs.getInt("dimensionID");

                IChatComponent tpLink = generateTeleportChatComponent(
                    homeX,
                    homeY,
                    homeZ,
                    dim,
                    args[0],
                    GenericQueueElement.CoordFormat.FLOAT_1DP);

                sender.addChatMessage(
                    new ChatComponentTranslation(
                        "tempora.command.averagehome.result",
                        PlayerUtils.UUIDToName(uuid),
                        dim,
                        tpLink));
            }

        } catch (SQLException e) {
            LOG.error("HomeChunkCommand SQL failed: {}. With stack trace: {}", e.getMessage(), e);
            sender.addChatMessage(new ChatComponentTranslation("tempora.command.averagehome.sql_error"));
        }
    }

    private static boolean containsLetter(String s) {
        for (int c : s.codePoints()
            .toArray()) if (Character.isLetter(c)) return true;
        return false;
    }

    private static int parseDim(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            throw new CommandException("tempora.command.homechunk.bad_dim");
        }
    }
}
