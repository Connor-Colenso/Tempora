package com.myname.mymodid.PositionalEvents.Loggers;

import static com.myname.mymodid.TemporaUtils.isClientSide;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import com.myname.mymodid.PositionalEvents.QueueElements.CommandQueueElement;
import net.minecraft.command.ICommand;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.CommandEvent;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class CommandLogger extends GenericPositionalLogger<CommandQueueElement> {

    @Override
    public void handleConfig(Configuration config) {

    }

    @Override
    protected String processResultSet(ResultSet rs) throws SQLException {
        return String.format(
            "%s executed [/%s %s] at [%.1f, %.1f, %.1f] on %s",
            rs.getString("playerName"),
            rs.getString("command"),
            rs.getString("arguments"),
            rs.getDouble("x"),
            rs.getDouble("y"),
            rs.getDouble("z"),
            rs.getTimestamp("timestamp"));
    }

    @Override
    public void initTable() {
        try {
            positionLoggerDBConnection
                .prepareStatement(
                    "CREATE TABLE IF NOT EXISTS " + getTableName()
                        + " (id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "playerName TEXT NOT NULL,"
                        + "command TEXT NOT NULL,"
                        + "arguments TEXT NOT NULL,"
                        + "x REAL NOT NULL,"
                        + "y REAL NOT NULL,"
                        + "z REAL NOT NULL,"
                        + "dimensionID INTEGER DEFAULT 0 NOT NULL,"
                        + "timestamp DATETIME NOT NULL);")
                .execute();
        } catch (final SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void threadedSaveEvent(CommandQueueElement commandQueueElement) {
        try {
            final String sql = "INSERT INTO " + getTableName()
                + "(playerName, command, arguments, x, y, z, dimensionID, timestamp) VALUES(?, ?, ?, ?, ?, ?, ?, ?)";
            final PreparedStatement pstmt = positionLoggerDBConnection.prepareStatement(sql);
            pstmt.setString(1, commandQueueElement.playerWhoIssuedCommand);
            pstmt.setString(2, commandQueueElement.commandName);
            pstmt.setString(3, commandQueueElement.arguments);
            pstmt.setDouble(4, commandQueueElement.x);
            pstmt.setDouble(5, commandQueueElement.y);
            pstmt.setDouble(6, commandQueueElement.z);
            pstmt.setInt(7, commandQueueElement.dimensionId);
            pstmt.setTimestamp(8, new Timestamp(commandQueueElement.timestamp));
            pstmt.executeUpdate();
        } catch (final SQLException e) {
            e.printStackTrace();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onCommand(final CommandEvent event) {
        // Server side only.
        if (isClientSide()) return;

        if (event.sender instanceof EntityPlayerMP player) {
            ICommand command = event.command;
            String[] args = event.parameters;

            CommandQueueElement queueElement = new CommandQueueElement(
                player.posX,
                player.posY,
                player.posZ,
                player.dimension);
            queueElement.playerWhoIssuedCommand = player.getCommandSenderName();
            queueElement.commandName = command.getCommandName();
            queueElement.arguments = String.join(" ", args);

            eventQueue.add(queueElement);
        }
    }
}
