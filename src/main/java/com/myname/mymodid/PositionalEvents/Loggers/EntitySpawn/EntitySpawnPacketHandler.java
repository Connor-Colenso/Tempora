package com.myname.mymodid.PositionalEvents.Loggers.EntitySpawn;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class EntitySpawnPacketHandler implements IMessage {

    public ArrayList<EntitySpawnQueueElement> eventList;

    @Override
    public void fromBytes(ByteBuf buf) {
        int numberOfEvents = buf.readInt();
        eventList = new ArrayList<>(numberOfEvents);

        for (int i = 0; i < numberOfEvents; i++) {
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            int dimensionId = buf.readInt();

            EntitySpawnQueueElement queueElement = new EntitySpawnQueueElement(x, y, z, dimensionId);

            int nameLength = buf.readInt();
            byte[] nameBytes = new byte[nameLength];
            buf.readBytes(nameBytes);
            queueElement.entityName = new String(nameBytes, StandardCharsets.UTF_8);

            eventList.add(queueElement);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(eventList.size()); // First write the number of events

        for (EntitySpawnQueueElement queueElement : eventList) {
            buf.writeDouble(queueElement.x);
            buf.writeDouble(queueElement.y);
            buf.writeDouble(queueElement.z);
            buf.writeInt(queueElement.dimensionId); // Write the dimension ID

            byte[] nameBytes = queueElement.entityName.getBytes(StandardCharsets.UTF_8);
            buf.writeInt(nameBytes.length); // First write the length of the entity name
            buf.writeBytes(nameBytes); // Then write the entity name itself
        }
    }

    public static class ClientMessageHandler implements IMessageHandler<EntitySpawnPacketHandler, IMessage> {
        @Override
        public IMessage onMessage(final EntitySpawnPacketHandler message, MessageContext ctx) {
            for (EntitySpawnQueueElement queueElement : message.eventList) {
                Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText("Spawned: "));
            }
            return null; // No response packet needed
        }
    }
}
