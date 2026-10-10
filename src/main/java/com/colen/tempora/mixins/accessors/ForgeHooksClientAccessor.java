package com.colen.tempora.mixins.accessors;

import net.minecraftforge.client.ForgeHooksClient;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Forge's current world render pass, so blocks rendered outside chunk rendering can be drawn as if in a given pass.
@Mixin(value = ForgeHooksClient.class, remap = false)
public interface ForgeHooksClientAccessor {

    @Accessor("worldRenderPass")
    static void tempora$setWorldRenderPass(int pass) {
        throw new AssertionError();
    }
}
