package com.colen.tempora.loggers.command;

import com.colen.tempora.Tempora;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.entity.player.EntityPlayerMP;

import static com.colen.tempora.Tempora.NETWORK;

public class CommandPacketHandler implements IMessageHandler<CommandQueueElement, IMessage> {

    @Override
    public IMessage onMessage(final CommandQueueElement message, MessageContext ctx) {
        Tempora.commandLogger.addEventToRender(message);
        return null;
    }

    public static void initPackets() {
        NETWORK.registerMessage(CommandPacketHandler.class, CommandQueueElement.class, 11, Side.CLIENT);
    }
}
