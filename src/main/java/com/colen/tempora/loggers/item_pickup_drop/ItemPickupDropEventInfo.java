package com.colen.tempora.loggers.item_pickup_drop;

import static com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.chat.EventChat;
import com.colen.tempora.loggers.generic.PositionalEventInfo;
import com.colen.tempora.loggers.generic.column.Column;
import com.colen.tempora.utils.CommandUtils;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.nbt.NBTUtils;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class ItemPickupDropEventInfo extends PositionalEventInfo {

    // An ItemPickupDropLogger.Action.
    @Column(constraints = "NOT NULL")
    public int action;

    @Column(constraints = "NOT NULL")
    public String playerUUID = PlayerUtils.UNKNOWN_PLAYER_NAME;

    @Column(constraints = "NOT NULL")
    public int itemID;

    @Column(constraints = "NOT NULL")
    public int itemMetadata;

    @Column(constraints = "NOT NULL")
    public int amount;

    // NBTUtils.NO_NBT when the item had none, NBTUtils.NBT_DISABLED when it had some but logNBT was off.
    @Column(constraints = "NOT NULL")
    public String encodedNBT = NBTUtils.NO_NBT;

    // Which way the player was looking, in degrees as Minecraft stores it. A thrown item sets off this way.
    @Column(constraints = "NOT NULL")
    public float rotationYaw;

    @Column(constraints = "NOT NULL")
    public float rotationPitch;

    // encodedNBT is deliberately not sent, as for the inventory logger: the client only draws these events, and item
    // NBT can be far larger than a packet string allows.
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
            IChatComponent invalid = new ChatComponentTranslation("tempora.event.item_pickup_drop.invalid", action);
            invalid.getChatStyle()
                .setColor(EnumChatFormatting.RED);
            return invalid;
        }

        // Green for items the player gained, red for items they lost.
        IChatComponent count = EventChat.text(
            formatNumber(amount),
            act == ItemPickupDropLogger.Action.PICKUP ? EnumChatFormatting.GREEN : EnumChatFormatting.RED);

        return EventChat.line(
            act.langKey(),
            EventChat.time(timestamp),
            EventChat.player(playerUUID),
            count,
            EventChat.item(itemID, itemMetadata, encodedNBT),
            EventChat.where(this, CommandUtils.TeleportType.EXACT));
    }

    @Override
    public String getLoggerName() {
        return TemporaEvents.ITEM_PICKUP_DROP;
    }
}
