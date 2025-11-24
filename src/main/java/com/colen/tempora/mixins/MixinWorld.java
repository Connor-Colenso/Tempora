package com.colen.tempora.mixins;

import com.colen.tempora.TemporaUtils;
import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderGenerate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.colen.tempora.Tempora;

// Todo only allow on server side.
@Mixin(World.class)
public class MixinWorld {

    @Shadow
    public WorldProvider provider;
    @Shadow
    protected IChunkProvider chunkProvider;

    @Inject(method = "setBlock", at = @At("HEAD"))
    private void onSetBlockHead(int x, int y, int z, Block blockIn, int metadataIn, int flags,
        CallbackInfoReturnable<Boolean> cir) {

        if (Tempora.blockChangeLogger == null) return;
        if (chunkProvider instanceof ChunkProviderGenerate) return; // worldgen
        if (TemporaUtils.isClientSide()) return;

        Tempora.blockChangeLogger.onSetBlockHead(x, y, z, blockIn, provider);
    }

    @Inject(method = "setBlock", at = @At("RETURN"))
    private void onSetBlockReturn(int x, int y, int z, Block blockIn, int metadataIn, int flags,
        CallbackInfoReturnable<Boolean> cir) {

        if (Tempora.blockChangeLogger == null) return;
        if (chunkProvider instanceof ChunkProviderGenerate) return; // worldgen
        if (TemporaUtils.isClientSide()) return;

        Tempora.blockChangeLogger.onSetBlockReturn(x, y, z, blockIn, flags, provider, cir);

    }
}
