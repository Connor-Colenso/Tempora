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

        // Client-side / HUD visibility checks
        if (!mc.theWorld.isRemote) return;
        if (mc.gameSettings.showDebugInfo) return;
        if (!mc.isSingleplayer() && mc.currentScreen == null) return;

        FontRenderer font = mc.fontRenderer;
        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int screenWidth = sr.getScaledWidth();

        // Collect block-change regions
        List<RegionToRender> blockChangeRegions = ClientRegionStore.all()
            .stream()
            .filter(r -> r.getRenderMode() == RegionRenderMode.BLOCK_CHANGE)
            .collect(Collectors.toList());

        if (blockChangeRegions.isEmpty()) return;

        // Initial Y position
        int y = 5;
        final int lineHeight = font.FONT_HEIGHT + 2;

        // Count intersecting regions
        int regionsInside = 0;
        for (RegionToRender region : blockChangeRegions) {
            if (region.getDimID() != player.dimension) continue;
            if (region.intersectsWith(player.dimension, player.boundingBox)) {
                regionsInside++;
            }
        }

        // Draw descriptor line
        String descriptorText = StatCollector.translateToLocalFormatted(
            "tempora.HUD.region.descriptor",
            formatNumber(regionsInside)
        );

        font.drawString(
            descriptorText,
            (screenWidth - font.getStringWidth(descriptorText)) / 2,
            y,
            0xFFFFFF
        );

        y += lineHeight;

        // Draw each intersecting region
        for (RegionToRender region : blockChangeRegions) {
            if (region.getDimID() != player.dimension) continue;
            if (!region.intersectsWith(player.dimension, player.boundingBox)) continue;

            String regionText = StatCollector.translateToLocalFormatted(
                "tempora.HUD.region.list",
                region.getLabel(),
                formatNumberCompact(region.getVolume())
            );

            font.drawString(
                regionText,
                (screenWidth - font.getStringWidth(regionText)) / 2,
                y,
                region.getColor().getRGB() & 0xFFFFFF
            );

            y += lineHeight;
        }
    }
}
