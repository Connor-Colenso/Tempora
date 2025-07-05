package com.colen.tempora.mixins;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.colen.tempora.logging.loggers.inventory.InventoryLogger;

@Mixin(Container.class)
public abstract class MixinContainer {

    @Shadow
    public List<Slot> inventorySlots;

    // holds a shallow copy of all stacks before the click
    private Map<Integer, ItemStack> snapshot;

    /* ─────── capture BEFORE vanilla runs ─────── */
    @Inject(method = "slotClick", at = @At("HEAD"))
    private void pre(int slot, int button, int mode, EntityPlayer player, CallbackInfoReturnable<ItemStack> cir) {
        if (player.worldObj.isRemote) return; // ignore client side
        snapshot = new HashMap<>();
        for (Slot s : inventorySlots) {
            snapshot.put(
                s.slotNumber,
                s.getStack() == null ? null
                    : s.getStack()
                        .copy());
        }
    }

    /* ─────── diff AFTER vanilla is done ─────── */
    @Inject(method = "slotClick", at = @At("RETURN"))
    private void post(int slot, int button, int mode, EntityPlayer player, CallbackInfoReturnable<ItemStack> cir) {
        if (player.worldObj.isRemote) return;

        Container container = (Container) (Object) this;

        InventoryLogger.preLogLogic(player, container, inventorySlots, snapshot);
    }
}
