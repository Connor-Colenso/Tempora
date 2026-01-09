package com.colen.tempora.loggers.item_use;

import static com.colen.tempora.utils.ItemUtils.getNameOfItemStack;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.loggers.generic.column.Column;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.TimeUtils;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class ItemUseQueueElement extends GenericQueueElement {

    @Column(constraints = "NOT NULL")
    public String playerUUID;

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
    public IChatComponent localiseText(String commandIssuerUUID) {
        IChatComponent coords = teleportChatComponent(x, y, z, dimensionID, CoordFormat.FLOAT_1DP);
        IChatComponent timeAgo = TimeUtils.formatTime(timestamp);

        return new ChatComponentTranslation(
            "message.item_use",
            PlayerUtils.playerNameFromUUID(playerUUID),
            getNameOfItemStack(itemID, itemMetadata),
            itemID,
            itemMetadata,
            coords,
            timeAgo);
    }

    @Override
    public String getLoggerName() {
        return TemporaEvents.ITEM_USE;
    }
}
