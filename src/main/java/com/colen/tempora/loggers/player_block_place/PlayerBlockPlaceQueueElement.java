package com.colen.tempora.loggers.player_block_place;

import static com.colen.tempora.utils.nbt.NBTConverter.NO_NBT;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.enums.LoggerEnum;
import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.BlockUtils;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.TimeUtils;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class PlayerBlockPlaceQueueElement extends GenericQueueElement {

    public int blockID;
    public int metadata;
    public int pickBlockID;
    public int pickBlockMeta;
    public String playerNameWhoPlacedBlock;
    public String encodedNBT = NO_NBT;

    @Override
    public void fromBytes(ByteBuf buf) {
        super.fromBytes(buf);
        blockID = buf.readInt();
        metadata = buf.readInt();
        pickBlockID = buf.readInt();
        pickBlockMeta = buf.readInt();
        playerNameWhoPlacedBlock = ByteBufUtils.readUTF8String(buf);
        encodedNBT = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        super.toBytes(buf);
        buf.writeInt(blockID);
        buf.writeInt(metadata);
        buf.writeInt(pickBlockID);
        buf.writeInt(pickBlockMeta);
        ByteBufUtils.writeUTF8String(buf, playerNameWhoPlacedBlock);
        ByteBufUtils.writeUTF8String(buf, encodedNBT);
    }

    @Override
    public IChatComponent localiseText(String uuid) {
        // Use normalized pickBlockID and pickBlockMeta for translation key
        IChatComponent block = BlockUtils.getUnlocalisedChatComponent(pickBlockID, pickBlockMeta);

        // Clickable coords component
        IChatComponent coords = generateTeleportChatComponent(
            x,
            y,
            z,
            dimensionId,
            PlayerUtils.UUIDToName(uuid),
            CoordFormat.INT);

        // Relative time component
        IChatComponent timeAgo = TimeUtils.formatTime(timestamp, uuid);

        // Create translation with playerName, block, raw blockID:metadata, coords, and timeAgo
        return new ChatComponentTranslation(
            "message.block_place",
            playerNameWhoPlacedBlock, // %0 – player name
            block,
            blockID,
            metadata,
            coords,
            timeAgo);
    }

    @Override
    public LoggerEnum getLoggerType() {
        return LoggerEnum.PlayerBlockPlaceLogger;
    }
}
