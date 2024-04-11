package com.myname.mymodid.PositionalEvents.Loggers.Command;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class CommandPacketHandler implements IMessage {

    public ArrayList<CommandQueueElement> eventList;

    @Override
    public void fromBytes(ByteBuf buf) {
        int numberOfEvents = buf.readInt();
        eventList = new ArrayList<>(numberOfEvents);

        for (int i = 0; i < numberOfEvents; i++) {
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            int dimensionId = buf.readInt();

            CommandQueueElement queueElement = new CommandQueueElement();
            queueElement.x = x;
            queueElement.y = y;
            queueElement.z = z;
            queueElement.dimensionId = dimensionId;

            int uuidLength = buf.readInt();
            byte[] uuidBytes = new byte[uuidLength];
            buf.readBytes(uuidBytes);
            queueElement.playerUUIDWhoIssuedCommand = new String(uuidBytes, StandardCharsets.UTF_8);

            int commandNameLength = buf.readInt();
            byte[] commandNameBytes = new byte[commandNameLength];
            buf.readBytes(commandNameBytes);
            queueElement.commandName = new String(commandNameBytes, StandardCharsets.UTF_8);

            int argumentsLength = buf.readInt();
            byte[] argumentsBytes = new byte[argumentsLength];
            buf.readBytes(argumentsBytes);
            queueElement.arguments = new String(argumentsBytes, StandardCharsets.UTF_8);

            eventList.add(queueElement);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(eventList.size()); // Write the number of events

        for (CommandQueueElement queueElement : eventList) {
            buf.writeDouble(queueElement.x);
            buf.writeDouble(queueElement.y);
            buf.writeDouble(queueElement.z);
            buf.writeInt(queueElement.dimensionId);

            byte[] uuidBytes = queueElement.playerUUIDWhoIssuedCommand.getBytes(StandardCharsets.UTF_8);
            buf.writeInt(uuidBytes.length);
            buf.writeBytes(uuidBytes);

            byte[] commandNameBytes = queueElement.commandName.getBytes(StandardCharsets.UTF_8);
            buf.writeInt(commandNameBytes.length);
            buf.writeBytes(commandNameBytes);

            byte[] argumentsBytes = queueElement.arguments.getBytes(StandardCharsets.UTF_8);
            buf.writeInt(argumentsBytes.length);
            buf.writeBytes(argumentsBytes);
        }
    }

    public static class ClientMessageHandler implements IMessageHandler<CommandPacketHandler, IMessage> {

        @Override
        public IMessage onMessage(final CommandPacketHandler message, MessageContext ctx) {
            Minecraft.getMinecraft().thePlayer.addChatMessage(
                new ChatComponentText(
                    "Received command: " + message.eventList.get(0).commandName
                        + " with args: "
                        + message.eventList.get(0).arguments));
            return null; // No response packet needed
        }
    }
}
