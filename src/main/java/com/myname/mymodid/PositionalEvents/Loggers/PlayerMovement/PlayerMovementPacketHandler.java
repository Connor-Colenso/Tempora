package com.myname.mymodid.PositionalEvents.Loggers.PlayerMovement;

import com.myname.mymodid.Utils.TimeUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.StatCollector;

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
            long timestamp = buf.readLong(); // Read timestamp

            PlayerMovementQueueElement queueElement = new PlayerMovementQueueElement(x, y, z, dimensionID);
            queueElement.timestamp = timestamp; // Assign timestamp

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
        buf.writeInt(eventList.size()); // Write the number of events

        for (PlayerMovementQueueElement queueElement : eventList) {
            buf.writeDouble(queueElement.x);
            buf.writeDouble(queueElement.y);
            buf.writeDouble(queueElement.z);
            buf.writeInt(queueElement.dimensionId);
            buf.writeLong(queueElement.timestamp); // Write timestamp

            byte[] uuidBytes = queueElement.playerUUID.getBytes(StandardCharsets.UTF_8);
            buf.writeInt(uuidBytes.length);  // Write the length of the UUID string
            buf.writeBytes(uuidBytes);       // Write the UUID string itself
        }
    }

    public static class ClientMessageHandler implements IMessageHandler<PlayerMovementPacketHandler, IMessage> {
        @Override
        public IMessage onMessage(final PlayerMovementPacketHandler message, MessageContext ctx) {
            for (PlayerMovementQueueElement queueElement : message.eventList) {
                String formattedTime = TimeUtils.formatTime(queueElement.timestamp); // Format time according to user's timezone

                // Get the localized message template from Minecraft's localization system
                String localizedMessage = StatCollector.translateToLocalFormatted("player.movement.message",
                    queueElement.x, queueElement.y, queueElement.z, queueElement.dimensionId, formattedTime);

                Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText(localizedMessage));
            }
            return null; // No response packet needed
        }
    }
}
