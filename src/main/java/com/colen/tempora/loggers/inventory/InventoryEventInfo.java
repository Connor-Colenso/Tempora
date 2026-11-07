package com.colen.tempora.loggers.inventory;

import static com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.chat.EventChat;
import com.colen.tempora.loggers.generic.PositionalEventInfo;
import com.colen.tempora.loggers.generic.column.Column;
import com.colen.tempora.loggers.generic.column.ColumnType;
import com.colen.tempora.utils.CommandUtils;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.nbt.NBTUtils;
import com.gtnewhorizon.gtnhlib.chat.customcomponents.ChatComponentNumber;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class InventoryEventInfo extends PositionalEventInfo {

    @Column
    public String containerName = "";

    @Column
    public int direction;

    @Column(type = ColumnType.PLAYER)
    public String playerUUID = PlayerUtils.UNKNOWN_PLAYER_NAME;

    @Column
    public int itemID;

    @Column
    public int itemMetadata;

    @Column
    public int amount;

    // NBTUtils.NO_NBT if the item had none, NBTUtils.NBT_DISABLED if it had some but logNBT was off
    @Column(type = ColumnType.NBT)
    public String encodedNBT = NBTUtils.NO_NBT;

    // encodedNBT isn't sent on purpose, the client only draws boxes from these and item NBT can be way bigger than a
    // packet string allows
    @Override
    public void fromBytes(ByteBuf buf) {
        super.fromBytes(buf);
        containerName = ByteBufUtils.readUTF8String(buf);
        direction = buf.readInt();
        itemID = buf.readInt();
        itemMetadata = buf.readInt();
        playerUUID = ByteBufUtils.readUTF8String(buf);
        amount = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        super.toBytes(buf);
        ByteBufUtils.writeUTF8String(buf, containerName);
        buf.writeInt(direction);
        buf.writeInt(itemID);
        buf.writeInt(itemMetadata);
        ByteBufUtils.writeUTF8String(buf, playerUUID);
        buf.writeInt(amount);
    }

    @Override
    public IChatComponent localiseText() {
        InventoryLogger.Direction dir = InventoryLogger.Direction.fromDbId(direction);
        if (dir == null) {
            IChatComponent invalid = new ChatComponentTranslation(
                "tempora.event.inventory.invalid",
                new ChatComponentNumber(direction));
            invalid.getChatStyle()
                .setColor(EnumChatFormatting.RED);
            return invalid;
        }

        // Green for put in, red for taken out
        boolean added = dir.isAddition();
        IChatComponent count = EventChat
            .text(formatNumber(amount), added ? EnumChatFormatting.GREEN : EnumChatFormatting.RED);

        // Plain text, the name was already worked out on the server and a renamed chest can have a % in it
        IChatComponent container = EventChat.text(containerName, EnumChatFormatting.WHITE);

        // Containers carried by a player (backpacks, wireless terminals) are logged at the player, still the right
        // place to teleport to
        return EventChat.line(
            added ? "tempora.event.inventory.added" : "tempora.event.inventory.removed",
            EventChat.time(timestamp),
            EventChat.player(playerUUID),
            count,
            EventChat.item(itemID, itemMetadata, encodedNBT),
            container,
            EventChat.where(this, CommandUtils.TeleportType.BLOCK));
    }

    @Override
    public String getLoggerName() {
        return TemporaEvents.INVENTORY;
    }
}
