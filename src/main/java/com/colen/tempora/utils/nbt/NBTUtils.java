package com.colen.tempora.utils.nbt;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.lang.reflect.Method;
import java.util.Base64;

import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class NBTUtils {

    public static final String NO_NBT = "NO_NBT";
    public static final String NBT_DISABLED = "NBT_PRESENT_LOGGING_OFF";

    // Encode NBTTagCompound to Base64 String
    public static String encodeToString(NBTTagCompound tagCompound) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        DataOutput output = new DataOutputStream(byteArrayOutputStream);

        // Use reflection to call the private write method
        invokeWriteMethod(tagCompound, output);

        // Get the byte array from the output stream
        byte[] nbtData = byteArrayOutputStream.toByteArray();

        // Encode the byte array to a Base64 string
        return Base64.getEncoder()
            .encodeToString(nbtData);
    }

    // Decode Base64 String to NBTTagCompound
    public static NBTTagCompound decodeFromString(String encodedString) {
        byte[] nbtData = Base64.getDecoder()
            .decode(encodedString);
        DataInput input = new DataInputStream(new ByteArrayInputStream(nbtData));

        NBTTagCompound tagCompound = new NBTTagCompound();
        // Use reflection to call the private read method
        invokeReadMethod(tagCompound, input);
        return tagCompound;
    }

    // Method to invoke the private write method
    private static void invokeWriteMethod(NBTTagCompound tagCompound, DataOutput output) {
        try {
            Method writeMethod = NBTTagCompound.class.getDeclaredMethod("write", DataOutput.class);
            writeMethod.setAccessible(true); // Allow access to private method
            writeMethod.invoke(tagCompound, output);
        } catch (Exception e) {
            throw new RuntimeException("Failed to write NBTTagCompound", e);
        }
    }

    // Method to invoke the private read method
    private static void invokeReadMethod(NBTTagCompound tagCompound, DataInput input) {
        try {
            Method readMethod = NBTTagCompound.class
                .getDeclaredMethod("func_152446_a", DataInput.class, int.class, NBTSizeTracker.class);
            readMethod.setAccessible(true); // Allow access to private method
            readMethod.invoke(tagCompound, input, 0, new NBTSizeTracker(Long.MAX_VALUE)); // Initialize NBTSizeTracker
            // with a large size
        } catch (Exception e) {
            throw new RuntimeException("Failed to read NBTTagCompound", e);
        }
    }

    public static String getEncodedTileEntityNBT(World world, int x, int y, int z, boolean nbtLoggingEnabled) {
        if (world == null) return NO_NBT;

        TileEntity tileEntity = world.getTileEntity(x, y, z);
        if (tileEntity == null) {
            return NO_NBT;
        }

        if (nbtLoggingEnabled) {
            NBTTagCompound tag = new NBTTagCompound();
            tileEntity.writeToNBT(tag);
            return encodeToString(tag);
        } else {
            return NBT_DISABLED;
        }
    }

}
