package com.colen.tempora.utils;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFlowerPot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public class BlockUtils {

    public static ItemStack getPickBlockSafe(Block block, World world, int x, int y, int z) {
        // Use Item.getItemFromBlock(this) directly to get the item (safe server-side)
        Item item = Item.getItemFromBlock(block);

        if (item == null) {
            return null;
        }

        // Determine block for metadata. todo review special logic here.
        Block theBlock = item instanceof ItemBlock && !(block instanceof BlockFlowerPot) ? Block.getBlockFromItem(item)
            : block;

        // Return ItemStack with a correct damage value
        return new ItemStack(item, 1, theBlock.getDamageValue(world, x, y, z));
    }

}
