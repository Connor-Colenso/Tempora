package com.myname.mymodid.PositionalEvents.Loggers.BlockBreak;

import com.myname.mymodid.Utils.BlockUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class BlockBreakPacketHandler implements IMessage {

    public ArrayList<BlockBreakQueueElement> eventList;

    @Override
    public void fromBytes(ByteBuf buf) {
        int numberOfEvents = buf.readInt();
        eventList = new ArrayList<>(numberOfEvents);

        for (int i = 0; i < numberOfEvents; i++) {
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();

            BlockBreakQueueElement queueElement = new BlockBreakQueueElement(x, y, z, 0); // dimID is irrelevant for this.

            int uuidLength = buf.readInt();
            byte[] uuidBytes = new byte[uuidLength];
            buf.readBytes(uuidBytes);

            String playerUUID = new String(uuidBytes, StandardCharsets.UTF_8);
            queueElement.playerUUIDWhoBrokeBlock = playerUUID;

            queueElement.blockID = buf.readInt();
            queueElement.metadata = buf.readInt();

            eventList.add(queueElement);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        // First write the number of events to be processed
        buf.writeInt(eventList.size());

        // Now write the data for each event
        for (BlockBreakQueueElement queueElement : eventList) {
            // Write position data
            buf.writeDouble(queueElement.x);
            buf.writeDouble(queueElement.y);
            buf.writeDouble(queueElement.z);

            // Write UUID information
            byte[] uuidBytes = queueElement.playerUUIDWhoBrokeBlock.getBytes(StandardCharsets.UTF_8);
            buf.writeInt(uuidBytes.length);  // First write the length of the UUID string
            buf.writeBytes(uuidBytes);       // Then write the UUID string itself

            // Write block ID and metadata
            buf.writeInt(queueElement.blockID);
            buf.writeInt(queueElement.metadata);
        }
    }


    public static class ClientMessageHandler implements IMessageHandler<BlockBreakPacketHandler, IMessage> {
        @Override
        public IMessage onMessage(final BlockBreakPacketHandler message, MessageContext ctx) {
            for (BlockBreakQueueElement queueElement : message.eventList) {
                Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText(BlockUtils.getLocalizedName(queueElement.blockID, queueElement.metadata)));
            }
            return null; // No response packet needed
        }
    }

}
