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
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import org.jetbrains.annotations.NotNull;

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

        // Use getUnlocalizedName() instead of getDisplayName(), append ".name" for a translation key
        String key = stack.getUnlocalizedName() + ".name";
        return new ChatComponentTranslation(key);
    }

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

    /**
     * Worldgen-style block set: writes directly into the chunk storage with
     * no block updates, no onBlockAdded, no neighbor notifications. Note that this DOES bypass logging as well.
     * <p>
     * Does NOT touch TileEntities. If you are changing a TE block, handle
     * TE creation/removal separately. THIS WILL CORRUPT WORLDS IF IGNORED!
     */
    public static boolean setBlockNoUpdate(World world, int x, int y, int z, @NotNull Block block, int meta) {

        // Vanilla hard bounds
        if (y < 0 || y >= world.getHeight()) {
            return false;
        }

        // Get chunk and local coordinates
        Chunk chunk = world.getChunkFromChunkCoords(x >> 4, z >> 4);
        int localX = x & 15;
        int localZ = z & 15;
        int storageIndex = y >> 4;
        int localY = y & 15;

        ExtendedBlockStorage[] storageArray = chunk.getBlockStorageArray();
        ExtendedBlockStorage storage = storageArray[storageIndex];

        if (storage == null) {
            storage = new ExtendedBlockStorage(storageIndex << 4, !world.provider.hasNoSky);
            storageArray[storageIndex] = storage;
        }

        // This updates blockRefCount / tickRefCount correctly
        storage.func_150818_a(localX, localY, localZ, block);
        storage.setExtBlockMetadata(localX, localY, localZ, meta);

        return true;
    }

}
