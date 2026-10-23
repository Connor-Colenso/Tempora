package com.colen.tempora.mixins;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.util.IChatComponent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.colen.tempora.TemporaEvents;

// Players leaving, with why, while they are still on the player list.
@Mixin(NetHandlerPlayServer.class)
public abstract class MixinNetHandlerPlayServer {

    @Shadow
    public EntityPlayerMP playerEntity;

    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void tempora$logLeave(IChatComponent reason, CallbackInfo ci) {
        TemporaEvents.playerConnectionLogger.onDisconnect(playerEntity, reason);
    }
}
