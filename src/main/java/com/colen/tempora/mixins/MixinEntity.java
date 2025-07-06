package com.colen.tempora.mixins;

import com.colen.tempora.mixin_interfaces.IEntityMixin;
import net.minecraft.nbt.NBTTagCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.entity.Entity;

import java.util.UUID;

@Mixin(Entity.class)
public abstract class MixinEntity implements IEntityMixin {

    @Unique
    private String tempora$EntityUUID;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onConstructed(CallbackInfo ci) {
        this.tempora$EntityUUID = UUID.randomUUID().toString();
    }

    // Save the uuid to NBT
    @Inject(method = "writeToNBT", at = @At("RETURN"))
    private void writeUuidToNBT(NBTTagCompound compound, CallbackInfo ci) {
        if (this.tempora$EntityUUID != null) {
            compound.setString("temporaEntityUUID", this.tempora$EntityUUID);
        }
    }

    // Load the uuid from NBT
    @Inject(method = "readFromNBT", at = @At("RETURN"))
    private void readUuidFromNBT(NBTTagCompound compound, CallbackInfo ci) {
        if (compound.hasKey("temporaEntityUUID")) {
            this.tempora$EntityUUID = compound.getString("temporaEntityUUID");
        } else {
            // Safety: generate a new UUID if none present
            this.tempora$EntityUUID = UUID.randomUUID().toString();
        }
    }

    @Override
    public String getTempora$EntityUUID() {
        return this.tempora$EntityUUID;
    }
}
