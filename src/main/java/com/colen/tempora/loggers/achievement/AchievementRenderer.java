package com.colen.tempora.loggers.achievement;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.stats.Achievement;
import net.minecraft.util.EnumChatFormatting;

import com.colen.tempora.rendering.events.EventRenderHelpers;
import com.colen.tempora.rendering.events.PlayerGhostRenderer;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

// Player where they earned each achievement holding its icon, with the achievements earned there above
@SideOnly(Side.CLIENT)
public class AchievementRenderer extends PlayerGhostRenderer<AchievementEventInfo> {

    public AchievementRenderer() {
        super(Color.GREEN);
    }

    @Override
    protected String playerUUID(AchievementEventInfo event) {
        return event.playerUUID;
    }

    @Override
    protected @Nullable ItemStack heldItem(AchievementEventInfo event) {
        Achievement achievement = event.achievement();
        return achievement == null || achievement.theItemStack == null ? null : achievement.theItemStack.copy();
    }

    @Override
    protected List<String> label(List<AchievementEventInfo> eventsHere) {
        List<String> lines = new ArrayList<>();
        for (AchievementEventInfo event : eventsHere) {
            Achievement achievement = event.achievement();
            String name = achievement != null ? achievement.func_150951_e()
                .getUnformattedText() : event.achievementID;

            lines.add(
                EnumChatFormatting.GREEN + name
                    + " "
                    + EnumChatFormatting.GRAY
                    + EventRenderHelpers.timeAgo(event.timestamp));
        }
        return lines;
    }
}
