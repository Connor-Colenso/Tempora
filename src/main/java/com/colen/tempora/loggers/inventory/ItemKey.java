package com.colen.tempora.loggers.inventory;

import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

// What makes two stacks the same item for inventory logging, item, damage/meta and NBT. Same rules vanilla uses for
// merging, so anything that would swap instead of merge counts as a different item. Empty NBT compound counts as no NBT
public final class ItemKey {

    public final Item item;
    public final int meta;
    // Null if no NBT. Never mutate it, the hash is cached
    public final @Nullable NBTTagCompound tag;
    private final int hash;

    private ItemKey(Item item, int meta, @Nullable NBTTagCompound tag) {
        this.item = item;
        this.meta = meta;
        this.tag = tag;
        this.hash = 31 * (31 * System.identityHashCode(item) + meta) + (tag == null ? 0 : tag.hashCode());
    }

    // Key for a stack that might keep changing after this, e.g. a before snapshot. Copies the NBT. Null for an empty
    // slot or an item that no longer exists
    public static @Nullable ItemKey snapshotOf(@Nullable ItemStack stack) {
        return of(stack, true);
    }

    // Key for a stack that's read once and not kept. Shares the NBT with the stack so don't hold onto the key once the
    // stack can change. Null for an empty slot or an item that no longer exists
    public static @Nullable ItemKey of(@Nullable ItemStack stack) {
        return of(stack, false);
    }

    private static @Nullable ItemKey of(@Nullable ItemStack stack, boolean copyTag) {
        if (stack == null || stack.getItem() == null || stack.stackSize <= 0) return null;

        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null && tag.hasNoTags()) tag = null;
        if (tag != null && copyTag) tag = (NBTTagCompound) tag.copy();

        return new ItemKey(stack.getItem(), stack.getItemDamage(), tag);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemKey other)) return false;
        return hash == other.hash && item == other.item && meta == other.meta && Objects.equals(tag, other.tag);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return Item.itemRegistry.getNameForObject(item) + ":" + meta + (tag == null ? "" : tag.toString());
    }
}
