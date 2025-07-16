package com.colen.tempora.loggers.player_block_break;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.BlockUtils;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.TimeUtils;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

import static com.colen.tempora.utils.nbt.NBTConverter.NO_NBT;

public class PlayerBlockBreakQueueElement extends GenericQueueElement {

    public int blockID;
    public int metadata;
    public int pickBlockID;
    public int pickBlockMeta;
    public String playerUUIDWhoBrokeBlock;
    public String encodedNBT = NO_NBT;

    @Override
    public void fromBytes(ByteBuf buf) {
        super.fromBytes(buf);
        blockID = buf.readInt();
        metadata = buf.readInt();
        pickBlockID = buf.readInt();
        pickBlockMeta = buf.readInt();
        playerUUIDWhoBrokeBlock = ByteBufUtils.readUTF8String(buf);
        encodedNBT = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        super.toBytes(buf);
        buf.writeInt(blockID);
        buf.writeInt(metadata);
        buf.writeInt(pickBlockID);
        buf.writeInt(pickBlockMeta);
        ByteBufUtils.writeUTF8String(buf, playerUUIDWhoBrokeBlock);
        ByteBufUtils.writeUTF8String(buf, encodedNBT);
    }

    @Override
    public IChatComponent localiseText(String uuid) {
        IChatComponent block = BlockUtils.getUnlocalisedChatComponent(pickBlockID, pickBlockMeta);
        IChatComponent coords = generateTeleportChatComponent(
            x,
            y,
            z,
            dimensionId,
            PlayerUtils.UUIDToName(uuid),
            CoordFormat.INT);

        IChatComponent timeAgo = TimeUtils.formatTime(timestamp, uuid);

        return new ChatComponentTranslation(
            "message.block_break",
            PlayerUtils.UUIDToName(playerUUIDWhoBrokeBlock), // player name
            block, // block localized name
            blockID, // block ID
            metadata, // metadata
            coords, // clickable coordinates
            timeAgo // localized relative time
        );
    }
}
