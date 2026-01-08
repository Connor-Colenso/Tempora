package com.colen.tempora.loggers.player_block_place;

import net.minecraft.block.Block;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.loggers.generic.column.Column;
import com.colen.tempora.utils.BlockUtils;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.TimeUtils;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class PlayerBlockPlaceQueueElement extends GenericQueueElement {

    @Column(constraints = "NOT NULL")
    public int blockID;

    @Column(constraints = "NOT NULL")
    public int metadata;

    @Column(constraints = "NOT NULL")
    public int pickBlockID;

    @Column(constraints = "NOT NULL")
    public int pickBlockMeta;

    @Column(constraints = "NOT NULL")
    public String playerUUID; // todo ensure proper translation to name on client.

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
    public IChatComponent localiseText(String commandIssuerUUID) {
        // Use normalized pickBlockID and pickBlockMeta for translation key
        IChatComponent block = BlockUtils.getUnlocalisedChatComponent(pickBlockID, pickBlockMeta);

        // Clickable coords component
        IChatComponent coords = generateTeleportChatComponent(
            x,
            y,
            z,
            dimensionID,
            PlayerUtils.UUIDToName(commandIssuerUUID),
            CoordFormat.INT);

        // Relative time component
        IChatComponent timeAgo = TimeUtils.formatTime(timestamp);

        // Create translation with playerName, block, raw blockID:metadata, coords, and timeAgo
        return new ChatComponentTranslation(
            "message.block_place",
            PlayerUtils.generatePlayerNameWithUUID(playerUUID),
            block,
            blockID,
            metadata,
            coords,
            timeAgo);
    }

    @Override
    public String getLoggerName() {
        return TemporaEvents.PLAYER_BLOCK_PLACE;
    }

    @Override
    public boolean needsTransparencyToRender() {
        return !Block.getBlockById(blockID)
            .isOpaqueCube();
    }
}
