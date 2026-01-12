package com.colen.tempora.rendering.regions;

import static com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber;
import static com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumberCompact;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import com.colen.tempora.loggers.block_change.region_registry.RegionToRender;
import com.colen.tempora.networking.PacketShowRegionInWorld;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class HudRenderRegionsInWorld {

    private final Minecraft mc = Minecraft.getMinecraft();

    // todo review the fact that rendered region seems 1 block too large in every direction.

    @SubscribeEvent
    public void onRenderText(RenderGameOverlayEvent.Text event) {
        // Only render HUD text
        if (event.type != RenderGameOverlayEvent.ElementType.TEXT) return;

        EntityClientPlayerMP player = mc.thePlayer;
        if (player == null) return;

        // Hide HUD when Tab is held in multiplayer
        if (!mc.theWorld.isRemote) return; // server safety
        if (mc.gameSettings.showDebugInfo) return; // hide if F3 debug
        if (!mc.isSingleplayer() && mc.currentScreen == null) {
            // Optional: detect if Tab list is visible in MP
            return;
        }

        FontRenderer font = mc.fontRenderer;

        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int screenWidth = sr.getScaledWidth();

        // Count regions the player is inside
        int regionsInside = 0;
        for (RegionToRender region : PacketShowRegionInWorld.CLIENT_REGIONS) {
            if (region.dim != player.dimension) continue;
            if (region.contains(player.dimension, player.posX, player.posY, player.posZ)) {
                regionsInside++;
            }
        }

        int yOffset = 5;

        // Draw descriptor centered at top.
        if (!PacketShowRegionInWorld.CLIENT_REGIONS.isEmpty()) {
            String descriptorText = StatCollector
                .translateToLocalFormatted("tempora.HUD.region.descriptor", formatNumber(regionsInside));
            font.drawString(descriptorText, (screenWidth - font.getStringWidth(descriptorText)) / 2, yOffset, 0xFFFFFF);
        }

        // Draw each region below, numbered, with bullets
        int lineHeight = 10;
        int index = 1;
        for (RegionToRender region : PacketShowRegionInWorld.CLIENT_REGIONS) {
            if (region.dim != player.dimension) continue;
            if (region.contains(player.dimension, player.posX, player.posY, player.posZ)) {
                String regionText = StatCollector.translateToLocalFormatted(
                    "tempora.HUD.region.list",
                    index++,
                    formatNumberCompact(region.getVolume()));
                font.drawString(
                    regionText,
                    (screenWidth - font.getStringWidth(regionText)) / 2,
                    yOffset + lineHeight * index,
                    region.color.getRGB() & 0xFFFFFF);
            }
        }
    }
}
