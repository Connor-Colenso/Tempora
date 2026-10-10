package com.colen.tempora.mixins.accessors;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryLargeChest;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// The two chests behind a double chest, so its slots can be logged against the half they belong to.
@Mixin(InventoryLargeChest.class)
public interface InventoryLargeChestAccessor {

    @Accessor("upperChest")
    IInventory tempora$getUpperChest();

    @Accessor("lowerChest")
    IInventory tempora$getLowerChest();
}
