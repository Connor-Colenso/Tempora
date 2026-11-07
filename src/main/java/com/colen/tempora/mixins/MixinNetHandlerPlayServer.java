package com.colen.tempora.mixins;

import static com.colen.tempora.Tempora.LOG;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.util.IChatComponent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.colen.tempora.TemporaEvents;

// Players leaving and why, while they're still on the player list
@Mixin(NetHandlerPlayServer.class)
public abstract class MixinNetHandlerPlayServer {

    @Shadow
    public EntityPlayerMP playerEntity;

    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void tempora$logLeave(IChatComponent reason, CallbackInfo ci) {
        // An exception here would skip the rest of vanilla's disconnect and leave the player on the player list
        try {
            TemporaEvents.playerConnectionLogger.onDisconnect(playerEntity, reason);
        } catch (RuntimeException | LinkageError e) {
            LOG.error("Tempora could not log a player leaving; the disconnect itself is unaffected.", e);
        }
    }
}
