package com.colen.tempora.mixins;

import net.minecraft.entity.player.EntityPlayerMP;

import org.spongepowered.asm.mixin.Mixin;

import com.colen.tempora.loggers.inventory.InventoryTransaction;
import com.colen.tempora.loggers.inventory.InventoryTransactionTracker;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

// Containers closed by the server (walking away, opening another GUI) can still drop or return items
@Mixin(EntityPlayerMP.class)
public abstract class MixinEntityPlayerMP {

    @WrapMethod(method = "closeContainer")
    private void tempora$trackClose(Operation<Void> original) {
        InventoryTransaction transaction = InventoryTransactionTracker.beforeClose((EntityPlayerMP) (Object) this);
        try {
            original.call();
        } finally {
            InventoryTransactionTracker.afterClose(transaction);
        }
    }
}
