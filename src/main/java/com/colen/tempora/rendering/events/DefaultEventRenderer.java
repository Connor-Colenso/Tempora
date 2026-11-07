package com.colen.tempora.rendering.events;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.ChunkPosition;

import com.colen.tempora.TemporaLoggerManager;
import com.colen.tempora.loggers.generic.PositionalEventInfo;
import com.colen.tempora.rendering.RenderUtils;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

// For loggers without their own renderer. Outlines the block each event happened in with a colour picked from the
// logger name, labelled with the logger and how long ago
@SideOnly(Side.CLIENT)
final class DefaultEventRenderer implements EventRenderer<PositionalEventInfo> {

    @Override
    public void renderWorld(List<PositionalEventInfo> events, EventRenderContext context) {
        for (PositionalEventInfo event : EventRenderHelpers.latestPerBlock(events)) {
            ChunkPosition block = EventRenderHelpers.blockOf(event);
            RenderUtils.renderRegion(
                block.chunkPosX,
                block.chunkPosY,
                block.chunkPosZ,
                block.chunkPosX + 1,
                block.chunkPosY + 1,
                block.chunkPosZ + 1,
                colourOf(event.getLoggerName()));
        }
    }

    @Override
    public void renderText(List<PositionalEventInfo> events, EventRenderContext context) {
        for (List<PositionalEventInfo> here : EventRenderHelpers.groupBy(events, EventRenderHelpers::blockOf)) {
            List<String> lines = new ArrayList<>();
            for (PositionalEventInfo event : here) {
                lines.add(
                    TemporaLoggerManager.displayName(event.getLoggerName()) + " "
                        + EnumChatFormatting.GRAY
                        + EventRenderHelpers.timeAgo(event.timestamp));
            }

            ChunkPosition block = EventRenderHelpers.blockOf(here.get(0));
            EventRenderHelpers
                .renderLabelAbove(lines, block.chunkPosX + 0.5, block.chunkPosY + 1.2, block.chunkPosZ + 0.5);
        }
    }

    // Same colour for a logger every time, usually different per logger
    private static Color colourOf(String loggerName) {
        float hue = (loggerName.hashCode() & 0xFFFF) / (float) 0xFFFF;
        return Color.getHSBColor(hue, 0.7f, 1f);
    }
}
