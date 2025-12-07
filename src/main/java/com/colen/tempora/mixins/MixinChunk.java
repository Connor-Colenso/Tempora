package com.colen.tempora.mixins;

import com.colen.tempora.Tempora;
import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Chunk.class)
public abstract class MixinChunk {

    @Shadow public World worldObj;
    @Shadow public int xPosition;
    @Shadow public int zPosition;

    /**
     * Core block write entry point:
     * boolean func_150807_a(int localX, int y, int localZ, Block newBlock, int newMeta)
     */
    @Inject(
        method = "func_150807_a",
        at = @At("HEAD"),
        remap = false
    )
    private void tempora$blockWriteStart(int localX, int y, int localZ,
                                         Block newBlock, int newMeta,
                                         CallbackInfoReturnable<Boolean> cir) {

        int globalX = (this.xPosition << 4) + localX;
        int globalZ = (this.zPosition << 4) + localZ;

        Tempora.blockChangeLogger.onSetBlockHead(globalX, y, globalZ, this.worldObj);
    }

    @Inject(
        method = "func_150807_a",
        at = @At("RETURN"),
        remap = false
    )
    private void tempora$blockWriteEnd(int localX, int y, int localZ,
                                       Block newBlock, int newMeta,
                                       CallbackInfoReturnable<Boolean> cir) {

        int globalX = (this.xPosition << 4) + localX;
        int globalZ = (this.zPosition << 4) + localZ;

        Tempora.blockChangeLogger.onSetBlockReturn(globalX, y, globalZ, this.worldObj, cir);
    }
}
