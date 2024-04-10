package com.myname.mymodid.Utils;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class BlockUtils {
    private static final Map<String, String> nameCache = new ConcurrentHashMap<>();

    /**
     * Get the localized name of a block from its ID and metadata, using caching to optimize.
     * @param blockId the ID of the block
     * @param meta the metadata of the block
     * @return the localized name of the block
     */
    public static String getLocalizedName(int blockId, int meta) {
        String key = blockId + ":" + meta;  // Create a unique key for the blockId and metadata combination.

        // Return cached name if present.
        if (nameCache.containsKey(key)) {
            return nameCache.get(key);
        }

        Block block = Block.getBlockById(blockId);
        if (block == null) {
            // Log an error or warning if the block is not found.
            return "Unknown Block"; // Consider using a logging framework to log this situation.
        }

        ItemStack stack = new ItemStack(block, 1, meta);
        String localizedName = stack.getDisplayName();

        // Cache and return the localized name.
        nameCache.put(key, localizedName);

        return localizedName;
    }
}
