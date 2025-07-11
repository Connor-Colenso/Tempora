package com.colen.tempora.utils;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFlowerPot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;

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

    public static ItemStack getPickBlockSafe(Block block, World world, int x, int y, int z) {
        // Use Item.getItemFromBlock(this) directly to get the item (safe server-side)
        Item item = Item.getItemFromBlock(block);

        if (item == null) {
            return null;
        }

        // Determine block for metadata, same as original logic
        Block theBlock = item instanceof ItemBlock && !(block instanceof BlockFlowerPot) ? Block.getBlockFromItem(item)
            : block;

        // Return ItemStack with correct damage value
        return new ItemStack(item, 1, theBlock.getDamageValue(world, x, y, z));
    }

}
