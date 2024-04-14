package com.colen.tempora.mixins;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.client.C0EPacketClickWindow;

@Mixin(targets = "net.minecraft.entity.player.EntityPlayerMP") // Specify the class to target
public class NetHandlerPlayServerMixin {

    @Inject(method = "processClickWindow(Lnet/minecraft/network/play/client/C0EPacketClickWindow;)V", at = @At("HEAD"))
    private void injectProcessClickWindow(C0EPacketClickWindow packetIn, CallbackInfo ci) {
        MyUtils.processPacket((EntityPlayerMP)(Object)this, packetIn); // Cast 'this' to the appropriate type
    }

}
