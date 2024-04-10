package com.myname.mymodid.PositionalEvents.Loggers.PlayerMovement;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class PlayerMovementPacketHandler implements IMessage {

    public ArrayList<PlayerMovementQueueElement> eventList;

    @Override
    public void fromBytes(ByteBuf buf) {
        int numberOfEvents = buf.readInt();
        eventList = new ArrayList<>(numberOfEvents);

        for (int i = 0; i < numberOfEvents; i++) {
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            int dimensionID = buf.readInt();

            PlayerMovementQueueElement queueElement = new PlayerMovementQueueElement(x, y, z, dimensionID);

            int uuidLength = buf.readInt();
            byte[] uuidBytes = new byte[uuidLength];
            buf.readBytes(uuidBytes);

            String playerUUID = new String(uuidBytes, StandardCharsets.UTF_8);
            queueElement.playerUUID = playerUUID;

            eventList.add(queueElement);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(eventList.size()); // First write the number of events to be processed

        for (PlayerMovementQueueElement queueElement : eventList) {
            buf.writeDouble(queueElement.x);
            buf.writeDouble(queueElement.y);
            buf.writeDouble(queueElement.z);
            buf.writeInt(queueElement.dimensionId);

            byte[] uuidBytes = queueElement.playerUUID.getBytes(StandardCharsets.UTF_8);
            buf.writeInt(uuidBytes.length);  // First write the length of the UUID string
            buf.writeBytes(uuidBytes);       // Then write the UUID string itself
        }
    }

    public static class ClientMessageHandler implements IMessageHandler<PlayerMovementPacketHandler, IMessage> {
        @Override
        public IMessage onMessage(final PlayerMovementPacketHandler message, MessageContext ctx) {
            for (PlayerMovementQueueElement queueElement : message.eventList) {
                Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText(
                    "Player moved to " + queueElement.x + ", " + queueElement.y + ", " + queueElement.z +
                        " in dimension " + queueElement.dimensionId));
            }
            return null; // No response packet needed
        }
    }
}
