package com.colen.tempora.mixins;

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

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;

@Mixin(World.class)
public class MixinWorld {

    @Shadow
    public WorldProvider provider;

    @Shadow
    protected IChunkProvider chunkProvider;

    @Inject(method = "setBlock", at = @At("RETURN"))
    private void onSetBlockReturn(int x, int y, int z, Block blockIn, int metadataIn, int flags,
        CallbackInfoReturnable<Boolean> cir) {
        if (Tempora.blockChangeLogger == null) return;

        if (chunkProvider instanceof ChunkProviderGenerate) {
            // Don't log during world generation
            return;
        }

        ModContainer modContainer = Loader.instance()
            .activeModContainer();
        String modID;
        if (modContainer == null) {
            modID = "[NO MOD]";
        } else {
            modID = modContainer.getModId();
        }

        // 2 for SEND_TO_CLIENTS.
        if (cir.getReturnValue() && (flags & 2) != 0) {
            Tempora.blockChangeLogger.recordSetBlock(x, y, z, blockIn, metadataIn, provider.dimensionId, modID);
        }
    }
}
