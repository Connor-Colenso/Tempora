package com.colen.tempora.logging.commands;

import com.colen.tempora.logging.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.logging.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.TimeUtils;                    // ⬅ NEW
import cpw.mods.fml.common.FMLLog;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.CommandException;
import net.minecraft.util.*;

import java.sql.*;
import java.util.*;

import static com.colen.tempora.logging.loggers.generic.GenericQueueElement.generateTeleportChatComponent;

public class AverageHomeCommand extends CommandBase {

    @Override public String getCommandName()          { return "averagehome"; }
    @Override public int    getRequiredPermissionLevel() { return 2; }
    @Override public String getCommandUsage(ICommandSender sender) {
        return "/averagehome <player> [--forcedim=<id>] [--lookback=<time>]";
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        return (args.length == 1)
            ? PlayerUtils.getTabCompletionForPlayerNames(args[0])
            : Collections.emptyList();
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {

        // Validate
        if (args.length < 1) {
            sender.addChatMessage(new ChatComponentTranslation(
                "tempora.command.averagehome.usage", getCommandUsage(sender)));
            return;
        }

        // Get UUID
        final String uuid = PlayerUtils.uuidForName(args[0]);
        if (uuid == null) {
            sender.addChatMessage(new ChatComponentTranslation(
                "tempora.command.averagehome.unknown_player", args[0]));
            return;
        }

        // Optional flags
        Integer forcedDim           = null;
        Long    lookbackCutoffEpoch = null;          // millis UTC
        for (int i = 1; i < args.length; i++) {
            String flag = args[i];

            if (flag.startsWith("--forcedim=")) {
                try {
                    forcedDim = Integer.parseInt(flag.substring("--forcedim=".length()));
                } catch (NumberFormatException ex) {
                    throw new CommandException("Bad --forcedim value; must be an integer.");
                }

            } else if (flag.startsWith("--lookback=")) {
                String spec = flag.substring("--lookback=".length());
                long seconds = TimeUtils.convertToSeconds(spec);  // e.g. “1h”, “3days”
                lookbackCutoffEpoch = System.currentTimeMillis() - seconds * 1000L;

            } else {
                throw new CommandException("Unknown flag: " + flag);
            }
        }

        // Get DB connection (read only)
        GenericPositionalLogger<?> movementLogger =
            GenericPositionalLogger.getLogger("PlayerMovementLogger");
        if (movementLogger == null) {
            sender.addChatMessage(new ChatComponentTranslation("tempora.command.averagehome.no_db"));
            return;
        }
        Connection conn = movementLogger.getReadOnlyConnection();
        if (conn == null) {
            sender.addChatMessage(new ChatComponentTranslation("tempora.command.averagehome.no_db"));
            return;
        }

        // Compose SQL
        final String tbl = movementLogger.getSQLTableName();
        StringBuilder sql = new StringBuilder()
            .append("SELECT AVG(x) AS avg_x, AVG(y) AS avg_y, AVG(z) AS avg_z, dimensionID ")
            .append("FROM ").append(tbl).append(" ")
            .append("WHERE playerUUID = ? ");

        if (forcedDim != null) {
            sql.append("AND dimensionID = ? ");
        } else {
            sql.append("AND dimensionID = (SELECT dimensionID FROM ").append(tbl)
                .append(" WHERE playerUUID = ? GROUP BY dimensionID ")
                .append(" ORDER BY COUNT(*) DESC LIMIT 1) ");
        }

        if (lookbackCutoffEpoch != null) {
            sql.append("AND timestamp >= ? ");
        }

        // Execute SQL
        try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            int idx = 1;
            stmt.setString(idx++, uuid);

            if (forcedDim != null) {
                stmt.setInt(idx++, forcedDim);
            } else {
                stmt.setString(idx++, uuid);
            }

            if (lookbackCutoffEpoch != null) {
                stmt.setLong(idx, lookbackCutoffEpoch);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    sender.addChatMessage(new ChatComponentTranslation(
                        "tempora.command.averagehome.no_data"));
                    return;
                }

                /* 6) ——— Build reply ——— */
                double avgX = rs.getDouble("avg_x");
                if (rs.wasNull()) {             // ➋ row exists, but AVG() ⇒ NULL
                    sender.addChatMessage(new ChatComponentTranslation(
                        "tempora.command.averagehome.no_data"));
                    return;
                }

                double avgY = rs.getDouble("avg_y");
                double avgZ = rs.getDouble("avg_z");
                int    dim  = rs.getInt("dimensionID");

                IChatComponent tpLink = generateTeleportChatComponent(
                    avgX, avgY, avgZ, dim, args[0],
                    GenericQueueElement.CoordFormat.FLOAT_1DP);

                IChatComponent msg = new ChatComponentTranslation(
                    "tempora.command.averagehome.result",
                    PlayerUtils.UUIDToName(uuid),
                    dim,
                    tpLink);

                sender.addChatMessage(msg);
            }

        } catch (SQLException e) {
            FMLLog.severe("AverageHomeCommand SQL failed: %s", e.getMessage());
            e.printStackTrace();
            sender.addChatMessage(new ChatComponentTranslation(
                "tempora.command.averagehome.sql_error"));
        }
    }
}
