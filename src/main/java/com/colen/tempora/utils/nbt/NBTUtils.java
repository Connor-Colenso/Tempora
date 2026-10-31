package com.colen.tempora.utils.nbt;

import static com.colen.tempora.Tempora.LOG;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.colen.tempora.loggers.generic.PositionalEventInfo;

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

    // ByteBufUtils.writeUTF8String takes at most this many bytes, and encoded NBT is Base64, so one byte per character.
    private static final int MAX_CLIENT_NBT_LENGTH = 16383;

    // Events already warned about, so each is logged once rather than on every lookup that sends it (and twice per
    // send, as RenderEventPacket.sendSafely writes it once to check it first).
    private static final int MAX_WARNED_EVENTS = 1_000;
    private static final Set<String> WARNED_EVENTS = ConcurrentHashMap.newKeySet();

    /**
     * Encoded NBT as sent to the client to draw an event: as stored, unless it is too long for a packet string,
     * when the client is told only that there was some. It then draws the block with a fresh tile
     * entity.
     */
    public static String forClient(String encodedNBT, PositionalEventInfo event) {
        if (encodedNBT == null || encodedNBT.length() <= MAX_CLIENT_NBT_LENGTH) return encodedNBT;

        if (WARNED_EVENTS.size() >= MAX_WARNED_EVENTS) WARNED_EVENTS.clear();
        if (WARNED_EVENTS.add(event.getLoggerName() + ":" + event.eventID)) {
            LOG.warn(
                "{} event {} at {}, {}, {} in dimension {} has {} characters of tile entity NBT, more than the {} a packet "
                    + "can carry. It is drawn with an empty tile entity instead (e.g. a chest without its items); the NBT "
                    + "is still stored in full.",
                event.getLoggerName(),
                event.eventID,
                event.x,
                event.y,
                event.z,
                event.dimensionID,
                encodedNBT.length(),
                MAX_CLIENT_NBT_LENGTH);
        }
        return NBT_DISABLED;
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
