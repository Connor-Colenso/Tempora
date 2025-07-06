// MixinEntity.java
package com.colen.tempora.mixins;

import com.colen.tempora.mixin_interfaces.IEntityMixin;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// We use this class to keep track of if a mob spawning has been logged by tempora already. Since otherwise reloading chunks will relog the same entity.
@Mixin(Entity.class)
public abstract class MixinEntity implements IEntityMixin {

    @Unique
    private boolean tempora$HasBeenLogged;

    @Inject(method = "writeToNBT", at = @At("RETURN"))
    private void onWrite(NBTTagCompound tag, CallbackInfo ci) {
        tag.setBoolean("temporaHasBeenLogged", this.tempora$HasBeenLogged);
    }

    @Inject(method = "readFromNBT", at = @At("RETURN"))
    private void onRead(NBTTagCompound tag, CallbackInfo ci) {
        this.tempora$HasBeenLogged = tag.getBoolean("temporaHasBeenLogged");
    }

    @Override
    public boolean getTempora$HasBeenLogged() {
        return this.tempora$HasBeenLogged;
    }

    @Override
    public void setTempora$HasBeenLogged(boolean logged) {
        this.tempora$HasBeenLogged = logged;
    }
}
