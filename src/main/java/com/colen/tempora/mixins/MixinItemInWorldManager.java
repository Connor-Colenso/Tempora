package com.colen.tempora.mixins;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.colen.tempora.loggers.inventory.ContainerHosts;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

// A container opened while a block handles being right clicked belongs to that block. Containers opened by the held
// item instead (onItemUse etc.) are left to InventoryTransactionTracker.
@Mixin(ItemInWorldManager.class)
public abstract class MixinItemInWorldManager {

    @Shadow
    public EntityPlayerMP thisPlayerMP;

    @WrapOperation(
        method = "activateBlockOrUseItem",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/block/Block;onBlockActivated(Lnet/minecraft/world/World;IIILnet/minecraft/entity/player/EntityPlayer;IFFF)Z"))
    private boolean tempora$recordBlockContainer(Block block, World world, int x, int y, int z, EntityPlayer player,
        int side, float hitX, float hitY, float hitZ, Operation<Boolean> original) {
        Container containerBefore = thisPlayerMP.openContainer;
        try {
            return original.call(block, world, x, y, z, player, side, hitX, hitY, hitZ);
        } finally {
            ContainerHosts.onBlockActivated(thisPlayerMP, containerBefore, world, x, y, z);
        }
    }
}
