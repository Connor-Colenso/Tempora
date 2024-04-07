package com.myname.mymodid.Loggers;

import static com.myname.mymodid.TemporaUtils.isClientSide;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import akka.japi.Pair;
import com.myname.mymodid.QueueElement.CommandQueueElement;
import net.minecraft.command.ICommand;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.CommandEvent;

import com.myname.mymodid.TemporaUtils;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class CommandLogger extends GenericLoggerPositional<CommandQueueElement> {

    @Override
    public void handleConfig(Configuration config) {

    }

    @Override
    protected String processResultSet(ResultSet rs) throws SQLException {
        return String.format(
            "%s executed [/%s %s] at [%.1f, %.1f, %.1f] in dimension %d on %s",
            rs.getString("playerName"),
            rs.getString("command"),
            rs.getString("arguments"),
            rs.getDouble("x"),
            rs.getDouble("y"),
            rs.getDouble("z"),
            rs.getInt("dimensionID"),
            rs.getString("timestamp"));
    }

    @Override
    public void initTable() {
        try {
            final String sql = "CREATE TABLE IF NOT EXISTS " + getTableName()
                + " (id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "playerName TEXT NOT NULL,"
                + "command TEXT NOT NULL,"
                + "arguments TEXT,"
                + "x REAL,"
                + "y REAL,"
                + "z REAL,"
                + "dimensionID INTEGER DEFAULT "
                + TemporaUtils.defaultDimID()
                + ","
                + "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP"
                + ");";
            positionLoggerDBConnection.prepareStatement(sql)
                .execute();
        } catch (final SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void threadedSaveEvent(CommandQueueElement commandQueueElement) {

    }


    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onCommand(final CommandEvent event) {
        // Server side only.
        if (isClientSide()) return;

        if (event.sender instanceof EntityPlayerMP player) {
            ICommand command = event.command;
            String[] args = event.parameters;

            CommandQueueElement queueElement = new CommandQueueElement(player.posX, player.posY, player.posZ, player.dimension);
            queueElement.commandName = command.getCommandName();
            queueElement.arguments = String.join(" ", args);

        }
    }
}
