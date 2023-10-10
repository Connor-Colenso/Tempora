package com.myname.mymodid.Network;

import com.myname.mymodid.Commands.TrackPlayer.PlayerTrackerRenderer;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

import java.util.Comparator;
import java.util.PriorityQueue;

public class PlayerPositionPacketHandler implements IMessageHandler<PlayerPositionPacket, IMessage> {

    @Override
    public IMessage onMessage(PlayerPositionPacket message, MessageContext ctx) {
        if (ctx.side.isClient()) {
            handleClientSide(message);
        }

        return null;
    }

    PriorityQueue<PlayerPosition> newTasks = new PriorityQueue<>(Comparator.comparingDouble(task -> task.time));

    private void handleClientSide(PlayerPositionPacket message) {
        // Data received.
        double[] xs = message.getX();
        double[] ys = message.getY();
        double[] zs = message.getZ();
        long[] times = message.getTime();

        // This seems a bit convoluted but exists for two reaosns.
        // 1. Stops packets from being excessively large and causing OPs to get kicked.
        // 2. Stops flickering when processing a new packet

        if (message.firstPacket) {
            newTasks.clear();
        }

        for (int i = 0; i < xs.length; i++) {
            newTasks.add(new PlayerPosition(xs[i], ys[i], zs[i], times[i]));
        }

        if (message.lastPacket) {
            PlayerTrackerRenderer.tasks = newTasks;
        }
    }

    public static class PlayerPosition {

        public PlayerPosition(double x, double y, double z, long time) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.time = time;
        }

        public long time;
        public double x;
        public double y;
        public double z;

    }
}
