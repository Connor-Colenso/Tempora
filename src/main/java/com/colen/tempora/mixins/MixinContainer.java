package com.colen.tempora.mixins;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.colen.tempora.Tempora;
import com.colen.tempora.logging.loggers.player_interact_with_inventory.PlayerInteractWithInventoryLogger;

import static com.colen.tempora.logging.loggers.player_interact_with_inventory.PlayerInteractWithInventoryLogger.handleSlotClick;

@Mixin(Container.class)
public class MixinContainer {

    @Inject(method = "slotClick", at = @At("HEAD"))
    private void onSlotClick(int slotId, int button, int mode,
                             EntityPlayer player, CallbackInfoReturnable<ItemStack> cir) {
        // Cast once here so the helper can stay completely vanilla
        handleSlotClick((Container) (Object) this, slotId, player);
    }

}
