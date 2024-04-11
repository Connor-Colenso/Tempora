package com.myname.mymodid.PositionalEvents.Loggers;

import com.myname.mymodid.PositionalEvents.Loggers.Generic.GenericQueueElement;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import org.apache.commons.lang3.NotImplementedException;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class GenericPacket<QueueElement extends GenericQueueElement> implements IMessage {

    public ArrayList<QueueElement> queueElementArrayList = new ArrayList<>();
    private Class<QueueElement> type; // Class reference for QueueElement

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            Constructor<QueueElement> constructor = type.getDeclaredConstructor(); // Get the default constructor

            int eventCount = buf.readInt();

            for (int elementCounter = 0; elementCounter < eventCount; elementCounter++) {
                QueueElement queueElement = constructor.newInstance(); // Create new instance using constructor

                for(Field field : queueElement.getClass().getDeclaredFields()) {
                    field.setAccessible(true); // Make the field accessible
                    Type fieldType = field.getType();

                    if (fieldType.equals(String.class)) {
                        int length = buf.readInt();
                        byte[] bytes = new byte[length];
                        buf.readBytes(bytes);
                        String value = new String(bytes, StandardCharsets.UTF_8);
                        field.set(queueElement, value); // Set field value
                    } else if (fieldType.equals(int.class)) {
                        field.set(queueElement, buf.readInt()); // Set field value
                    } else if (fieldType.equals(double.class)) {
                        field.set(queueElement, buf.readDouble()); // Set field value
                    } else if (fieldType.equals(float.class)) {
                        field.set(queueElement, buf.readFloat()); // Set field value
                    } else {
                        throw new NotImplementedException("Unsupported type " + fieldType);
                    }
                }
                queueElementArrayList.add(queueElement); // Add the populated element to the list
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to deserialize QueueElement", e);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        try {
            // Write the number of elements in the list to the buffer
            buf.writeInt(queueElementArrayList.size());

            // Iterate over each element in the list
            for (QueueElement element : queueElementArrayList) {
                // Make sure each field is accessible
                for (Field field : element.getClass().getDeclaredFields()) {
                    field.setAccessible(true); // Make the field accessible
                    Type fieldType = field.getType();

                    // Check the type of each field and serialize it accordingly
                    if (fieldType.equals(String.class)) {
                        String value = (String) field.get(element);
                        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                        buf.writeInt(bytes.length); // Write the length of the string
                        buf.writeBytes(bytes);      // Write the string itself
                    } else if (fieldType.equals(int.class)) {
                        Integer value = (Integer) field.get(element);
                        buf.writeInt(value);        // Write the integer value
                    } else if (fieldType.equals(double.class)) {
                        Double value = (Double) field.get(element);
                        buf.writeDouble(value);     // Write the double value
                    } else if (fieldType.equals(float.class)) {
                        Float value = (Float) field.get(element);
                        buf.writeFloat(value);      // Write the float value
                    } else {
                        throw new NotImplementedException("Unsupported type " + fieldType);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to serialize QueueElement", e);
        }
    }

    public static class ClientMessageHandler implements IMessageHandler<GenericPacket, IMessage> {

        @Override
        public IMessage onMessage(final GenericPacket message, MessageContext ctx) {
            // Ensure the operation is run on the client side thread
//            for (QueueElement queueElement : message.queueElementArrayList) {
//                Minecraft.getMinecraft().thePlayer.addChatComponentMessage(new ChatComponentText("Received: " + queueElement.toString()));
//                Minecraft.getMinecraft().thePlayer.addChatComponentMessage(new ChatComponentText("Received: " + queueElement.x));
//            }
            return null; // No response packet needed
        }
    }

}
