package com.colen.tempora.mixins;

import net.minecraft.world.NextTickListEntry;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import com.colen.tempora.utils.WorldGenTicks;

@Mixin(NextTickListEntry.class)
public abstract class MixinNextTickListEntry implements WorldGenTicks.Marked {

    @Unique
    private long tempora$worldGenUntil;

    @Override
    public long tempora$getWorldGenUntil() {
        return tempora$worldGenUntil;
    }

    @Override
    public void tempora$setWorldGenUntil(long worldTime) {
        tempora$worldGenUntil = worldTime;
    }
}
