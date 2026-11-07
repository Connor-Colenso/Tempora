package com.colen.tempora.mixins;

import static com.colen.tempora.Tempora.LOG;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.colen.tempora.TemporaEvents;

@Mixin(Chunk.class)
// Every Tempora call here is guarded, including against a LinkageError from another mod's code it calls (pick block, TE
// NBT), so the block write always goes ahead
public abstract class MixinChunk {

    @Shadow
    public World worldObj;
    @Shadow
    public int xPosition;
    @Shadow
    public int zPosition;

    // Main block write entry point
    // boolean func_150807_a(int localX, int y, int localZ, Block newBlock, int newMeta)
    @Inject(method = "func_150807_a", at = @At("HEAD"), remap = false)
    private void tempora$blockWriteStart(int localX, int y, int localZ, Block newBlock, int newMeta,
        CallbackInfoReturnable<Boolean> cir) {
        if (worldObj.isRemote) return;

        try {
            int globalX = (this.xPosition << 4) + localX;
            int globalZ = (this.zPosition << 4) + localZ;

            TemporaEvents.blockChangeLogger.onSetBlockHead(globalX, y, globalZ, this.worldObj, newBlock);
        } catch (RuntimeException | LinkageError e) {
            LOG.error("Tempora block-change logging failed on block write start; block write continues.", e);
        }
    }

    @Inject(method = "func_150807_a", at = @At("RETURN"), remap = false)
    private void tempora$blockWriteEnd(int localX, int y, int localZ, Block newBlock, int newMeta,
        CallbackInfoReturnable<Boolean> cir) {
        if (worldObj.isRemote) return;

        try {
            int globalX = (this.xPosition << 4) + localX;
            int globalZ = (this.zPosition << 4) + localZ;

            TemporaEvents.blockChangeLogger.onSetBlockReturn(globalX, y, globalZ, this.worldObj, cir);
        } catch (RuntimeException | LinkageError e) {
            LOG.error("Tempora block-change logging failed on block write end; block write continues.", e);
        }
    }

    // Meta only write entry point
    // boolean setBlockMetadata(int localX, int y, int localZ, int newMeta)
    // Unlike func_150807_a this has an MCP name so it has to be remapped, it's func_76589_b in production
    @Inject(method = "setBlockMetadata", at = @At("HEAD"))
    private void tempora$metaWriteStart(int localX, int y, int localZ, int newMeta,
        CallbackInfoReturnable<Boolean> cir) {
        if (worldObj.isRemote) return;

        try {
            int globalX = (this.xPosition << 4) + localX;
            int globalZ = (this.zPosition << 4) + localZ;

            TemporaEvents.blockChangeLogger.onSetBlockHead(globalX, y, globalZ, this.worldObj, null);
        } catch (RuntimeException | LinkageError e) {
            LOG.error("Tempora block-change logging failed on metadata write start; write continues.", e);
        }
    }

    @Inject(method = "setBlockMetadata", at = @At("RETURN"))
    private void tempora$metaWriteEnd(int localX, int y, int localZ, int newMeta, CallbackInfoReturnable<Boolean> cir) {
        if (worldObj.isRemote) return;

        try {
            int globalX = (this.xPosition << 4) + localX;
            int globalZ = (this.zPosition << 4) + localZ;

            TemporaEvents.blockChangeLogger.onSetBlockReturn(globalX, y, globalZ, this.worldObj, cir);
        } catch (RuntimeException | LinkageError e) {
            LOG.error("Tempora block-change logging failed on metadata write end; write continues.", e);
        }
    }

}
