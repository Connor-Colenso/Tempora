package com.colen.tempora.mixins;

import static com.colen.tempora.Tempora.LOG;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.colen.tempora.loggers.inventory.ContainerHosts;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;

import cpw.mods.fml.common.network.internal.FMLNetworkHandler;

// Records which block a mod GUI was opened for, so the inventory logger knows where its container is.
@Mixin(value = FMLNetworkHandler.class, remap = false)
public abstract class MixinFMLNetworkHandler {

    @Inject(method = "openGui", at = @At("HEAD"))
    private static void tempora$beforeOpenGui(EntityPlayer player, Object mod, int guiId, World world, int x, int y,
        int z, CallbackInfo ci, @Share("tempora$containerBefore") LocalRef<Container> containerBefore) {
        containerBefore.set(player.openContainer);
    }

    @Inject(method = "openGui", at = @At("RETURN"))
    private static void tempora$afterOpenGui(EntityPlayer player, Object mod, int guiId, World world, int x, int y,
        int z, CallbackInfo ci, @Share("tempora$containerBefore") LocalRef<Container> containerBefore) {
        // Names the container from the block's tile entity, which is mod code: it must never stop the GUI opening.
        try {
            ContainerHosts.onGuiOpened(player, containerBefore.get(), world, x, y, z);
        } catch (RuntimeException | LinkageError e) {
            LOG.error("Tempora could not record which block opened a GUI; the GUI itself is unaffected.", e);
        }
    }
}
