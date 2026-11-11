package com.colen.tempora.loggers.item_pickup_drop;

import static com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.chat.EventChat;
import com.colen.tempora.loggers.generic.PositionalEventInfo;
import com.colen.tempora.loggers.generic.column.Column;
import com.colen.tempora.loggers.generic.column.ColumnType;
import com.colen.tempora.loggers.generic.column.names.lookups.ItemNames;
import com.colen.tempora.utils.CommandUtils;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.nbt.NBTUtils;
import com.gtnewhorizon.gtnhlib.chat.customcomponents.ChatComponentNumber;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class ItemPickupDropEventInfo extends PositionalEventInfo {

    // An ItemPickupDropLogger.Action
    @Column
    public int action;

    @Column(type = ColumnType.PLAYER)
    public String playerUUID = PlayerUtils.UNKNOWN_PLAYER_NAME;

    @Column(nameRole = ItemNames.class, part = ItemNames.ID)
    public int itemID;

    @Column(nameRole = ItemNames.class, part = ItemNames.META)
    public int itemMetadata;

    @Column
    public int amount;

    // NBTUtils.NO_NBT if the item had none, NBTUtils.NBT_DISABLED if it had some but logNBT was off
    @Column(type = ColumnType.NBT)
    public String encodedNBT = NBTUtils.NO_NBT;

    // Which way the player was looking in degrees like MC stores it, a thrown item heads off this way
    @Column
    public float rotationYaw;

    @Column
    public float rotationPitch;

    // encodedNBT isn't sent on purpose, same as the inventory logger. The client only draws these and item NBT can be
    // way bigger than a packet string allows
    @Override
    public void fromBytes(ByteBuf buf) {
        super.fromBytes(buf);
        action = buf.readInt();
        playerUUID = ByteBufUtils.readUTF8String(buf);
        itemID = buf.readInt();
        itemMetadata = buf.readInt();
        amount = buf.readInt();
        rotationYaw = buf.readFloat();
        rotationPitch = buf.readFloat();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        super.toBytes(buf);
        buf.writeInt(action);
        ByteBufUtils.writeUTF8String(buf, playerUUID);
        buf.writeInt(itemID);
        buf.writeInt(itemMetadata);
        buf.writeInt(amount);
        buf.writeFloat(rotationYaw);
        buf.writeFloat(rotationPitch);
    }

    @Override
    public IChatComponent localiseText() {
        ItemPickupDropLogger.Action act = ItemPickupDropLogger.Action.fromDbId(action);
        if (act == null) {
            IChatComponent invalid = new ChatComponentTranslation(
                "tempora.event.item_pickup_drop.invalid",
                new ChatComponentNumber(action));
            invalid.getChatStyle()
                .setColor(EnumChatFormatting.RED);
            return invalid;
        }

        // Green for gained, red for lost
        IChatComponent count = EventChat.text(
            formatNumber(amount),
            act == ItemPickupDropLogger.Action.PICKUP ? EnumChatFormatting.GREEN : EnumChatFormatting.RED);

        return EventChat.line(
            act.langKey(),
            EventChat.time(timestamp),
            EventChat.player(playerUUID),
            count,
            EventChat.searchable(EventChat.item(itemID, itemMetadata, encodedNBT)),
            EventChat.where(this, CommandUtils.TeleportType.EXACT));
    }

    @Override
    public String getLoggerName() {
        return TemporaEvents.ITEM_PICKUP_DROP;
    }
}
