package com.colen.tempora.utils.nbt;

import static com.colen.tempora.Tempora.LOG;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.item.Item;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.colen.tempora.loggers.generic.PositionalEventInfo;

public class NBTUtils {

    // Stored in place of NBT so kept to a char at most, nearly every block and item event has one. None of them can be
    // Base64 so real NBT never gets mistaken for one
    public static final String NO_NBT = "";
    // There was NBT but NBT logging is off
    public static final String NBT_DISABLED = "-";
    // TE or item threw while saving its NBT (another mod's bug), nothing to store
    public static final String NBT_UNREADABLE = "!";

    // Whether this is real encoded NBT, not null or one of the markers above
    public static boolean hasStoredNBT(String encodedNBT) {
        return encodedNBT != null && !NO_NBT.equals(encodedNBT)
            && !NBT_DISABLED.equals(encodedNBT)
            && !NBT_UNREADABLE.equals(encodedNBT);
    }

    // NBTTagCompound to Base64
    public static String encodeToString(NBTTagCompound tagCompound) {
        try {
            return Base64.getEncoder()
                .encodeToString(CompressedStreamTools.compress(tagCompound));
        } catch (Exception e) {
            throw new RuntimeException("Failed to encode NBT", e);
        }
    }

    // And back again
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

    // TE types already warned about, see getEncodedTileEntityNBT
    private static final Set<Class<?>> UNSAVABLE_TILE_ENTITIES = ConcurrentHashMap.newKeySet();

    // Seems a bit silly but saves duplicating the same logic elsewhere
    public static String getEncodedTileEntityNBT(World world, int x, int y, int z, boolean nbtLoggingEnabled) {
        if (world == null) return NO_NBT;

        TileEntity tileEntity = world.getTileEntity(x, y, z);
        if (tileEntity == null) {
            return NO_NBT;
        }

        if (nbtLoggingEnabled) {
            try {
                NBTTagCompound tag = new NBTTagCompound();
                tileEntity.writeToNBT(tag);
                return encodeToString(tag);
            } catch (RuntimeException | LinkageError e) {
                // Another mod's TE that can't save itself. Logging must never stop a block being broken or changed, so
                // the event gets stored without its NBT
                if (UNSAVABLE_TILE_ENTITIES.add(tileEntity.getClass())) {
                    LOG.warn(
                        "{} at {}, {}, {} in dimension {} threw while saving its NBT, so Tempora stores its events without "
                            + "NBT. Logged once per tile entity type.",
                        tileEntity.getClass()
                            .getName(),
                        x,
                        y,
                        z,
                        world.provider.dimensionId,
                        e);
                }
                return NBT_UNREADABLE;
            }
        } else {
            return NBT_DISABLED;
        }
    }

    // ByteBufUtils.writeUTF8String takes at most this many bytes, and encoded NBT is Base64 so one byte per char
    private static final int MAX_CLIENT_NBT_LENGTH = 16383;

    // Events already warned about, so each gets logged once instead of on every lookup that sends it (and twice per
    // send since RenderEventPacket.sendSafely writes it once to check it first)
    private static final int MAX_WARNED_EVENTS = 1_000;
    private static final Set<String> WARNED_EVENTS = ConcurrentHashMap.newKeySet();

    // Encoded NBT as sent to the client to draw an event. As stored, unless it's too long for a packet string, then the
    // client only gets told there was some and draws the block with a fresh TE
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

    // Items already warned about, see getEncodedItemNBT
    private static final Set<Item> UNSAVABLE_ITEMS = ConcurrentHashMap.newKeySet();

    // Item NBT as the item loggers store it. NO_NBT if none, NBT_DISABLED if it has some but NBT logging is off,
    // NBT_UNREADABLE if it can't be encoded
    public static String getEncodedItemNBT(Item item, NBTTagCompound tag, boolean nbtLoggingEnabled) {
        if (tag == null) return NO_NBT;
        if (!nbtLoggingEnabled) return NBT_DISABLED;

        try {
            return encodeToString(tag);
        } catch (RuntimeException e) {
            // A tag another mod filled with something NBT can't write (e.g. a null string). Event still gets stored
            // without the NBT
            if (UNSAVABLE_ITEMS.add(item)) {
                LOG.warn(
                    "The NBT of a {} could not be encoded, so Tempora stores its events without NBT. Logged once per item.",
                    Item.itemRegistry.getNameForObject(item),
                    e);
            }
            return NBT_UNREADABLE;
        }
    }

    // Undoes getEncodedItemNBT. Null if there was no NBT, it wasn't logged or it can't be read
    public static NBTTagCompound decodeItemNBT(String encodedNBT) {
        if (!hasStoredNBT(encodedNBT)) return null;

        try {
            return decodeFromString(encodedNBT);
        } catch (RuntimeException e) {
            return null;
        }
    }

}
