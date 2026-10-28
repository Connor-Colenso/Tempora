package com.colen.tempora.networking.handlers;

import static com.colen.tempora.utils.CommandUtils.OP_ONLY;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.Tempora;
import com.colen.tempora.commands.TemporaCommand;
import com.colen.tempora.networking.packets.PacketTeleportRequest;
import com.colen.tempora.utils.CommandUtils;
import com.colen.tempora.utils.GenericUtils;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

/**
 * Teleports a player who clicked coordinates in Tempora's chat output, if they may use /tempora. Runs on the server
 * thread, as 1.7.10 queues mod packets there like vanilla ones.
 */
public class TeleportRequestHandler implements IMessageHandler<PacketTeleportRequest, IMessage> {

    @Override
    public IMessage onMessage(PacketTeleportRequest message, MessageContext ctx) {
        handle(ctx.getServerHandler().playerEntity, message);
        return null;
    }

    // Public for the tests, which can't make a MessageContext.
    public static void handle(EntityPlayerMP player, PacketTeleportRequest message) {
        if (TemporaCommand.isDisabled(player)) return;

        // Any client can send this packet, so it gets the same permission check /tempora does.
        if (!player.canCommandSenderUseCommand(OP_ONLY, TemporaCommand.NAME)) {
            sendError(player, "tempora.teleport.no_permission");
            return;
        }

        double x = message.getX();
        double y = message.getY();
        double z = message.getZ();
        int targetDim = message.getDimID();

        // A NaN or infinite position would corrupt the player's saved position.
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return;

        if (player.dimension != targetDim) {
            if (MinecraftServer.getServer()
                .worldServerForDimension(targetDim) == null) {
                sendError(player, "tempora.dimension.invalid", targetDim);
                return;
            }

            player.mcServer.getConfigurationManager()
                .transferPlayerToDimension(player, targetDim);
        }

        player.playerNetServerHandler.setPlayerLocation(x, y, z, player.rotationYaw, player.rotationPitch);

        // The coordinates are clickable again, to come back after wandering off.
        IChatComponent teleported = new ChatComponentTranslation(
            "tempora.teleport.done",
            CommandUtils.teleportChatComponent(x, y, z, targetDim, CommandUtils.TeleportType.EXACT),
            GenericUtils.getDimensionName(targetDim));
        teleported.getChatStyle()
            .setColor(EnumChatFormatting.GREEN);
        player.addChatMessage(teleported);

        // Not a command, so the server doesn't log it for us. Kept, as admins will want to know who went where.
        Tempora.LOG.info(
            "{} teleported to {}, {}, {} in dimension {} from Tempora's chat output.",
            player.getCommandSenderName(),
            x,
            y,
            z,
            targetDim);
    }

    private static void sendError(EntityPlayerMP player, String key, Object... args) {
        IChatComponent error = new ChatComponentTranslation(key, args);
        error.getChatStyle()
            .setColor(EnumChatFormatting.RED);
        player.addChatMessage(error);
    }
}
