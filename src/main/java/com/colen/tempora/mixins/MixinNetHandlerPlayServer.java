package com.colen.tempora.mixins;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.play.client.C0EPacketClickWindow;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetHandlerPlayServer.class)
public class MixinNetHandlerPlayServer {

    @Shadow
    public EntityPlayerMP playerEntity;

    private Map<Integer, ItemStack> snapshot;

    @Inject(method = "processClickWindow", at = @At("HEAD"))
    private void onProcessClickWindowHead(C0EPacketClickWindow packetIn, CallbackInfo ci) {
        if (playerEntity.worldObj.isRemote) return; // ignore on the client side
        snapshot = new HashMap<>();
        for (Slot s : playerEntity.openContainer.inventorySlots) {
            snapshot.put(
                s.slotNumber,
                s.getStack() == null ? null
                    : s.getStack()
                        .copy());
        }
    }

    @Inject(method = "processClickWindow", at = @At("RETURN"))
    private void onProcessClickWindowReturn(C0EPacketClickWindow packetIn, CallbackInfo ci) {
        com.colen.tempora.loggers.inventory.InventoryLogger
            .preLogLogic(playerEntity, playerEntity.openContainer, playerEntity.openContainer.inventorySlots, snapshot);
    }
}
