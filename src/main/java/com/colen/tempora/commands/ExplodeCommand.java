package com.colen.tempora.commands;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import com.colen.tempora.commands.command_base.TemporaSubcommand;

/**
 * /tempora explode <power>
 * Blows up the block the player is looking at, for testing explosion logging. Only registered in a development
 * environment, see TemporaCommand.
 */
public class ExplodeCommand extends TemporaSubcommand {

    public ExplodeCommand() {
        super("explode", "<power>");
    }

    @Override
    public boolean isPlayerOnly() {
        return true;
    }

    @Override
    public void execute(ICommandSender sender, String[] args) {
        EntityPlayerMP player = (EntityPlayerMP) sender;

        if (args.length != 1) {
            sendUsageError(sender);
            return;
        }

        float power;

        try {
            power = Float.parseFloat(args[0]);
        } catch (NumberFormatException e) {
            sender.addChatMessage(new ChatComponentTranslation("tempora.command.explode.not_a_number"));
            return;
        }

        World world = player.worldObj;

        // Ray trace from the player's eyes
        Vec3 start = Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);

        Vec3 look = player.getLookVec();
        double range = 200.0D;

        Vec3 end = start.addVector(look.xCoord * range, look.yCoord * range, look.zCoord * range);

        MovingObjectPosition hit = world.rayTraceBlocks(start, end, false);

        if (hit == null) {
            sender.addChatMessage(new ChatComponentTranslation("tempora.command.explode.no_block_in_sight"));
            return;
        }

        double x = hit.hitVec.xCoord;
        double y = hit.hitVec.yCoord;
        double z = hit.hitVec.zCoord;

        // Create explosion
        world.newExplosion(
            player, // Explosion source
            x,
            y,
            z,
            power,
            false, // Causes fire
            true // Damages blocks
        );
    }

    @Override
    public String getExampleArgs() {
        return "5";
    }

}
