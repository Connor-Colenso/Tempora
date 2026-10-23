package com.colen.tempora.chat;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.StatCollector;

import com.gtnewhorizon.gtnhlib.chat.AbstractChatComponentCustom;
import com.gtnewhorizon.gtnhlib.chat.customcomponents.AbstractChatComponentBuffer;

/**
 * Display name of a block (or item) ID and meta, resolved on the client. Many modded items, such as GregTech machines,
 * name themselves through getItemStackDisplayName rather than a translation key, so a translation key alone can't
 * always be localised.
 */
public class ChatComponentBlockName extends AbstractChatComponentBuffer<ChatComponentBlockName> {

    private int id;
    private int meta;

    public ChatComponentBlockName(int id, int meta) {
        this.id = id;
        this.meta = meta;
    }

    public ChatComponentBlockName() {}

    // Client side call.
    @Override
    public String getUnformattedTextForChat() {
        // 0 is hard‑wired vanilla Air
        if (id == 0) return StatCollector.translateToLocal("tempora.block.air");

        Item item = Item.getItemById(id);
        if (item == null) {
            Block block = Block.getBlockById(id);
            if (block != null) item = Item.getItemFromBlock(block);
        }

        if (item == null) return "[unknown ID " + id + "]";

        try {
            return new ItemStack(item, 1, meta).getDisplayName();
        } catch (Exception e) {
            return "[" + id + ":" + meta + "]";
        }
    }

    @Override
    public void encode(PacketBuffer buf) {
        buf.writeInt(id);
        buf.writeInt(meta);
    }

    @Override
    public void decode(PacketBuffer buf) {
        id = buf.readInt();
        meta = buf.readInt();
    }

    @Override
    public String getID() {
        return "ChatComponentBlockName";
    }

    @Override
    protected AbstractChatComponentCustom copySelf() {
        return new ChatComponentBlockName(id, meta);
    }
}
