package com.colen.tempora.mixins;

import static com.colen.tempora.Tempora.LOG;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.loggers.item_pickup_drop.ItemPickupDropLogger;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

// Whatever a player touching an item on the ground takes from it, including what mods' pickup handlers (backpacks)
// take, is a pickup. See ItemPickupDropLogger.logPickup. Called every tick for each item a player stands on, so only
// the stack size is noted until something is taken.
@Mixin(EntityItem.class)
public abstract class MixinEntityItem {

    @WrapMethod(method = "onCollideWithPlayer")
    private void tempora$logPickup(EntityPlayer player, Operation<Void> original) {
        EntityItem self = (EntityItem) (Object) this;
        ItemPickupDropLogger logger = TemporaEvents.itemPickupDropLogger;
        if (self.worldObj.isRemote || self.isDead || !logger.isActive()) {
            original.call(player);
            return;
        }

        ItemStack stack = self.getEntityItem();
        int amountBefore = stack == null ? 0 : stack.stackSize;

        original.call(player);

        if (amountBefore <= 0) return;
        // Logging must never crash the entity's tick, e.g. on an item whose NBT can't be encoded.
        try {
            logger.logPickup(player, self, stack, amountBefore);
        } catch (RuntimeException | LinkageError e) {
            LOG.error("Tempora could not log an item pickup; the pickup itself is unaffected.", e);
        }
    }
}
