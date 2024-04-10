package com.myname.mymodid.PositionalEvents.Loggers.ItemUse;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class ItemUsePacketHandler implements IMessage {
    public ArrayList<ItemUseQueueElement> eventList;

    @Override
    public void fromBytes(ByteBuf buf) {
        int numberOfEvents = buf.readInt();
        eventList = new ArrayList<>(numberOfEvents);

        for (int i = 0; i < numberOfEvents; i++) {
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            int dimensionID = buf.readInt(); // Read dimension ID for each event

            ItemUseQueueElement queueElement = new ItemUseQueueElement(x, y, z, dimensionID);

            int uuidLength = buf.readInt();
            byte[] uuidBytes = new byte[uuidLength];
            buf.readBytes(uuidBytes);
            queueElement.playerUUID = new String(uuidBytes, StandardCharsets.UTF_8);

            queueElement.itemID = buf.readInt();
            queueElement.itemMetadata = buf.readInt();

            eventList.add(queueElement);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(eventList.size()); // First write the number of events

        for (ItemUseQueueElement queueElement : eventList) {
            buf.writeDouble(queueElement.x); // Write position data
            buf.writeDouble(queueElement.y);
            buf.writeDouble(queueElement.z);
            buf.writeInt(queueElement.dimensionId); // Write dimension ID

            // Write UUID information
            byte[] uuidBytes = queueElement.playerUUID.getBytes(StandardCharsets.UTF_8);
            buf.writeInt(uuidBytes.length); // First write the length of the UUID string
            buf.writeBytes(uuidBytes); // Then write the UUID string itself

            // Write item ID and metadata
            buf.writeInt(queueElement.itemID);
            buf.writeInt(queueElement.itemMetadata);
        }
    }

    public static class ClientMessageHandler implements IMessageHandler<ItemUsePacketHandler, IMessage> {
        @Override
        public IMessage onMessage(final ItemUsePacketHandler message, MessageContext ctx) {
            for (ItemUseQueueElement queueElement : message.eventList) {
                Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText("W32"));
            }
            return null; // No response packet needed
        }
    }
}
