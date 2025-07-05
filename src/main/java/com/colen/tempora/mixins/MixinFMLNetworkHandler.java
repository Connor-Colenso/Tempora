package com.colen.tempora.mixins;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.colen.tempora.utils.LastInvPos;

import cpw.mods.fml.common.network.internal.FMLNetworkHandler;

@Mixin(value = FMLNetworkHandler.class, remap = false)
public abstract class MixinFMLNetworkHandler {

    @Inject(method = "openGui", at = @At("HEAD"), remap = false)
    private static void tempora$onOpenGui(EntityPlayer player, Object mod, int guiId, World world, int x, int y, int z,
        CallbackInfo ci) {

        int dim = world.provider.dimensionId;
        LastInvPos pos = new LastInvPos(dim, x, y, z);
        LastInvPos.LAST_OPENED.put(player.getUniqueID(), pos);
    }
}
