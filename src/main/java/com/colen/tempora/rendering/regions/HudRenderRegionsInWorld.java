package com.colen.tempora.rendering.regions;

import static com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber;
import static com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumberCompact;

import java.util.List;
import java.util.stream.Collectors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import com.colen.tempora.loggers.block_change.region_registry.RegionToRender;
import com.colen.tempora.rendering.ClientRegionStore;

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

        List<RegionToRender> blockChangeRegions = ClientRegionStore.all()
            .stream()
            .filter(r -> r.getRenderMode() == RegionRenderMode.BLOCK_CHANGE)
            .collect(Collectors.toList());

        // Count regions the player is inside
        // todo make neater (compact loops)
        int regionsInside = 0;
        for (RegionToRender region : blockChangeRegions) {
            if (region.getDimID() != player.dimension) continue;
            if (region.intersectsWith(player.dimension, player.boundingBox)) {
                regionsInside++;
            }
        }

        int yOffset = 5;

        // Draw descriptor centered at top.
        if (!blockChangeRegions.isEmpty()) {
            String descriptorText = StatCollector
                .translateToLocalFormatted("tempora.HUD.region.descriptor", formatNumber(regionsInside));
            font.drawString(descriptorText, (screenWidth - font.getStringWidth(descriptorText)) / 2, yOffset, 0xFFFFFF);
        }

        // Draw each region below, numbered, with bullets
        int lineHeight = 10;
        int index = 1;
        for (RegionToRender region : blockChangeRegions) {
            if (region.getDimID() != player.dimension) continue;

            if (region.intersectsWith(player.dimension, player.boundingBox)) {
                String regionText = StatCollector.translateToLocalFormatted(
                    "tempora.HUD.region.list",
                    region.getLabel(),
                    formatNumberCompact(region.getVolume()));
                font.drawString(
                    regionText,
                    (screenWidth - font.getStringWidth(regionText)) / 2,
                    yOffset + lineHeight * index,
                    region.getColor()
                        .getRGB() & 0xFFFFFF);
            }
        }
    }
}
