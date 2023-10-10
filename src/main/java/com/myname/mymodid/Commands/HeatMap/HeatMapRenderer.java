package com.myname.mymodid.Commands.HeatMap;

import com.myname.mymodid.Network.HeatMapPacketHandler;
import net.minecraft.init.Blocks;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;

import static com.myname.mymodid.Rendering.RenderUtils.addRenderedBlockInWorld;


public class HeatMapRenderer {

    public static ArrayList<HeatMapPacketHandler.PlayerPostion> tasks = new ArrayList<>();

    public static void renderInWorld(RenderWorldLastEvent event) {

        for (HeatMapPacketHandler.PlayerPostion postion : tasks) {
            GL11.glPushMatrix();
            GL11.glColor4f(1.0F, 0.0F, 0.0F, (float) postion.getIntensity());
            addRenderedBlockInWorld(Blocks.stone, 0, postion.getX(), postion.getY(), postion.getZ());
            GL11.glPopMatrix();
        }

    }
}
