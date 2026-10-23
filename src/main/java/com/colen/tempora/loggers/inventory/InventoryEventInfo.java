package com.colen.tempora.loggers.inventory;

import static com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber;

import net.minecraft.event.HoverEvent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
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

public class InventoryEventInfo extends PositionalEventInfo {

    // Chat packets cannot carry arbitrarily large hover text, so huge NBT (full backpacks, AE2 cells) is not shown.
    private static final int MAX_HOVER_NBT_LENGTH = 8192;

    @Column(constraints = "NOT NULL")
    public String containerName = "";

    @Column(constraints = "NOT NULL")
    public int direction;

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

    // encodedNBT is deliberately not sent: the client only uses these events to draw boxes, and item NBT can be far
    // larger than a packet string allows.
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
            IChatComponent invalid = new ChatComponentTranslation("tempora.event.inventory.invalid", direction);
            invalid.getChatStyle()
                .setColor(EnumChatFormatting.RED);
            return invalid;
        }

        // Green for items put in, red for items taken out.
        boolean added = dir.isAddition();
        IChatComponent count = EventChat
            .text(formatNumber(amount), added ? EnumChatFormatting.GREEN : EnumChatFormatting.RED);

        // Text, as the container's name was already worked out on the server, and a renamed chest can hold a %.
        IChatComponent container = EventChat.text(containerName, EnumChatFormatting.WHITE);

        // Containers carried by a player (backpacks, wireless terminals) are logged at the player, which is still the
        // block worth teleporting to.
        return EventChat.line(
            added ? "tempora.event.inventory.added" : "tempora.event.inventory.removed",
            EventChat.time(timestamp),
            EventChat.player(playerUUID),
            count,
            itemChatComponent(),
            container,
            EventChat.where(this, CommandUtils.TeleportType.BLOCK));
    }

    // The item's name, with its tooltip on hover when the NBT was logged.
    private IChatComponent itemChatComponent() {
        Item item = Item.getItemById(itemID);
        if (item == null) {
            return EventChat.text("[unknown item " + itemID + ":" + itemMetadata + "]", EnumChatFormatting.WHITE);
        }

        ItemStack itemStack = new ItemStack(item, 1, itemMetadata);
        NBTTagCompound tag = decodeItemNBT();
        if (tag != null) itemStack.setTagCompound(tag);

        // Already localised here, like vanilla's own item mentions.
        IChatComponent itemDetails = EventChat.text(itemStack.getDisplayName(), EnumChatFormatting.WHITE);

        // The item's own tooltip on hover, NBT and all, unless the NBT is too big to send.
        String hoverNBT = itemStack.writeToNBT(new NBTTagCompound())
            .toString();
        if (hoverNBT.length() <= MAX_HOVER_NBT_LENGTH) {
            itemDetails.getChatStyle()
                .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM, new ChatComponentText(hoverNBT)));
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
