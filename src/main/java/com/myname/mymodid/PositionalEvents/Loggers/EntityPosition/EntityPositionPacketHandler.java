package com.myname.mymodid.PositionalEvents.Loggers.EntityPosition;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class EntityPositionPacketHandler implements IMessage {

    public ArrayList<EntityPositionQueueElement> eventList;

    @Override
    public void fromBytes(ByteBuf buf) {
        int numberOfEvents = buf.readInt();
        eventList = new ArrayList<>(numberOfEvents);

        for (int i = 0; i < numberOfEvents; i++) {
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();

            EntityPositionQueueElement queueElement = new EntityPositionQueueElement(x, y, z, 0); // Assume dimensionID is handled elsewhere or is not relevant here.

            int entityNameLength = buf.readInt();
            byte[] entityNameBytes = new byte[entityNameLength];
            buf.readBytes(entityNameBytes);

            String entityName = new String(entityNameBytes, StandardCharsets.UTF_8);
            queueElement.entityName = entityName;

            eventList.add(queueElement);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        // First write the number of events to be processed
        buf.writeInt(eventList.size());

        for (EntityPositionQueueElement queueElement : eventList) {
            // Write position data
            buf.writeDouble(queueElement.x);
            buf.writeDouble(queueElement.y);
            buf.writeDouble(queueElement.z);

            // Write entity name
            byte[] entityNameBytes = queueElement.entityName.getBytes(StandardCharsets.UTF_8);
            buf.writeInt(entityNameBytes.length);
            buf.writeBytes(entityNameBytes);
        }
    }

    public static class ClientMessageHandler implements IMessageHandler<EntityPositionPacketHandler, IMessage> {
        @Override
        public IMessage onMessage(final EntityPositionPacketHandler message, MessageContext ctx) {
            Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText("Entity spotted: "));
            return null; // No response packet needed
        }
    }

}
