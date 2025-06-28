package com.colen.tempora.utils;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import java.lang.reflect.Method;

public class BlockUtils {

    public static IChatComponent getUnlocalisedChatComponent(int blockID, int meta) {
        if (blockID == 0) return new ChatComponentText("Air");

        Block block = Block.getBlockById(blockID);
        if (block == null) return new ChatComponentText("[Unknown block " + blockID + ']');

        Item item = Item.getItemFromBlock(block);
        if (item == null) return new ChatComponentText(block.getLocalizedName());

        ItemStack stack = new ItemStack(item, 1, meta);
        String key = item.getUnlocalizedName(stack);

        if (key.endsWith(".default"))
            key = key.substring(0, key.length() - ".default".length());

        return new ChatComponentTranslation(key + ".name");
    }




}
