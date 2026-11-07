package com.colen.tempora.utils;

import static com.colen.tempora.Tempora.LOG;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFlowerPot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public class BlockUtils {

    // Blocks already warned about, see getPickBlockSafe
    private static final Set<Block> BROKEN_PICK_BLOCKS = ConcurrentHashMap.newKeySet();

    public static ItemStack getPickBlockSafe(Block block, World world, int x, int y, int z) {
        // Item.getItemFromBlock is safe server side
        Item item = Item.getItemFromBlock(block);

        if (item == null) {
            return null;
        }

        // Block to take the meta from. TODO review the special case here
        Block theBlock = item instanceof ItemBlock && !(block instanceof BlockFlowerPot) ? Block.getBlockFromItem(item)
            : block;

        // ItemStack with the right damage value. Null if another mod's getDamageValue throws so callers fall back to
        // the block itself, logging must never stop a block being broken or changed
        try {
            return new ItemStack(item, 1, theBlock.getDamageValue(world, x, y, z));
        } catch (RuntimeException | LinkageError e) {
            if (BROKEN_PICK_BLOCKS.add(theBlock)) {
                LOG.warn(
                    "{} at {}, {}, {} in dimension {} threw while working out its item, so Tempora stores the block "
                        + "rather than its item. Logged once per block.",
                    Block.blockRegistry.getNameForObject(theBlock),
                    x,
                    y,
                    z,
                    world.provider.dimensionId,
                    e);
            }
            return null;
        }
    }

}
