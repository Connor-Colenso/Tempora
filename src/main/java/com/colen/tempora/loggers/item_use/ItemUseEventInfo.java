package com.colen.tempora.loggers.item_use;

import net.minecraft.util.IChatComponent;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.chat.EventChat;
import com.colen.tempora.loggers.generic.PositionalEventInfo;
import com.colen.tempora.loggers.generic.column.Column;
import com.colen.tempora.utils.CommandUtils;
import com.colen.tempora.utils.PlayerUtils;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class ItemUseEventInfo extends PositionalEventInfo {

    @Column(constraints = "NOT NULL")
    public String playerUUID = PlayerUtils.UNKNOWN_PLAYER_NAME;

    @Column(constraints = "NOT NULL")
    public int itemID;

    @Column(constraints = "NOT NULL")
    public int itemMetadata;

    @Override
    public void fromBytes(ByteBuf buf) {
        super.fromBytes(buf);
        playerUUID = ByteBufUtils.readUTF8String(buf);
        itemID = buf.readInt();
        itemMetadata = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        super.toBytes(buf);
        ByteBufUtils.writeUTF8String(buf, playerUUID);
        buf.writeInt(itemID);
        buf.writeInt(itemMetadata);
    }

    @Override
    public IChatComponent localiseText() {
        IChatComponent where = EventChat.where(this, CommandUtils.TeleportType.EXACT);

        // Item 0 is an empty hand, e.g. right clicking a block to open it.
        if (itemID == 0) {
            return EventChat
                .line("message.item_use.empty_hand", EventChat.time(timestamp), EventChat.player(playerUUID), where);
        }

        return EventChat.line(
            "message.item_use",
            EventChat.time(timestamp),
            EventChat.player(playerUUID),
            EventChat.block(itemID, itemMetadata),
            where);
    }

    @Override
    public String getLoggerName() {
        return TemporaEvents.ITEM_USE;
    }
}
