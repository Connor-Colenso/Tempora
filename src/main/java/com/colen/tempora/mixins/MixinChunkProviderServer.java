package com.colen.tempora.mixins;

import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.colen.tempora.utils.WorldGenPhaseTracker;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

// Every world's chunks get generated through its ChunkProviderServer whatever the dimension or generator, so world gen
// is marked here instead of in each generator
@Mixin(ChunkProviderServer.class)
public abstract class MixinChunkProviderServer {

    // Population, the dim's own generator (ores, plants, lakes, structures) then every mod's IWorldGenerator through
    // GameRegistry.generateWorld, GT ore veins included. Chunks it loads or generates along the way count too
    @WrapMethod(method = "populate")
    private void tempora$populateIsWorldGen(IChunkProvider provider, int chunkX, int chunkZ, Operation<Void> original) {
        WorldGenPhaseTracker.enter();
        try {
            original.call(provider, chunkX, chunkZ);
        } finally {
            WorldGenPhaseTracker.exit();
        }
    }

    // A new chunk's terrain. Vanilla fills the chunk arrays directly but a mod's generator might set blocks
    @WrapOperation(
        method = "originalLoadChunk",
        remap = false,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/chunk/IChunkProvider;provideChunk(II)Lnet/minecraft/world/chunk/Chunk;",
            remap = true))
    private Chunk tempora$terrainIsWorldGen(IChunkProvider generator, int chunkX, int chunkZ,
        Operation<Chunk> original) {
        WorldGenPhaseTracker.enter();
        try {
            return original.call(generator, chunkX, chunkZ);
        } finally {
            WorldGenPhaseTracker.exit();
        }
    }
}
