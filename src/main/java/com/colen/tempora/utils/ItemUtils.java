package com.colen.tempora.utils;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class ItemUtils {

    public static String getNameOfItemStack(int id, int meta) {
        ItemStack itemStack = new ItemStack(Item.getItemById(id), 1, meta);
        return itemStack.getDisplayName();
    }
}
