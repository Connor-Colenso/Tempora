package com.colen.tempora.commands;

import com.colen.tempora.commands.command_base.CommandArg;
import com.colen.tempora.commands.command_base.TemporaCommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import com.colen.tempora.utils.CommandUtils;

public class ExplodeCommand extends TemporaCommandBase {

    public ExplodeCommand() {
        super(
            new CommandArg("<strength>", "tempora.command.explode.help.arg0")
            );
    }

    @Override
    public String getCommandName() {
        return "tempora_explode";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2; // OP-only
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayerMP player)) {
            sender.addChatMessage(CommandUtils.playerOnly());
            return;
        }

        if (args.length != 1) {
            sender.addChatMessage(CommandUtils.wrongUsage(getCommandUsage(sender)));
            return;
        }

        float strength;

        try {
            strength = Float.parseFloat(args[0]);
        } catch (NumberFormatException e) {
            sender.addChatMessage(new ChatComponentTranslation("tempora.command.explode.force.must.be.numeric"));
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
            strength,
            false, // Causes fire
            true // Damages blocks
        );
    }

    public IChatComponent getCommandDescription() {
        return new ChatComponentTranslation("tempora.command.explode.help.description");
    }

    @Override
    public String getExampleArgs() {
        return "5";
    }

    @Override
    public String getTranslationKeyBase() {
        return "tempora.command.explode";
    }
}
