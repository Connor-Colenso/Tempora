package com.colen.tempora.rendering;

import com.colen.tempora.networking.PacketDetectedInfo;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraftforge.client.event.RenderWorldLastEvent;

@SideOnly(Side.CLIENT)
public final class LastWorldEventRenderer {

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent e) {
        if (PacketDetectedInfo.CLIENT_POS.isEmpty()) return;

        int curDim = Minecraft.getMinecraft().thePlayer.dimension;
        for (PacketDetectedInfo.Pos pos : PacketDetectedInfo.CLIENT_POS) {
            if (pos.dim != curDim) continue;
            RenderUtils.addRenderedBlockInWorld(Blocks.cobblestone, 0,
                pos.x, pos.y, pos.z);
        }
    }
}
