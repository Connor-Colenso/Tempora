package com.colen.tempora.mixins;

import net.minecraft.network.INetHandler;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.colen.tempora.loggers.inventory.InventoryTransactionTracker;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

// Every packet a player sends, mod packets included, gets handled here on the server thread. Wrapping each one lets the
// inventory logger compare items before and after whatever the packet did
@Mixin(NetworkManager.class)
public abstract class MixinNetworkManager {

    @WrapOperation(
        method = "processReceivedPackets",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/Packet;processPacket(Lnet/minecraft/network/INetHandler;)V"))
    private void tempora$trackInventoryTransaction(Packet packet, INetHandler handler, Operation<Void> original) {
        if (!(handler instanceof NetHandlerPlayServer serverHandler)) {
            original.call(packet, handler);
            return;
        }

        InventoryTransactionTracker.PacketToken token = InventoryTransactionTracker
            .beforePacket(serverHandler.playerEntity, packet);
        try {
            original.call(packet, handler);
        } finally {
            InventoryTransactionTracker.afterPacket(token);
        }
    }
}
