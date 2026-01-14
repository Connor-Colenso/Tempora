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

import com.colen.tempora.loggers.block_change.region_registry.TemporaWorldRegion;
import com.colen.tempora.rendering.ClientRegionStore;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class HudRenderRegionsInWorld {

    private final Minecraft mc = Minecraft.getMinecraft();

    // Probably very inefficient, but it's fine for now.
    @SubscribeEvent
    public void onRenderText(RenderGameOverlayEvent.Text event) {
        if (event.type != RenderGameOverlayEvent.ElementType.TEXT) return;

        EntityClientPlayerMP player = mc.thePlayer;
        if (player == null) return;

        if (!mc.theWorld.isRemote) return;
        if (mc.gameSettings.showDebugInfo) return;
        if (!mc.isSingleplayer() && mc.currentScreen == null) return;

        FontRenderer font = mc.fontRenderer;
        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int screenWidth = sr.getScaledWidth();

        // Collect block-change regions
        List<TemporaWorldRegion> blockChangeRegions = ClientRegionStore.all()
            .stream()
            .filter(r -> r.getRenderMode() == RegionRenderMode.BLOCK_CHANGE)
            .collect(Collectors.toList());

        if (blockChangeRegions.isEmpty()) return;

        // Filter to regions intersecting the player
        List<TemporaWorldRegion> intersectingRegions = blockChangeRegions.stream()
            .filter(r -> r.getDimID() == player.dimension)
            .filter(r -> r.intersectsWith(player.dimension, player.boundingBox))
            .collect(Collectors.toList());

        if (intersectingRegions.isEmpty()) return;

        int y = 5;
        final int lineHeight = font.FONT_HEIGHT + 2;

        // Draw descriptor line
        String descriptorText;
        if (intersectingRegions.size() == 1) {
            descriptorText = StatCollector.translateToLocalFormatted(
                "tempora.HUD.region.descriptor.singular",
                formatNumber(intersectingRegions.size()));
        } else {
            descriptorText = StatCollector.translateToLocalFormatted(
                "tempora.HUD.region.descriptor.plural",
                formatNumber(intersectingRegions.size()));
        }

        font.drawString(descriptorText, (screenWidth - font.getStringWidth(descriptorText)) / 2, y, 0xFFFFFF);
        y += lineHeight;

        // Draw up to MAX_REGIONS_TO_SHOW_ON_HUD intersecting regions
        final int MAX_REGIONS_TO_SHOW_ON_HUD = 10;
        int indexEnd = Math.min(intersectingRegions.size(), MAX_REGIONS_TO_SHOW_ON_HUD);

        for (TemporaWorldRegion region : intersectingRegions.subList(0, indexEnd)) {
            String regionText = StatCollector.translateToLocalFormatted(
                "tempora.HUD.region.list",
                region.getLabel(),
                formatNumberCompact(region.getVolume()));

            font.drawString(
                regionText,
                (screenWidth - font.getStringWidth(regionText)) / 2,
                y,
                region.getColor()
                    .getRGB() & 0xFFFFFF);
            y += lineHeight;
        }

        // Show "..." if there are more regions beyond those displayed
        int remaining = intersectingRegions.size() - MAX_REGIONS_TO_SHOW_ON_HUD;
        if (remaining > 0) {
            String moreText = StatCollector.translateToLocalFormatted("tempora.hud.render.and.more", remaining);

            font.drawString(moreText, (screenWidth - font.getStringWidth(moreText)) / 2, y, 0xFFFFFF);
        }
    }
}
