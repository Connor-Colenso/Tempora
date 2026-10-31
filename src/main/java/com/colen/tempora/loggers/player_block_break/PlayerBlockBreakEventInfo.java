package com.colen.tempora.loggers.player_block_break;

import net.minecraft.util.IChatComponent;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.chat.EventChat;
import com.colen.tempora.loggers.generic.PositionalEventInfo;
import com.colen.tempora.loggers.generic.column.Column;
import com.colen.tempora.loggers.generic.column.ColumnType;
import com.colen.tempora.utils.CommandUtils;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.nbt.NBTUtils;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class PlayerBlockBreakEventInfo extends PositionalEventInfo {

    @Column(constraints = "NOT NULL")
    public int blockID;

    @Column(constraints = "NOT NULL")
    public int metadata;

    @Column(constraints = "NOT NULL")
    public int pickBlockID;

    @Column(constraints = "NOT NULL")
    public int pickBlockMeta;

    @Column(type = ColumnType.PLAYER, constraints = "NOT NULL")
    public String playerUUID = PlayerUtils.UNKNOWN_PLAYER_NAME;

    @Column(constraints = "NOT NULL")
    public String encodedNBT = NBTUtils.NO_NBT;

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
        ByteBufUtils.writeUTF8String(buf, NBTUtils.forClient(encodedNBT, this));
    }

    @Override
    public IChatComponent localiseText() {
        return EventChat.line(
            "tempora.event.block_break",
            EventChat.time(timestamp),
            EventChat.player(playerUUID),
            EventChat.block(pickBlockID, pickBlockMeta, blockID, metadata),
            EventChat.where(this, CommandUtils.TeleportType.BLOCK));
    }

    @Override
    public String getLoggerName() {
        return TemporaEvents.PLAYER_BLOCK_BREAK;
    }
}
