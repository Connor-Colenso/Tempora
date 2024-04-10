package com.myname.mymodid.PositionalEvents.Loggers.BlockPlace;

import com.myname.mymodid.Utils.BlockUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class BlockPlacePacketHandler implements IMessage {

    public ArrayList<BlockPlaceQueueElement> eventList;

    @Override
    public void fromBytes(ByteBuf buf) {
        int numberOfEvents = buf.readInt();
        eventList = new ArrayList<>(numberOfEvents);

        for (int i = 0; i < numberOfEvents; i++) {
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            int dimensionID = buf.readInt(); // Read dimension ID for this

            BlockPlaceQueueElement queueElement = new BlockPlaceQueueElement(x, y, z, dimensionID);

            int uuidLength = buf.readInt();
            byte[] uuidBytes = new byte[uuidLength];
            buf.readBytes(uuidBytes);

            queueElement.playerUUIDWhoPlacedBlock = new String(uuidBytes, StandardCharsets.UTF_8);

            queueElement.blockID = buf.readInt();
            queueElement.metadata = buf.readInt();

            eventList.add(queueElement);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(eventList.size());

        for (BlockPlaceQueueElement queueElement : eventList) {
            buf.writeDouble(queueElement.x);
            buf.writeDouble(queueElement.y);
            buf.writeDouble(queueElement.z);
            buf.writeInt(queueElement.dimensionId); // Include dimension ID

            byte[] uuidBytes = queueElement.playerUUIDWhoPlacedBlock.getBytes(StandardCharsets.UTF_8);
            buf.writeInt(uuidBytes.length);
            buf.writeBytes(uuidBytes);

            buf.writeInt(queueElement.blockID);
            buf.writeInt(queueElement.metadata);
        }
    }

    public static class ClientMessageHandler implements IMessageHandler<BlockPlacePacketHandler, IMessage> {
        @Override
        public IMessage onMessage(final BlockPlacePacketHandler message, MessageContext ctx) {
            for (BlockPlaceQueueElement queueElement : message.eventList) {
                Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText(
                    BlockUtils.getLocalizedName(queueElement.blockID, queueElement.metadata)
                ));
            }
            return null;
        }
    }
}
