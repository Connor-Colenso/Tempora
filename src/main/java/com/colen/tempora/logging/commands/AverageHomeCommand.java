package com.colen.tempora.logging.commands;

import com.colen.tempora.logging.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.logging.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.PlayerUtils;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

import static com.colen.tempora.logging.loggers.generic.GenericQueueElement.generateTeleportChatComponent;

public class AverageHomeCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "averagehome";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/averagehome <player>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {

        if (args.length < 1) {
            sender.addChatMessage(new ChatComponentTranslation(
                "tempora.command.averagehome.usage",
                getCommandUsage(sender)));
            return;
        }

        String uuid = PlayerUtils.uuidForName(args[0]);
        if (uuid == null) {
            sender.addChatMessage(new ChatComponentTranslation(
                "tempora.command.averagehome.unknown_player", args[0]));
            return;
        }

        GenericPositionalLogger<?> movementLogger =
            GenericPositionalLogger.getLogger("PlayerMovementLogger");

        if (movementLogger == null) {
            sender.addChatMessage(new ChatComponentTranslation("tempora.command.averagehome.no_db"));
            return;
        }

        String sql =
            "SELECT AVG(x) AS avg_x, AVG(y) AS avg_y, AVG(z) AS avg_z, dimensionID " +
                "FROM " + movementLogger.getSQLTableName() + " " +
                "WHERE playerUUID = ? " +
                "  AND dimensionID = ( " +
                "      SELECT dimensionID " +
                "      FROM " + movementLogger.getSQLTableName() + " " +
                "      WHERE playerUUID = ? " +
                "      GROUP BY dimensionID " +
                "      ORDER BY COUNT(*) DESC " +
                "      LIMIT 1 )";

        Connection conn = movementLogger.getReadOnlyConnection();
        if (conn == null) {
            sender.addChatMessage(new ChatComponentTranslation("tempora.command.averagehome.no_db"));
            return;
        }

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, uuid);
            stmt.setString(2, uuid);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    double avgX = rs.getDouble("avg_x");
                    double avgY = rs.getDouble("avg_y");
                    double avgZ = rs.getDouble("avg_z");
                    int    dimId = rs.getInt("dimensionID");

                    IChatComponent tpLink = generateTeleportChatComponent(
                        avgX, avgY, avgZ,
                        dimId,
                        args[0],
                        GenericQueueElement.CoordFormat.FLOAT_1DP);

                    // Player X is most active in dim Y, click [coords] to visit location.
                    IChatComponent msg = new ChatComponentTranslation(
                        "tempora.command.averagehome.result",
                        PlayerUtils.UUIDToName(uuid),
                        dimId,
                        tpLink);

                    sender.addChatMessage(msg);

                } else {
                    sender.addChatMessage(new ChatComponentTranslation(
                        "tempora.command.averagehome.no_data"));
                }
            }

        } catch (SQLException e) {
            FMLLog.severe("AverageHomeCommand SQL failed: %s", e.getMessage());
            sender.addChatMessage(new ChatComponentTranslation(
                "tempora.command.averagehome.sql_error"));
        }
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) { // completing the first argument (player name)
            return PlayerUtils.getTabCompletionForPlayerNames(args[0]);
        }
        return Collections.emptyList();
    }
}
