package com.colen.tempora.utils.nbt;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class NBTUtils {

    public static final String NO_NBT = "NO_NBT";
    public static final String NBT_DISABLED = "NBT_PRESENT_LOGGING_OFF";

    // Encode NBTTagCompound to Base64 String
    public static String encodeToString(NBTTagCompound tagCompound) {
        try {
            return Base64.getEncoder()
                .encodeToString(CompressedStreamTools.compress(tagCompound));
        } catch (Exception e) {
            throw new RuntimeException("Failed to encode NBT", e);
        }
    }

    // Decode it back out again.
    public static NBTTagCompound decodeFromString(String encodedString) {
        try {
            byte[] data = Base64.getDecoder()
                .decode(encodedString);
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            return CompressedStreamTools.readCompressed(bais);
        } catch (IOException e) {
            throw new RuntimeException("Failed to decode NBT", e);
        }
    }

    // Realise this seems a bit silly but reduces code duplication elsewhere for handling identical logic.
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

    // An item's NBT as the item loggers store it: NO_NBT when it has none, NBT_DISABLED when it has some but logging
    // NBT is off.
    public static String getEncodedItemNBT(NBTTagCompound tag, boolean nbtLoggingEnabled) {
        if (tag == null) return NO_NBT;
        return nbtLoggingEnabled ? encodeToString(tag) : NBT_DISABLED;
    }

    // Undoes getEncodedItemNBT. Null when there was no NBT, it was not logged, or it cannot be read.
    public static NBTTagCompound decodeItemNBT(String encodedNBT) {
        if (encodedNBT == null || NO_NBT.equals(encodedNBT) || NBT_DISABLED.equals(encodedNBT)) return null;

        try {
            return decodeFromString(encodedNBT);
        } catch (RuntimeException e) {
            return null;
        }
    }

}
