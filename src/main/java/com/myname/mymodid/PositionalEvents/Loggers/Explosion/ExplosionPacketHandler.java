package com.myname.mymodid.PositionalEvents.Loggers.Explosion;

import com.myname.mymodid.Utils.BlockUtils; // Ensure this is replaced or corrected to suit explosion-related utilities if required.
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class ExplosionPacketHandler implements IMessage {

    public ArrayList<ExplosionQueueElement> eventList;

    @Override
    public void fromBytes(ByteBuf buf) {
        int numberOfEvents = buf.readInt();
        eventList = new ArrayList<>(numberOfEvents);

        for (int i = 0; i < numberOfEvents; i++) {
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            float strength = buf.readFloat();
            int dimensionID = buf.readInt();

            ExplosionQueueElement queueElement = new ExplosionQueueElement(x, y, z, dimensionID);

            int exploderLength = buf.readInt();
            byte[] exploderBytes = new byte[exploderLength];
            buf.readBytes(exploderBytes);
            queueElement.exploderName = new String(exploderBytes, StandardCharsets.UTF_8);

            int playerLength = buf.readInt();
            byte[] playerBytes = new byte[playerLength];
            buf.readBytes(playerBytes);
            queueElement.closestPlayerUUID = new String(playerBytes, StandardCharsets.UTF_8);

            queueElement.closestPlayerUUIDDistance = buf.readDouble();
            queueElement.strength = strength;

            eventList.add(queueElement);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(eventList.size());

        for (ExplosionQueueElement queueElement : eventList) {
            buf.writeDouble(queueElement.x);
            buf.writeDouble(queueElement.y);
            buf.writeDouble(queueElement.z);
            buf.writeFloat(queueElement.strength);
            buf.writeInt(queueElement.dimensionId);

            byte[] exploderBytes = queueElement.exploderName.getBytes(StandardCharsets.UTF_8);
            buf.writeInt(exploderBytes.length);
            buf.writeBytes(exploderBytes);

            byte[] playerBytes = queueElement.closestPlayerUUID.getBytes(StandardCharsets.UTF_8);
            buf.writeInt(playerBytes.length);
            buf.writeBytes(playerBytes);

            buf.writeDouble(queueElement.closestPlayerUUIDDistance);
        }
    }

    public static class ClientMessageHandler implements IMessageHandler<ExplosionPacketHandler, IMessage> {
        @Override
        public IMessage onMessage(final ExplosionPacketHandler message, MessageContext ctx) {
            Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText("Explosion detected!"));
            // Here, additional client-side logic can be implemented, such as effects or warnings based on the explosion data received.
            return null; // No response packet needed
        }
    }

}
