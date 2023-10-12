package com.myname.mymodid.Commands;

import static com.myname.mymodid.TemporaUtils.parseTime;


import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.S28PacketEffect;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

import com.myname.mymodid.TemporaUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class QueryEventsCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "queryevents";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/queryevents <radius> <time>";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            throw new WrongUsageException(getCommandUsage(sender));
        }

        int radius = parseInt(sender, args[0]);
        long seconds = parseTime(args[1]);

        queryDatabase(sender, radius, seconds);
    }

    private void queryDatabase(ICommandSender sender, int radius, long seconds) {
        try (Connection conn = DriverManager.getConnection(TemporaUtils.databaseDirectory() + "blockBreakEvents.db")) {

            final String sql = "SELECT playerName, blockType, x, y, z, timestamp FROM BlockBreakEvents "
                + "WHERE ABS(x - ?) <= ? AND ABS(y - ?) <= ? AND ABS(z - ?) <= ? AND "
                + "timestamp >= datetime(CURRENT_TIMESTAMP, ? || ' seconds')";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, sender.getPlayerCoordinates().posX);
            pstmt.setInt(2, radius);
            pstmt.setInt(3, sender.getPlayerCoordinates().posY);
            pstmt.setInt(4, radius);
            pstmt.setInt(5, sender.getPlayerCoordinates().posZ);
            pstmt.setInt(6, radius);
            pstmt.setLong(7, -seconds); // Negate seconds for the "ago" behavior.

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String result = String.format(
                    "%s broke %s at [%d, %d, %d] on %s",
                    rs.getString("playerName"),
                    rs.getString("blockType"),
                    rs.getInt("x"),
                    rs.getInt("y"),
                    rs.getInt("z"),
                    rs.getString("timestamp"));
                sender.addChatMessage(new ChatComponentText(result));

                spawnParticleAt(rs.getInt("x"), rs.getInt("y"), rs.getInt("z"), sender.getEntityWorld());
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void spawnParticleAt(int x, int y, int z, World world) {
        int PARTICLE_ID = 2006;

        for (EntityPlayer player : world.playerEntities) {
            double distanceSquared = player.getDistanceSq(x + 0.5D, y + 0.5D, z + 0.5D);
            if (distanceSquared < 4096) { // If within 64 blocks
                S28PacketEffect packet = new S28PacketEffect(PARTICLE_ID, x, y, z, 0, false);
                ((EntityPlayerMP) player).playerNetServerHandler.sendPacket(packet);
            }
        }
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2; // Require OP permission level.
    }
}
