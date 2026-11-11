package com.colen.tempora.loggers.generic.column.names.lookups;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.colen.tempora.chat.ChatComponentBlockName;
import com.colen.tempora.loggers.generic.column.names.NamePart;
import com.colen.tempora.loggers.generic.column.names.NameRole;
import com.colen.tempora.loggers.generic.column.names.ShownNames;

/**
 * Items by {@code Item.getIdFromItem} and damage, on two int fields. For a block store its pick block, the item you get
 * middle clicking it, and not the block itself, f: goes by the names players see and those are item names. Blocks with
 * no item are named by their block ID with any meta
 */
public final class ItemNames implements NameRole {

    public static final String ID = "id";
    public static final String META = "meta";

    private static final List<NamePart> PARTS = Arrays.asList(NamePart.integer(ID), NamePart.integer(META));

    @Override
    public List<NamePart> parts() {
        return PARTS;
    }

    // Same names lookup results show (ChatComponentBlockName)
    @Override
    public void shownNames(ShownNames add) {
        List<ItemStack> stacks = new ArrayList<>();

        for (Object object : Item.itemRegistry) {
            Item item = (Item) object;
            int id = Item.getIdFromItem(item);

            // No subtypes means meta is damage (tools) or unused, one name for all of it
            if (!item.getHasSubtypes()) {
                add(add, id, NamePart.ANY, new ItemStack(item, 1, 0));
                continue;
            }

            stacks.clear();
            try {
                item.getSubItems(item, item.getCreativeTab(), stacks);
            } catch (Throwable t) {
                // Some mod's getSubItems blew up, named by meta 0 below like an item with none
                stacks.clear();
            }

            if (stacks.isEmpty()) add(add, id, NamePart.ANY, new ItemStack(item, 1, 0));
            for (ItemStack stack : stacks) {
                if (stack == null || stack.getItem() == null) continue;
                add(add, Item.getIdFromItem(stack.getItem()), stack.getItemDamage(), stack);
            }
        }

        // Blocks with no item, stored by their block ID. Air too (ID 0)
        for (Object object : Block.blockRegistry) {
            Block block = (Block) object;
            if (Item.getItemFromBlock(block) != null) continue;

            int id = Block.getIdFromBlock(block);
            try {
                String name = id == 0 ? StatCollector.translateToLocal("tempora.block.air")
                    : ChatComponentBlockName.nameWithoutItem(block);
                add.add(name, id, NamePart.ANY);
            } catch (Exception ignored) {
                // Can't be named, so can't be found by name either
            }
        }
    }

    private static void add(ShownNames add, int id, Object meta, ItemStack stack) {
        String name;
        try {
            name = EnumChatFormatting.getTextWithoutFormattingCodes(stack.getDisplayName());
        } catch (Exception e) {
            return;
        }
        if (name != null && !name.isEmpty()) add.add(name, id, meta);
    }
}
