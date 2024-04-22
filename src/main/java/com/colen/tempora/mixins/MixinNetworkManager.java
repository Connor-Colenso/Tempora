package com.colen.tempora.mixins;

import com.colen.tempora.TemporaUtils;
import com.google.common.collect.Queues;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Queue;

@Mixin(NetworkManager.class)
public class MixinNetworkManager {

    @Shadow
    @Final
    private final Queue receivedPacketsQueue = Queues.newConcurrentLinkedQueue();

    @Unique
    private static final HashSet<String> tempora2$bannedPackets = new HashSet<>();

    static {
        tempora2$bannedPackets.add("S19PacketEntityHeadLook");
        tempora2$bannedPackets.add("C04PacketPlayerPosition");
        tempora2$bannedPackets.add("S15PacketEntityRelMove");
        tempora2$bannedPackets.add("S03PacketTimeUpdate");
        tempora2$bannedPackets.add("FMLProxyPacket");
        tempora2$bannedPackets.add("C03PacketPlayer");
    }

    @Inject(method = "processReceivedPackets", at = @At("HEAD"))
    private void onProcessReceivedPackets(CallbackInfo ci) {
        if (TemporaUtils.isClientSide()) return;

        Packet packet = (Packet) receivedPacketsQueue.peek();
        if (packet == null)  {
            return;
        }
        String packetName = packet.getClass().getSimpleName();
        if (tempora2$bannedPackets.contains(packetName)) return;

        System.out.println(packetName);
    }

}
