package com.colen.tempora.utils;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class ItemUtils {

    public static String getNameOfItemStack(int id, int meta) {
        Item item = Item.getItemById(id);
        if (item == null) {
            String errorItemName = "[ERROR UNKNOWN ITEM (" + id + ":" + meta + ")]";
            System.err.println(errorItemName);
            return errorItemName;
        }
        ItemStack itemStack = new ItemStack(item, 1, meta);
        return itemStack.getDisplayName();
    }
}
