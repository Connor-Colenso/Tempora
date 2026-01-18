package com.colen.tempora.rendering;

import static com.colen.tempora.config.DebugConfig.IDMetaRenderingRadius;
import static com.colen.tempora.config.DebugConfig.showIDMetaRenderInBlocksNearby;

import java.util.Collections;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class RenderIDsInWorld {

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent e) {
        if (!showIDMetaRenderInBlocksNearby) return;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        World world = mc.theWorld;

        if (player == null || world == null) return;

        // Interpolated camera position+
        double camX = player.lastTickPosX + (player.posX - player.lastTickPosX) * e.partialTicks;
        double camY = player.lastTickPosY + (player.posY - player.lastTickPosY) * e.partialTicks;
        double camZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * e.partialTicks;

        int px = MathHelper.floor_double(player.posX);
        int py = MathHelper.floor_double(player.posY);
        int pz = MathHelper.floor_double(player.posZ);

        for (int dx = -IDMetaRenderingRadius; dx <= IDMetaRenderingRadius; dx++) {
            for (int dy = -IDMetaRenderingRadius; dy <= IDMetaRenderingRadius; dy++) {
                for (int dz = -IDMetaRenderingRadius; dz <= IDMetaRenderingRadius; dz++) {

                    int x = px + dx;
                    int y = py + dy;
                    int z = pz + dz;

                    Block block = world.getBlock(x, y, z);
                    if (block == null || block.isAir(world, x, y, z)) continue;

                    int id = Block.getIdFromBlock(block);
                    int meta = world.getBlockMetadata(x, y, z);

                    String text = id + ":" + meta;

                    // Center of block, relative to camera
                    double rx = x + 0.5D - camX;
                    double ry = y + 0.5D - camY;
                    double rz = z + 0.5D - camZ;

                    RenderUtils.renderFloatingText(Collections.singletonList(text), rx, ry, rz);
                }
            }
        }
    }
}
