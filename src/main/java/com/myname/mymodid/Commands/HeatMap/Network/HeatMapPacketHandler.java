package com.myname.mymodid.Commands.HeatMap.Network;

import java.util.ArrayList;

import com.myname.mymodid.Commands.HeatMap.HeatMapRenderer;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

public class HeatMapPacketHandler implements IMessageHandler<HeatMapPacket, IMessage> {

    @Override
    public IMessage onMessage(HeatMapPacket message, MessageContext ctx) {
        if (ctx.side.isClient()) {
            handleClientSide(message);
        }

        return null;
    }

    ArrayList<HeatMapPacketHandler.PlayerPostion> newTasks = new ArrayList<>();

    private void handleClientSide(HeatMapPacket message) {
        // Data received.
        int[] xs = message.getX();
        int[] ys = message.getY();
        int[] zs = message.getZ();
        double[] intensity = message.getIntensity();

        // This seems a bit convoluted but exists for two reasons.
        // 1. Stops packets from being excessively large and causing OPs to get kicked.
        // 2. Stops flickering when processing a new packet

        if (message.isFirstPacket()) {
            newTasks.clear();
        }

        for (int i = 0; i < xs.length; i++) {
            newTasks.add(new PlayerPostion(xs[i], ys[i], zs[i], intensity[i]));
        }

        if (message.isLastPacket()) {
            HeatMapRenderer.tasks = newTasks;
        }
    }

    public static class PlayerPostion {

        int x;
        int y;
        int z;
        double intensity;

        public PlayerPostion(int x, int y, int z, double intensity) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.intensity = intensity;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getZ() {
            return z;
        }

        public double getIntensity() {
            return intensity;
        }
    }
}
