package com.colen.tempora.logging.loggers.generic;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;

import org.apache.commons.lang3.NotImplementedException;

import com.colen.tempora.TemporaUtils;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class GenericPacket implements IMessage {

    // Do not delete! It will cause errors.
    @SuppressWarnings("unused")
    public GenericPacket() {}

    public ArrayList<ISerializable> queueElementArrayList = new ArrayList<>();

    public GenericPacket(ArrayList<ISerializable> queueElementArrayList) {
        this.queueElementArrayList = queueElementArrayList;
    }

    @Override
    public void fromBytes(ByteBuf buf) {

        if (TemporaUtils.isServerSide()) {
            System.err.println(
                "Warning! Tempora has detected suspicious behaviour with incorrect packets being sent to the server. This may be a malicious actor.");
            return;
        }

        try {
            // Read the class name from the buffer
            int classNameLength = buf.readInt();
            byte[] stringBytes = new byte[classNameLength];
            buf.readBytes(stringBytes);
            String className = new String(stringBytes, StandardCharsets.UTF_8);

            // Getting the Class object for the given class name and ensure it is assignable to ISerializable
            Class<?> rawClass = Class.forName(className);
            if (!ISerializable.class.isAssignableFrom(rawClass)) {
                throw new ClassNotFoundException("Class does not implement ISerializable: " + className);
            }
            Class<? extends ISerializable> clazz = rawClass.asSubclass(ISerializable.class);

            // Read the number of elements
            int eventCount = buf.readInt();

            // Processing each element
            for (int elementCounter = 0; elementCounter < eventCount; elementCounter++) {
                ISerializable queueElement = clazz.getDeclaredConstructor()
                    .newInstance(); // Create a new instance for each element

                for (Field field : queueElement.getClass()
                    .getFields()) {
                    Type fieldType = field.getType();

                    // Deserialize data based on field type
                    if (fieldType.equals(String.class)) {
                        int length = buf.readInt();
                        byte[] bytes = new byte[length];
                        buf.readBytes(bytes);
                        String value = new String(bytes, StandardCharsets.UTF_8);
                        field.set(queueElement, value);
                    } else if (fieldType.equals(int.class)) {
                        field.set(queueElement, buf.readInt());
                    } else if (fieldType.equals(long.class)) {
                        field.set(queueElement, buf.readLong());
                    } else if (fieldType.equals(double.class)) {
                        field.set(queueElement, buf.readDouble());
                    } else if (fieldType.equals(float.class)) {
                        field.set(queueElement, buf.readFloat());
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
            // Type
            byte[] classNameBytes = queueElementArrayList.get(0)
                .getClass()
                .getName()
                .getBytes(StandardCharsets.UTF_8);
            buf.writeInt(classNameBytes.length); // Write the length of the string
            buf.writeBytes(classNameBytes); // Write the string itself

            // Write the number of elements in the list to the buffer
            buf.writeInt(queueElementArrayList.size());

            // Iterate over each element in the list
            for (ISerializable element : queueElementArrayList) {
                // Make sure each field is accessible
                for (Field field : element.getClass()
                    .getFields()) {
                    Type fieldType = field.getType();

                    // Check the type of each field and serialize it accordingly
                    if (fieldType.equals(String.class)) {
                        String value = (String) field.get(element);
                        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                        buf.writeInt(bytes.length); // Write the length of the string
                        buf.writeBytes(bytes); // Write the string itself
                    } else if (fieldType.equals(int.class)) {
                        Integer value = (Integer) field.get(element);
                        buf.writeInt(value);
                    } else if (fieldType.equals(long.class)) {
                        Long value = (Long) field.get(element);
                        buf.writeLong(value);
                    } else if (fieldType.equals(double.class)) {
                        Double value = (Double) field.get(element);
                        buf.writeDouble(value);
                    } else if (fieldType.equals(float.class)) {
                        Float value = (Float) field.get(element);
                        buf.writeFloat(value);
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
        public IMessage onMessage(final GenericPacket packet, MessageContext ctx) {

            // Assuming packet.queueElementArrayList is a List that supports accessing elements by index

            List<ISerializable> list = packet.queueElementArrayList;
            for (int i = list.size() - 1; i >= 0; i--) {
                ISerializable iSerializable = list.get(i);
                String message = iSerializable.localiseText();
                if (message == null) continue;

                Minecraft.getMinecraft().thePlayer.addChatComponentMessage(new ChatComponentText(message));
            }

            return null; // No response packet needed
        }
    }

}
