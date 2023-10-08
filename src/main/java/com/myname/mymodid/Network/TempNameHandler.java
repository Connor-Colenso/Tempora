package com.myname.mymodid.Network;

import com.myname.mymodid.Rendering.PlayerPosition;
import com.myname.mymodid.Rendering.RenderEvent;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

public class TempNameHandler implements IMessageHandler<TempName, IMessage> {

    @Override
    public IMessage onMessage(TempName message, MessageContext ctx) {
        if (ctx.side.isClient()) {
            handleClientSide(message);
        }

        return null;
    }

    private void handleClientSide(TempName message) {
        // Data received.

        double x = message.getX();
        double y = message.getY();
        double z = message.getZ();
        long time = message.getTime();

        if (message.firstPacket()) {
            RenderEvent.clearBuffer();
        }

        RenderEvent.tasks.add(new PlayerPosition(x, y, z, time));
    }
}
