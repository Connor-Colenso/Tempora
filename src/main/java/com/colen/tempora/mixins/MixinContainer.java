package com.colen.tempora.mixins;

import com.colen.tempora.Tempora;
import com.colen.tempora.logging.loggers.player_interact_with_inventory.PlayerInteractWithInventoryLogger;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Container.class)
public class MixinContainer {

    @Inject(method = "slotClick", at = @At("HEAD"))
    private void onSlotClick(int slotId, int button, int mode, EntityPlayer player, CallbackInfoReturnable<ItemStack> cir) {
        if (player.worldObj.isRemote) return;
        if (slotId < 0) return; // -999 = click outside inventory

        Container container = (Container)(Object)this;

        if (slotId >= container.inventorySlots.size()) return;

        Slot slot = container.getSlot(slotId);
        if (slot == null) return;

        ItemStack stack = slot.getStack();
        if (stack == null || stack.stackSize <= 0) return;

        boolean fromPlayerInventory = (slot.inventory == player.inventory);
        PlayerInteractWithInventoryLogger.Direction direction = fromPlayerInventory ? PlayerInteractWithInventoryLogger.Direction.FromPlayer : PlayerInteractWithInventoryLogger.Direction.ToPlayer;

        if (slot.inventory instanceof TileEntity tileEntity) {
            Tempora.playerInteractWithInventoryLogger.playerInteractedWithInventory(player, container, stack, direction, tileEntity);
        } else {
            Tempora.playerInteractWithInventoryLogger.playerInteractedWithInventory(player, container, stack, direction, null);
        }
    }
}
