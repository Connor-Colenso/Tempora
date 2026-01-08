package com.colen.tempora.loggers.player_block_break;

import net.minecraft.block.Block;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.loggers.generic.column.Column;
import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.BlockUtils;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.TimeUtils;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class PlayerBlockBreakQueueElement extends GenericQueueElement {

    @Column(constraints = "NOT NULL")
    public int blockID;

    @Column(constraints = "NOT NULL")
    public int metadata;

    @Column(constraints = "NOT NULL")
    public int pickBlockID;

    @Column(constraints = "NOT NULL")
    public int pickBlockMeta;

    @Column(constraints = "NOT NULL")
    public String playerUUID;

    @Column(constraints = "NOT NULL")
    public String encodedNBT;

    @Override
    public void fromBytes(ByteBuf buf) {
        super.fromBytes(buf);
        blockID = buf.readInt();
        metadata = buf.readInt();
        pickBlockID = buf.readInt();
        pickBlockMeta = buf.readInt();
        playerUUID = ByteBufUtils.readUTF8String(buf);
        encodedNBT = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        super.toBytes(buf);
        buf.writeInt(blockID);
        buf.writeInt(metadata);
        buf.writeInt(pickBlockID);
        buf.writeInt(pickBlockMeta);
        ByteBufUtils.writeUTF8String(buf, playerUUID);
        ByteBufUtils.writeUTF8String(buf, encodedNBT);
    }

    @Override
    public IChatComponent localiseText(String playerUUID) {
        IChatComponent block = BlockUtils.getUnlocalisedChatComponent(pickBlockID, pickBlockMeta);
        IChatComponent coords = generateTeleportChatComponent(
            x,
            y,
            z,
            dimensionID,
            PlayerUtils.UUIDToName(playerUUID),
            CoordFormat.INT);

        IChatComponent timeAgo = TimeUtils.formatTime(timestamp);

        return new ChatComponentTranslation(
            "message.block_break",
            // PlayerUtils.UUIDToName(this.playerUUID), // player name
            playerUUID,
            block, // block localized name
            blockID, // block ID
            metadata, // metadata
            coords, // clickable coordinates
            timeAgo, // localized relative time
            generateUndoCommand(getLoggerName(), eventID));
    }

    @Override
    public String getLoggerName() {
        return TemporaEvents.PLAYER_BLOCK_BREAK;
    }

    @Override
    public boolean needsTransparencyToRender() {
        return !Block.getBlockById(blockID)
            .isOpaqueCube();
    }
}
