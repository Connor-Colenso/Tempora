package com.colen.tempora.mixins;

import com.colen.tempora.Tempora;
import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public class MixinWorld {

    @Shadow
    public WorldProvider provider;

    @Inject(method = "setBlock", at = @At("RETURN"))
    private void onSetBlockReturn(int x, int y, int z, Block blockIn, int metadataIn, int flags, CallbackInfoReturnable<Boolean> cir) {
        // 1 for NOTIFY_NEIGHBORS and 2 for SEND_TO_CLIENTS.
        if (cir.getReturnValue() && (flags & 2) != 0) {
            Tempora.blockChangeLogger.recordSetBlock(x, y, z, blockIn, metadataIn, provider.dimensionId);
        }
    }
}
