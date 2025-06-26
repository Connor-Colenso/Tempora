package com.colen.tempora.utils;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

public class BlockUtils {

    public static IChatComponent getUnlocalisedChatComponent(int blockID, int metadata) {
        if (blockID == 0) return new ChatComponentText("Air");

        Block block = Block.getBlockById(blockID);
        if (block != null) {
            // Try to get the ItemBlock for this block
            ItemStack stack = new ItemStack(block, 1, metadata);

            String unlocalized = stack.getUnlocalizedName(); // usually something like "tile.wool.colored.white" or "tile.grass.default"

            // Normalize the unlocalized name to avoid ".default"
            if (unlocalized.endsWith(".default")) {
                unlocalized = unlocalized.substring(0, unlocalized.length() - ".default".length());
            }

            // Add ".name" suffix since lang keys usually end like "tile.wool.colored.white.name"
            String langKey = unlocalized + ".name";

            return new ChatComponentTranslation(langKey);
        } else {
            return new ChatComponentText("[Unknown Block]");
        }
    }

}
