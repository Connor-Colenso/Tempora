package com.colen.tempora.loggers.inventory;

import static com.colen.tempora.utils.CommandUtils.teleportChatComponent;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.loggers.generic.GenericEventInfo;
import com.colen.tempora.loggers.generic.column.Column;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.TimeUtils;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class InventoryEventInfo extends GenericEventInfo {

    @Column(constraints = "NOT NULL")
    public String containerName;

    @Column(constraints = "NOT NULL")
    public int interactionType; // Note: DB column is TEXT, but the field is an int – consider changing either to match

    @Column(constraints = "NOT NULL")
    public String playerUUID;

    @Column(constraints = "NOT NULL")
    public int itemId; // corresponds to "itemID" column

    @Column(constraints = "NOT NULL")
    public int itemMetadata;

    @Column(constraints = "NOT NULL")
    public int stackSize; // corresponds to "stacksize" column

    @Override
    public void fromBytes(ByteBuf buf) {
        super.fromBytes(buf);
        containerName = ByteBufUtils.readUTF8String(buf);
        interactionType = buf.readInt();
        itemId = buf.readInt();
        itemMetadata = buf.readInt();
        playerUUID = ByteBufUtils.readUTF8String(buf);
        stackSize = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        super.toBytes(buf);
        ByteBufUtils.writeUTF8String(buf, containerName);
        buf.writeInt(interactionType);
        buf.writeInt(itemId);
        buf.writeInt(itemMetadata);
        ByteBufUtils.writeUTF8String(buf, playerUUID);
        buf.writeInt(stackSize);
    }

    @Override
    public IChatComponent localiseText(String commandIssuerUUID) {
        IChatComponent formattedTime = TimeUtils.formatTime(timestamp);

        // Try to localise the item name...
        ItemStack itemStack = new ItemStack(Item.getItemById(itemId), stackSize, itemMetadata);
        IChatComponent itemDetails = new ChatComponentTranslation(itemStack.getDisplayName());

        IChatComponent coords = teleportChatComponent(x, y, z, dimensionID);

        InventoryLogger.Direction dir = InventoryLogger.Direction.fromOrdinal(interactionType);

        if (dir == null) {
            return new ChatComponentTranslation("message.inventorylogger.invalid_interaction", interactionType);
        }

        String translationKey = dir.isAddition() ? "message.inventory_interaction_added"
            : "message.inventory_interaction_removed";

        return new ChatComponentTranslation(
            translationKey,
            PlayerUtils.playerNameFromUUID(playerUUID),
            stackSize,
            itemDetails,
            itemId,
            itemMetadata,
            new ChatComponentTranslation(containerName),
            coords,
            formattedTime);
    }

    @Override
    public String getLoggerName() {
        return TemporaEvents.INVENTORY;
    }
}
