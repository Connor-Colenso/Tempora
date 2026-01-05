package com.colen.tempora.loggers.inventory;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.TimeUtils;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class InventoryQueueElement extends GenericQueueElement {

    public String containerName;
    public int interactionType;
    public int itemId;
    public int itemMetadata;
    public String playerUUID;
    public int stackSize;

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
    public IChatComponent localiseText(String uuid) {
        IChatComponent formattedTime = TimeUtils.formatTime(timestamp, uuid);
        String playerName = PlayerUtils.UUIDToName(playerUUID);

        // Try localise the item name...
        ItemStack itemStack = new ItemStack(Item.getItemById(itemId), stackSize, itemMetadata);
        IChatComponent itemDetails = new ChatComponentTranslation(itemStack.getDisplayName());

        IChatComponent coords = generateTeleportChatComponent(x, y, z, dimensionId, PlayerUtils.UUIDToName(uuid));

        InventoryLogger.Direction dir = InventoryLogger.Direction.fromOrdinal(interactionType);

        if (dir == null) {
            return new ChatComponentTranslation("message.inventorylogger.invalid_interaction", interactionType);
        }

        String translationKey = dir.isAddition() ? "message.inventory_interaction_added"
            : "message.inventory_interaction_removed";

        return new ChatComponentTranslation(
            translationKey,
            playerName,
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
