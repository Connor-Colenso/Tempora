package com.myname.mymodid.Loggers;

import com.myname.mymodid.TemporaUtils;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.command.ICommand;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.CommandEvent;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static com.myname.mymodid.TemporaUtils.isClientSide;

public class CommandLogger extends GenericLoggerPositional {

    @Override
    protected String processResultSet(ResultSet rs) throws SQLException {
        return String.format(
            "%s executed [/%s %s] at [%f, %f, %f] in dimension %d on %s",
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
    public Connection initDatabase() {
        try {
            conn = DriverManager.getConnection(databaseURL());
            final String sql = "CREATE TABLE IF NOT EXISTS Events (" + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
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
            final PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.execute();
        } catch (final SQLException e) {
            e.printStackTrace();
        }

        return conn;
    }

    @Override
    protected String databaseURL() {
        return TemporaUtils.databaseDirectory() + "commandLogger.db";
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onCommand(final CommandEvent event) {
        // Server side only.
        if (isClientSide()) return;

        if (event.sender instanceof EntityPlayerMP player) {
            ICommand command = event.command;
            String[] args = event.parameters;

            try {
                final String sql = "INSERT INTO Events(playerName, command, arguments, x, y, z, dimensionID) VALUES(?, ?, ?, ?, ?, ?, ?)";
                final PreparedStatement pstmt = conn.prepareStatement(sql);
                pstmt.setString(1, player.getDisplayName());
                pstmt.setString(2, command.getCommandName());
                pstmt.setString(3, String.join(" ", args));
                pstmt.setDouble(4, player.posX);
                pstmt.setDouble(5, player.posY);
                pstmt.setDouble(6, player.posZ);
                pstmt.setInt(7, player.worldObj.provider.dimensionId);
                pstmt.executeUpdate();
            } catch (final SQLException e) {
                e.printStackTrace();
            }
        }
    }
}
