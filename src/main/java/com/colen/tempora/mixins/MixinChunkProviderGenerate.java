package com.colen.tempora.mixins;

import com.colen.tempora.utils.WorldGenPhaseTracker;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderGenerate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkProviderGenerate.class)
public class MixinChunkProviderGenerate {

    @Inject(method = "populate", at = @At("HEAD"))
    private void tempora$startPopulate(IChunkProvider provider, int chunkX, int chunkZ, CallbackInfo ci) {
        WorldGenPhaseTracker.IN_WORLD_GEN = true;
    }

    @Inject(method = "populate", at = @At("RETURN"))
    private void tempora$endPopulate(IChunkProvider provider, int chunkX, int chunkZ, CallbackInfo ci) {
        WorldGenPhaseTracker.IN_WORLD_GEN = false;
    }
}
