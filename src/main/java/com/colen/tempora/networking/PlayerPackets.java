package com.colen.tempora.networking;

import static com.colen.tempora.Tempora.NETWORK;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.util.FakePlayer;

import cpw.mods.fml.common.network.simpleimpl.IMessage;

/** Sends Tempora's packets to players, only those with a real connection to send them over. */
public final class PlayerPackets {

    private PlayerPackets() {}

    /**
     * Sends a packet to a player, unless they have no connection, such as a machine's fake player, which is skipped.
     * Every packet to a player goes through here: FML can't find a fake player's connection, and rather than throwing
     * here it keeps the error in Tempora's shared server channel, then throws it at the next packet any player's
     * client sends Tempora, disconnecting that player.
     */
    public static void sendTo(IMessage message, EntityPlayerMP player) {
        if (!hasConnection(player)) return;
        NETWORK.sendTo(message, player);
    }

    /** Whether a player is a real client, with a connection packets can be sent over. */
    public static boolean hasConnection(EntityPlayerMP player) {
        // Some mods' fake players (EnderIO's farm) don't extend FakePlayer, so the connection itself is checked too.
        return !(player instanceof FakePlayer) && player.playerNetServerHandler != null
            && player.playerNetServerHandler.netManager != null
            && player.playerNetServerHandler.netManager.channel() != null;
    }
}
