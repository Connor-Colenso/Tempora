package com.colen.tempora.mixins;

import static com.colen.tempora.utils.BlockUtils.getPickBlockSafe;
import static com.colen.tempora.utils.nbt.NBTUtils.getEncodedTileEntityNBT;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
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
import com.colen.tempora.loggers.block_change.BlockChangeLogger;
import com.colen.tempora.loggers.block_change.SetBlockEventInfo;

import cpw.mods.fml.common.FMLLog;

import java.util.*;

// Todo only allow on server side.
@Mixin(World.class)
public class MixinWorld {

    @Shadow
    public WorldProvider provider;
    @Shadow
    protected IChunkProvider chunkProvider;

    // It is possible for SetBlock to call other SetBlocks, hence this is required, to untangle nested calls.
    private static Deque<SetBlockEventInfo> eventInfoQueue = new ArrayDeque<>();

    private static long worldTick;

    @Inject(method = "setBlock", at = @At("HEAD"))
    private void onSetBlockHead(int x, int y, int z, Block blockIn, int metadataIn, int flags,
        CallbackInfoReturnable<Boolean> cir) {

        // Pre checks.
//        MinecraftServer server = MinecraftServer.getServer();
//        boolean isDedicated = server != null && server.isDedicatedServer();
//        if (!isDedicated) return;
        if (Tempora.blockChangeLogger == null) return;
        if (chunkProvider instanceof ChunkProviderGenerate) return; // worldgen

        eventInfoQueue.add(new SetBlockEventInfo());

        SetBlockEventInfo currentEventInfo = eventInfoQueue.peek();

        currentEventInfo.beforeBlockID = Block.getIdFromBlock(provider.worldObj.getBlock(x, y, z));
        currentEventInfo.beforeMeta = provider.worldObj.getBlockMetadata(x, y, z);

        // Pick block info.
        ItemStack pickStack = getPickBlockSafe(blockIn, provider.worldObj, x, y, z);
        if (pickStack != null && pickStack.getItem() != null) {
            currentEventInfo.beforePickBlockID = Item.getIdFromItem(pickStack.getItem());
            currentEventInfo.beforePickBlockMeta = pickStack.getItemDamage();
        } else {
            // Fallback to the raw place‑block data
            currentEventInfo.beforePickBlockID = currentEventInfo.beforeBlockID;
            currentEventInfo.beforePickBlockMeta = currentEventInfo.beforeMeta;
        }

        // Log NBT.
        currentEventInfo.beforeEncodedNBT = getEncodedTileEntityNBT(
            provider.worldObj,
            x,
            y,
            z,
            BlockChangeLogger.isLogNBTEnabled());


        currentEventInfo.worldTick = provider.worldObj.getTotalWorldTime();
    }

    @Inject(method = "setBlock", at = @At("RETURN"))
    private void onSetBlockReturn(int x, int y, int z, Block blockIn, int metadataIn, int flags,
        CallbackInfoReturnable<Boolean> cir) {

        // Pre checks.
//        MinecraftServer server = MinecraftServer.getServer();
//        boolean isDedicated = server != null && server.isDedicatedServer();
//        if (!isDedicated) return;
        if (Tempora.blockChangeLogger == null) return;
        if (chunkProvider instanceof ChunkProviderGenerate) return; // worldgen

        // Only log successful placements
        if (!cir.getReturnValue()) return;

        worldTick = provider.worldObj.getTotalWorldTime();

        SetBlockEventInfo currentEventInfo = eventInfoQueue.poll();
        if (currentEventInfo == null) {
            // todo critical
            FMLLog.severe("CRITICAL");
        }

        currentEventInfo.afterBlockID = Block.getIdFromBlock(provider.worldObj.getBlock(x, y, z));
        currentEventInfo.afterMeta = provider.worldObj.getBlockMetadata(x, y, z);

        // Pick block info.
        ItemStack pickStack = getPickBlockSafe(blockIn, provider.worldObj, x, y, z);
        if (pickStack != null && pickStack.getItem() != null) {
            currentEventInfo.afterPickBlockID = Item.getIdFromItem(pickStack.getItem());
            currentEventInfo.afterPickBlockMeta = pickStack.getItemDamage();
        } else {
            // Fallback to the raw place‑block data
            currentEventInfo.afterPickBlockID = currentEventInfo.afterBlockID;
            currentEventInfo.afterPickBlockMeta = currentEventInfo.afterMeta;
        }

        // Log NBT.
        currentEventInfo.afterEncodedNBT = getEncodedTileEntityNBT(
            provider.worldObj,
            x,
            y,
            z,
            BlockChangeLogger.isLogNBTEnabled());

        // Ignore no-ops (same block and metadata)
        if (currentEventInfo.isNoOp()) return;

        // Todo more safety checks like compare x y z.
        // Todo get mod ID.
        if (worldTick == provider.worldObj.getTotalWorldTime()) {
            Tempora.blockChangeLogger.recordSetBlock(x, y, z, currentEventInfo, provider, "mod");
        } else {
            FMLLog.severe(
                "[TEMPORA BLOCK LOGGER CRITICAL ERROR]\n" + "World tick mismatch detected during setBlock logging!\n"
                    + "Expected tick: %d | Actual tick: %d\n"
                    + "Dim ID: %d | Pos: (%d,%d,%d)\n"
                    + "Before: %s:%d | After: %s:%d | Flags: %d\n"
                    + "This should NEVER occur.\n"
                    + "Please report this with full logs immediately.",
                worldTick,
                provider.worldObj.getTotalWorldTime(),
                provider.dimensionId,
                x,
                y,
                z,
                currentEventInfo.beforeBlockID,
                currentEventInfo.beforeMeta,
                currentEventInfo.afterBlockID,
                currentEventInfo.afterMeta,
                flags);
        }
    }
}
