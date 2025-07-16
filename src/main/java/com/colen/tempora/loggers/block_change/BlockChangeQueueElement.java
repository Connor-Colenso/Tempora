package com.colen.tempora.loggers.block_change;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.BlockUtils;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.TimeUtils;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class BlockChangeQueueElement extends GenericQueueElement {

    public int blockID;
    public int metadata;
    public int pickBlockID;
    public int pickBlockMeta;
    public String stackTrace;
    public String encodedNBT;
    public String closestPlayerUUID;
    public double closestPlayerDistance;

    @Override
    public IChatComponent localiseText(String uuid) {

        IChatComponent blockName = BlockUtils.getUnlocalisedChatComponent(blockID, metadata);
        IChatComponent coords = generateTeleportChatComponent(
            x,
            y,
            z,
            dimensionId,
            PlayerUtils.UUIDToName(uuid),
            CoordFormat.INT);
        // We use UUID to determine timezone, for localising.
        IChatComponent timeAgo = TimeUtils.formatTime(timestamp, uuid);

        return new ChatComponentTranslation(
            "message.block_change",
            blockName,
            coords,
            stackTrace,
            timeAgo,
            closestPlayerUUID,
            String.format("%.1f", closestPlayerDistance) // %s (distance)
        );
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        super.fromBytes(buf);
        blockID = buf.readInt();
        metadata = buf.readInt();
        pickBlockID = buf.readInt();
        pickBlockMeta = buf.readInt();
        stackTrace = ByteBufUtils.readUTF8String(buf);
        closestPlayerUUID = ByteBufUtils.readUTF8String(buf);
        closestPlayerDistance = buf.readDouble();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        super.toBytes(buf);
        buf.writeInt(blockID);
        buf.writeInt(metadata);
        buf.writeInt(pickBlockID);
        buf.writeInt(pickBlockMeta);
        ByteBufUtils.writeUTF8String(buf, stackTrace);
        ByteBufUtils.writeUTF8String(buf, closestPlayerUUID);
        buf.writeDouble(closestPlayerDistance);
    }
}
