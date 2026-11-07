package com.colen.tempora.mixins;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.world.NextTickListEntry;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.colen.tempora.utils.WorldGenPhaseTracker;
import com.colen.tempora.utils.WorldGenTicks;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

@Mixin(WorldServer.class)
public abstract class MixinWorldServer {

    // Bonus chest gets generated next to spawn on world creation, outside chunk population
    @WrapMethod(method = "createBonusChest")
    private void tempora$bonusChestIsWorldGen(Operation<Void> original) {
        WorldGenPhaseTracker.enter();
        try {
            original.call();
        } finally {
            WorldGenPhaseTracker.exit();
        }
    }

    // Scheduled block updates world gen left behind, see WorldGenTicks

    @ModifyExpressionValue(
        method = "scheduleBlockUpdateWithPriority",
        at = @At(value = "NEW", target = "net/minecraft/world/NextTickListEntry"))
    private NextTickListEntry tempora$markScheduledTick(NextTickListEntry entry) {
        ((WorldGenTicks.Marked) entry)
            .tempora$setWorldGenUntil(WorldGenTicks.markForNewTick((World) (Object) this, entry));
        return entry;
    }

    @WrapOperation(
        method = "tickUpdates",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/block/Block;updateTick(Lnet/minecraft/world/World;IIILjava/util/Random;)V"))
    private void tempora$runWorldGenTick(Block block, World world, int x, int y, int z, Random random,
        Operation<Void> original, @Local NextTickListEntry entry) {
        if (WorldGenTicks.isWorldGen(entry, world)) {
            WorldGenTicks.runAsWorldGen(entry, world, () -> original.call(block, world, x, y, z, random));
        } else {
            original.call(block, world, x, y, z, random);
        }
    }
}
