package com.myname.mymodid.PositionalEvents.Loggers.EntityDeath;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class EntityDeathPacketHandler implements IMessage {

    public ArrayList<EntityDeathQueueElement> eventList;

    @Override
    public void fromBytes(ByteBuf buf) {
        int numberOfEvents = buf.readInt();
        eventList = new ArrayList<>(numberOfEvents);

        for (int i = 0; i < numberOfEvents; i++) {
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            int dimensionId = buf.readInt();

            EntityDeathQueueElement queueElement = new EntityDeathQueueElement(x, y, z, dimensionId);

            int nameLength = buf.readInt();
            byte[] nameBytes = new byte[nameLength];
            buf.readBytes(nameBytes);
            queueElement.nameOfDeadMob = new String(nameBytes, StandardCharsets.UTF_8);

            int killerLength = buf.readInt();
            byte[] killerBytes = new byte[killerLength];
            buf.readBytes(killerBytes);
            queueElement.killedBy = new String(killerBytes, StandardCharsets.UTF_8);

            eventList.add(queueElement);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(eventList.size()); // First write the number of events to be processed

        for (EntityDeathQueueElement queueElement : eventList) {
            buf.writeDouble(queueElement.x); // Write X coordinate
            buf.writeDouble(queueElement.y); // Write Y coordinate
            buf.writeDouble(queueElement.z); // Write Z coordinate
            buf.writeInt(queueElement.dimensionId); // Write dimension ID

            // Write mob name information
            byte[] nameBytes = queueElement.nameOfDeadMob.getBytes(StandardCharsets.UTF_8);
            buf.writeInt(nameBytes.length);  // First write the length of the name
            buf.writeBytes(nameBytes);       // Then write the name itself

            // Write killer information
            byte[] killerBytes = queueElement.killedBy.getBytes(StandardCharsets.UTF_8);
            buf.writeInt(killerBytes.length);  // First write the length of the killer string
            buf.writeBytes(killerBytes);       // Then write the killer string itself
        }
    }

    public static class ClientMessageHandler implements IMessageHandler<EntityDeathPacketHandler, IMessage> {
        @Override
        public IMessage onMessage(final EntityDeathPacketHandler message, MessageContext ctx) {
            for (EntityDeathQueueElement queueElement : message.eventList) {
                Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText("mobInfo"));
            }
            return null; // No response packet needed
        }
    }

}
