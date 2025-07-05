package com.colen.tempora.utils;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

public class BlockUtils {

    public static IChatComponent getUnlocalisedChatComponent(int blockId, int meta) {
        // 0 is hard‑wired vanilla Air
        if (blockId == 0) return new ChatComponentTranslation("tile.air.name");

        Item item = Item.getItemById(blockId);
        if (item == null) {
            Block blk = Block.getBlockById(blockId);
            if (blk != null) item = Item.getItemFromBlock(blk);
        }

        if (item == null) {
            return new ChatComponentText("[unknown ID " + blockId + "]");
        }

        ItemStack stack = new ItemStack(item, 1, meta);

        return new ChatComponentTranslation(stack.getDisplayName());
    }

}
