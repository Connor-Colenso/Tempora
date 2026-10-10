package com.colen.tempora.loggers.inventory;

import static com.colen.tempora.utils.CommandUtils.teleportChatComponent;

import net.minecraft.event.HoverEvent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.loggers.generic.GenericEventInfo;
import com.colen.tempora.loggers.generic.column.Column;
import com.colen.tempora.utils.CommandUtils;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.TimeUtils;
import com.colen.tempora.utils.nbt.NBTUtils;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class InventoryEventInfo extends GenericEventInfo {

    // Chat packets cannot carry arbitrarily large hover text, so huge NBT (full backpacks, AE2 cells) is not shown.
    private static final int MAX_HOVER_NBT_LENGTH = 8192;

    @Column(constraints = "NOT NULL")
    public String containerName;

    @Column(constraints = "NOT NULL")
    public int interactionType;

    @Column(constraints = "NOT NULL")
    public String playerUUID;

    @Column(constraints = "NOT NULL")
    public int itemId;

    @Column(constraints = "NOT NULL")
    public int itemMetadata;

    @Column(constraints = "NOT NULL")
    public int stackSize;

    // NBTUtils.NO_NBT when the item had none, NBTUtils.NBT_DISABLED when it had some but logNBT was off.
    @Column(constraints = "NOT NULL DEFAULT '" + NBTUtils.NO_NBT + "'")
    public String encodedNBT = NBTUtils.NO_NBT;

    // encodedNBT is deliberately not sent: the client only uses these events to draw boxes, and item NBT can be far
    // larger than a packet string allows.
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

        IChatComponent itemDetails = itemChatComponent();

        // Todo what about e.g. player backbacks, do we need to handle block coords differently?
        IChatComponent coords = teleportChatComponent(x, y, z, dimensionID, CommandUtils.TeleportType.BLOCK);

        InventoryLogger.Direction dir = InventoryLogger.Direction.fromDbId(interactionType);

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

    // The item's name, with its tooltip on hover when the NBT was logged.
    private IChatComponent itemChatComponent() {
        Item item = Item.getItemById(itemId);
        if (item == null) {
            return new ChatComponentText("[unknown item " + itemId + ":" + itemMetadata + "]");
        }

        ItemStack itemStack = new ItemStack(item, stackSize, itemMetadata);
        NBTTagCompound tag = decodeItemNBT();
        if (tag != null) itemStack.setTagCompound(tag);

        // Already localised here, like vanilla's own item mentions.
        IChatComponent itemDetails = new ChatComponentText(itemStack.getDisplayName());

        if (tag != null) {
            String hoverNBT = itemStack.writeToNBT(new NBTTagCompound())
                .toString();
            if (hoverNBT.length() <= MAX_HOVER_NBT_LENGTH) {
                itemDetails.getChatStyle()
                    .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM, new ChatComponentText(hoverNBT)));
            }
        }

        return itemDetails;
    }

    private NBTTagCompound decodeItemNBT() {
        if (encodedNBT == null || NBTUtils.NO_NBT.equals(encodedNBT) || NBTUtils.NBT_DISABLED.equals(encodedNBT)) {
            return null;
        }

        try {
            return NBTUtils.decodeFromString(encodedNBT);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public String getLoggerName() {
        return TemporaEvents.INVENTORY;
    }
}
