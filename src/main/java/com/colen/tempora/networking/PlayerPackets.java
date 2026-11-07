package com.colen.tempora.networking;

import static com.colen.tempora.Tempora.NETWORK;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.util.FakePlayer;

import cpw.mods.fml.common.network.simpleimpl.IMessage;

// Sends our packets to players, only ones with a real connection
public final class PlayerPackets {

    private PlayerPackets() {}

    // Sends a packet to a player unless they have no connection (a machine's fake player), which gets skipped. Every
    // packet to a player goes through here since FML can't find a fake player's connection, and instead of throwing
    // here it parks the error in our shared server channel and throws it at the next packet any client sends us,
    // disconnecting that player
    public static void sendTo(IMessage message, EntityPlayerMP player) {
        if (!hasConnection(player)) return;
        NETWORK.sendTo(message, player);
    }

    // Whether a player is a real client with a connection packets can go over
    public static boolean hasConnection(EntityPlayerMP player) {
        // Some mods' fake players (EnderIO farm) don't extend FakePlayer, so check the connection too
        return !(player instanceof FakePlayer) && player.playerNetServerHandler != null
            && player.playerNetServerHandler.netManager != null
            && player.playerNetServerHandler.netManager.channel() != null;
    }
}
