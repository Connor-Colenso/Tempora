package com.myname.mymodid.Commands;

import static com.myname.mymodid.TemporaUtils.parseTime;

import com.myname.mymodid.Loggers.GenericLoggerPositional;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.S28PacketEffect;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

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

        queryDatabases(sender, radius, seconds);
    }

    private void queryDatabases(ICommandSender sender, int radius, long seconds) {

        if (!(sender instanceof EntityPlayerMP)) return;

        for (GenericLoggerPositional logger : GenericLoggerPositional.loggerList) {
            for(String message : logger.queryEventsWithinRadiusAndTime(sender, radius, seconds)) {
                sender.addChatMessage(new ChatComponentText(message));
            }
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
