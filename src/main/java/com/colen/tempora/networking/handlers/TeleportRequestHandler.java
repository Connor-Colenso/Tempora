package com.colen.tempora.networking.handlers;

import static com.colen.tempora.utils.CommandUtils.mayUseTempora;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import com.colen.tempora.Tempora;
import com.colen.tempora.commands.TemporaCommand;
import com.colen.tempora.networking.packets.PacketTeleportRequest;
import com.colen.tempora.utils.CommandUtils;
import com.colen.tempora.utils.GenericUtils;
import com.colen.tempora.utils.NoPortalTeleporter;
import com.gtnewhorizon.gtnhlib.chat.customcomponents.ChatComponentNumber;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

// Teleports a player who clicked coords in our chat output, if they can use /tempora. Server thread, 1.7.10 queues mod
// packets there like vanilla ones
public class TeleportRequestHandler implements IMessageHandler<PacketTeleportRequest, IMessage> {

    @Override
    public IMessage onMessage(PacketTeleportRequest message, MessageContext ctx) {
        handle(ctx.getServerHandler().playerEntity, message);
        return null;
    }

    // Public for the tests, they can't make a MessageContext
    public static void handle(EntityPlayerMP player, PacketTeleportRequest message) {
        if (TemporaCommand.isDisabled(player)) return;

        // Any client can send this, so same permission check as /tempora
        if (!mayUseTempora(player)) {
            sendError(player, "tempora.teleport.no_permission");
            return;
        }

        double x = message.getX();
        double y = message.getY();
        double z = message.getZ();
        int targetDim = message.getDimID();

        // NaN or infinite position would corrupt the player's saved position
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return;

        boolean wasFlying = player.capabilities.isFlying;

        if (player.dimension != targetDim) {
            WorldServer target = MinecraftServer.getServer()
                .worldServerForDimension(targetDim);
            if (target == null) {
                sendError(player, "tempora.dimension.invalid", new ChatComponentNumber(targetDim));
                return;
            }

            // Not the target world's own teleporter, which would build a Nether portal (or the End platform) where the
            // player lands
            player.mcServer.getConfigurationManager()
                .transferPlayerToDimension(player, targetDim, new NoPortalTeleporter(target));
        }

        // Most clicked coords are a block's (placed, broken, changed), if that block is solid now stand on it
        y = standingHeight(player.worldObj, x, y, z);
        player.playerNetServerHandler.setPlayerLocation(x, y, z, player.rotationYaw, player.rotationPitch);

        // Changing dimension gives the client a new player with the game mode's default abilities, so someone flying
        // would arrive falling. Only if the server still lets them fly since a mod's flight might end on leaving
        if (wasFlying && player.capabilities.allowFlying) {
            player.capabilities.isFlying = true;
            player.sendPlayerAbilities();
        }

        // Coords are clickable again to come back after wandering off
        IChatComponent teleported = new ChatComponentTranslation(
            "tempora.teleport.done",
            CommandUtils.teleportChatComponent(x, y, z, targetDim, CommandUtils.TeleportType.EXACT),
            GenericUtils.getDimensionName(targetDim));
        teleported.getChatStyle()
            .setColor(EnumChatFormatting.GREEN);
        player.addChatMessage(teleported);

        // Not a command so the server doesn't log it for us. Admins will want to know who went where
        Tempora.LOG.info(
            "{} teleported to {}, {}, {} in dimension {} from Tempora's chat output.",
            player.getCommandSenderName(),
            x,
            y,
            z,
            targetDim);
    }

    // Lowest height at or above y where a player's feet and head are both clear, so a click on a solid block lands on
    // top not inside. Just y if that's already clear
    private static double standingHeight(World world, double x, double y, double z) {
        int blockX = MathHelper.floor_double(x);
        int blockZ = MathHelper.floor_double(z);
        int blockY = MathHelper.floor_double(y);
        for (int height = blockY; height < world.getHeight() - 1; height++) {
            if (!isSolid(world, blockX, height, blockZ) && !isSolid(world, blockX, height + 1, blockZ)) {
                return height == blockY ? y : height;
            }
        }
        return y;
    }

    private static boolean isSolid(World world, int x, int y, int z) {
        return world.getBlock(x, y, z)
            .getMaterial()
            .blocksMovement();
    }

    private static void sendError(EntityPlayerMP player, String key, Object... args) {
        IChatComponent error = new ChatComponentTranslation(key, args);
        error.getChatStyle()
            .setColor(EnumChatFormatting.RED);
        player.addChatMessage(error);
    }
}
