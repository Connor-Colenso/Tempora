package com.colen.tempora.logging.loggers.command;

import static com.colen.tempora.TemporaUtils.isClientSide;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.colen.tempora.logging.loggers.generic.GenericQueueElement;
import net.minecraft.command.ICommand;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.CommandEvent;

import com.colen.tempora.logging.loggers.generic.ColumnDef;
import com.colen.tempora.logging.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.utils.PlayerUtils;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class CommandLogger extends GenericPositionalLogger<CommandQueueElement> {

    @Override
    public String getSQLTableName() {
        return "CommandLogger";
    }

    @Override
    public List<ColumnDef> getTableColumns() {
        return Arrays.asList(
            new ColumnDef("playerUUID", "TEXT", "NOT NULL"),
            new ColumnDef("command", "TEXT", "NOT NULL"),
            new ColumnDef("arguments", "TEXT", "NOT NULL"));
    }

    @Override
    public List<GenericQueueElement> generateQueryResults(ResultSet resultSet) throws SQLException {
        ArrayList<GenericQueueElement> eventList = new ArrayList<>();

        while (resultSet.next()) {
            double x = resultSet.getDouble("x");
            double y = resultSet.getDouble("y");
            double z = resultSet.getDouble("z");

            CommandQueueElement queueElement = new CommandQueueElement();
            queueElement.x = x;
            queueElement.y = y;
            queueElement.z = z;
            queueElement.dimensionId = resultSet.getInt("dimensionID");
            queueElement.timestamp = resultSet.getLong("timestamp");

            queueElement.playerNameWhoIssuedCommand = PlayerUtils.UUIDToName(resultSet.getString("playerUUID"));
            queueElement.commandName = resultSet.getString("command");
            queueElement.arguments = resultSet.getString("arguments");

            eventList.add(queueElement);
        }

        return eventList;
    }

    @Override
    public void threadedSaveEvents(List<CommandQueueElement> commandQueueElements) throws SQLException {
        if (commandQueueElements == null || commandQueueElements.isEmpty()) return;

        final String sql = "INSERT INTO " + getSQLTableName()
            + " (playerUUID, command, arguments, x, y, z, dimensionID, timestamp) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = getDBConn().prepareStatement(sql)) {
            for (CommandQueueElement commandQueueElement : commandQueueElements) {
                pstmt.setString(1, commandQueueElement.playerNameWhoIssuedCommand);
                pstmt.setString(2, commandQueueElement.commandName);
                pstmt.setString(3, commandQueueElement.arguments);
                pstmt.setDouble(4, commandQueueElement.x);
                pstmt.setDouble(5, commandQueueElement.y);
                pstmt.setDouble(6, commandQueueElement.z);
                pstmt.setInt(7, commandQueueElement.dimensionId);
                pstmt.setTimestamp(8, new Timestamp(commandQueueElement.timestamp));
                pstmt.addBatch();
            }

            pstmt.executeBatch();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onCommand(final CommandEvent event) {
        // Server side only.
        if (isClientSide()) return;
        if (event.isCanceled()) return;

        if (event.sender instanceof EntityPlayerMP player) {
            ICommand command = event.command;
            String[] args = event.parameters;

            CommandQueueElement queueElement = new CommandQueueElement();
            queueElement.x = player.posX;
            queueElement.y = player.posY;
            queueElement.z = player.posZ;
            queueElement.dimensionId = player.dimension;
            queueElement.timestamp = System.currentTimeMillis();

            queueElement.playerNameWhoIssuedCommand = player.getUniqueID()
                .toString();
            queueElement.commandName = command.getCommandName();
            queueElement.arguments = String.join(" ", args);

            queueEvent(queueElement);
        }
    }
}
