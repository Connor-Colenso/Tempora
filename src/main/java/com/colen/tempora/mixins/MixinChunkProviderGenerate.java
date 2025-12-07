package com.colen.tempora.mixins;

import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderGenerate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.colen.tempora.utils.WorldGenPhaseTracker;

@Mixin(ChunkProviderGenerate.class)
public class MixinChunkProviderGenerate {

    @Inject(method = "populate", at = @At("HEAD"))
    private void tempora$startPopulate(IChunkProvider provider, int chunkX, int chunkZ, CallbackInfo ci) {
        WorldGenPhaseTracker.enter(WorldGenPhaseTracker.Phase.BASE_TERRAIN);
    }

    @Inject(method = "populate", at = @At("TAIL"))
    private void tempora$endPopulate(IChunkProvider provider, int chunkX, int chunkZ, CallbackInfo ci) {
        WorldGenPhaseTracker.exit();
    }
}
