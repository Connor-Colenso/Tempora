package com.colen.tempora.loggers.item_use;

import static com.colen.tempora.utils.ItemUtils.getNameOfItemStack;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.TimeUtils;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class ItemUseQueueElement extends GenericQueueElement {

    public String playerName;
    public int itemID;
    public int itemMetadata;

    @Override
    public void fromBytes(ByteBuf buf) {
        super.fromBytes(buf);
        playerName = ByteBufUtils.readUTF8String(buf);
        itemID = buf.readInt();
        itemMetadata = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        super.toBytes(buf);
        ByteBufUtils.writeUTF8String(buf, playerName);
        buf.writeInt(itemID);
        buf.writeInt(itemMetadata);
    }

    @Override
    public IChatComponent localiseText(String uuid) {
        IChatComponent coords = generateTeleportChatComponent(
            x,
            y,
            z,
            dimensionId,
            PlayerUtils.UUIDToName(uuid),
            CoordFormat.FLOAT_1DP);
        IChatComponent timeAgo = TimeUtils.formatTime(timestamp, uuid);

        return new ChatComponentTranslation(
            "message.item_use",
            playerName,
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
