package com.colen.tempora.mixins;

import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureStart;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.colen.tempora.utils.WorldGenPhaseTracker;

@Mixin(StructureStart.class)
public abstract class MixinStructureStart {

    /**
     * Wrap the entire generateStructure call in a WorldGenPhaseTracker phase.
     */
    @Inject(
        method = "generateStructure(Lnet/minecraft/world/World;Ljava/util/Random;Lnet/minecraft/world/gen/structure/StructureBoundingBox;)V",
        at = @At("HEAD"))
    private void onGenerateStructureEnter(World world, java.util.Random random, StructureBoundingBox bounds,
        CallbackInfo ci) {
        // Enter the phase before generation
        WorldGenPhaseTracker.enter(WorldGenPhaseTracker.Phase.MOD_FEATURES);
    }

    @Inject(
        method = "generateStructure(Lnet/minecraft/world/World;Ljava/util/Random;Lnet/minecraft/world/gen/structure/StructureBoundingBox;)V",
        at = @At("RETURN"))
    private void onGenerateStructureExit(World world, java.util.Random random, StructureBoundingBox bounds,
        CallbackInfo ci) {
        // Exit the phase after generation
        WorldGenPhaseTracker.exit();
    }
}
