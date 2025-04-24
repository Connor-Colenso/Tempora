package com.colen.tempora.mixins;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.play.client.C0EPacketClickWindow;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.colen.tempora.Tempora;
import com.colen.tempora.TemporaUtils;

@Mixin(NetHandlerPlayServer.class)
public abstract class MixinNetHandlerPlayServer {

    @Shadow
    public EntityPlayerMP playerEntity;

    @Inject(method = "processClickWindow", at = @At("HEAD"))
    private void onProcessClickWindow(C0EPacketClickWindow packetIn, CallbackInfo ci) {
        if (TemporaUtils.isServerSide() && TemporaUtils.shouldTemporaRun()) {
//            Tempora.playerInteractWithInventoryLogger.playerInteractedWithInventory(playerEntity, packetIn);
        }
    }
}
