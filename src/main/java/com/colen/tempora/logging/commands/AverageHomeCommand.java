package com.colen.tempora.logging.commands;

import com.colen.tempora.logging.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.logging.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.TimeUtils;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

import static com.colen.tempora.logging.loggers.generic.GenericQueueElement.generateTeleportChatComponent;

public class AverageHomeCommand extends CommandBase {

    @Override public String getCommandName() { return "averagehome"; }
    @Override public int    getRequiredPermissionLevel()    { return 2; }
    @Override public String getCommandUsage(ICommandSender sender) {
        return "/averagehome <player> [<look-back>] [<dim>]";
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender s, String[] a) {
        return (a.length == 1)
            ? PlayerUtils.getTabCompletionForPlayerNames(a[0])
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

        // Get player UUID
        final String uuid = PlayerUtils.uuidForName(args[0]);
        if (uuid == null) {
            sender.addChatMessage(new ChatComponentTranslation(
                "tempora.command.averagehome.unknown_player", args[0]));
            return;
        }

        // Optional lookback and force dim param handling.
        Long    lookbackCutoffEpoch = null;
        Integer forcedDim           = null;

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

        // Get read only DB connection.
        GenericPositionalLogger<?> movementLogger =
            GenericPositionalLogger.getLogger("PlayerMovementLogger");
        if (movementLogger == null || movementLogger.getReadOnlyConnection() == null) {
            sender.addChatMessage(new ChatComponentTranslation("tempora.command.averagehome.no_db"));
            return;
        }
        Connection conn = movementLogger.getReadOnlyConnection();

        // Build SQL query
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

        // Execute query
        try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            int i = 1;
            stmt.setString(i++, uuid);

            if (forcedDim != null)         stmt.setInt   (i++, forcedDim);
            else                           stmt.setString(i++, uuid);

            if (lookbackCutoffEpoch != null) stmt.setLong(i, lookbackCutoffEpoch);

            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next() || rs.getObject("avg_x") == null) {
                    sender.addChatMessage(new ChatComponentTranslation(
                        "tempora.command.averagehome.no_data"));
                    return;
                }

                double avgX = rs.getDouble("avg_x");
                double avgY = rs.getDouble("avg_y");
                double avgZ = rs.getDouble("avg_z");
                int    dim  = rs.getInt   ("dimensionID");

                IChatComponent tpLink = generateTeleportChatComponent(
                    avgX, avgY, avgZ, dim, args[0],
                    GenericQueueElement.CoordFormat.FLOAT_1DP);

                sender.addChatMessage(new ChatComponentTranslation(
                    "tempora.command.averagehome.result",
                    PlayerUtils.UUIDToName(uuid), dim, tpLink));
            }

        } catch (SQLException e) {
            FMLLog.severe("AverageHomeCommand SQL failed: %s", e.getMessage());
            e.printStackTrace();
            sender.addChatMessage(new ChatComponentTranslation(
                "tempora.command.averagehome.sql_error"));
        }
    }

    private static boolean containsLetter(String s) {
        for (int c : s.codePoints().toArray())
            if (Character.isLetter(c)) return true;
        return false;
    }

    private static int parseDim(String s) {
        try { return Integer.parseInt(s); }
        catch (NumberFormatException ex) {
            throw new CommandException("Bad <dim> value; must be an integer.");
        }
    }
}
