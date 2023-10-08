package com.myname.mymodid.Network;

import com.myname.mymodid.Particle.RedBoxParticle;
import com.myname.mymodid.Rendering.PlayerPosition;
import com.myname.mymodid.Rendering.RenderEvent;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;

public class TempNameHandler implements IMessageHandler<TempName, IMessage> {

    @Override
    public IMessage onMessage(TempName message, MessageContext ctx) {
        if (ctx.side.isClient()) {
            // Handle the packet client side.
            handleClientSide(message, ctx);
        }

        return null; // No response packet.
    }

    private void handleClientSide(TempName message, MessageContext ctx) {
        // This runs on Minecraft's main thread since networking runs on a separate thread.
        // It's safe to interact with game data here.

        double x = message.getX();
        double y = message.getY();
        double z = message.getZ();

        // TODO: Write your client-side code here for handling the received data.

        RenderEvent.tasks.add(new PlayerPosition(x,y,z));

//        EntityFX particle = new RedBoxParticle(Minecraft.getMinecraft().theWorld, x, y, z);
//        Minecraft.getMinecraft().effectRenderer.addEffect(particle);
    }
}
