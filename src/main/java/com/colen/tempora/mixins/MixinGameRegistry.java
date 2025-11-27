package com.colen.tempora.mixins;

import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.colen.tempora.utils.WorldGenPhaseTracker;

import cpw.mods.fml.common.registry.GameRegistry;

@Mixin(value = GameRegistry.class, remap = false)
public class MixinGameRegistry {

    @Inject(method = "generateWorld", at = @At("HEAD"), remap = false)
    private static void tempora$startModGen(int chunkX, int chunkZ, World world, IChunkProvider chunkGenerator,
        IChunkProvider chunkProvider, CallbackInfo ci) {
        WorldGenPhaseTracker.IN_WORLD_GEN = true;
    }

    @Inject(method = "generateWorld", at = @At("RETURN"), remap = false)
    private static void tempora$endModGen(CallbackInfo ci) {
        WorldGenPhaseTracker.IN_WORLD_GEN = false;
    }
}
