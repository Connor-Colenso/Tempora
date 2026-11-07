package com.colen.tempora.loggers.achievement;

import static com.colen.tempora.utils.GenericUtils.isClientSide;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.stats.Achievement;
import net.minecraft.stats.StatisticsFile;
import net.minecraftforge.event.entity.player.AchievementEvent;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.enums.LoggerEventType;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

// Logs players earning achievements, at where they stood
//
// Forge fires AchievementEvent every time one is triggered, before vanilla checks anything. Picking up a log fires
// "Getting Wood" again for someone who's had it for years, and for someone who hasn't opened their inventory yet so
// vanilla won't give it. Only log when vanilla actually gives it
public class AchievementLogger extends GenericPositionalLogger<AchievementEventInfo> {

    @Override
    protected String playerColumn() {
        return "playerUUID";
    }

    @Override
    public @NotNull String getLoggerName() {
        return TemporaEvents.ACHIEVEMENT;
    }

    @Override
    public @NotNull String getShortName() {
        return "achievement";
    }

    @Override
    public @NotNull AchievementEventInfo newEventInfo() {
        return new AchievementEventInfo();
    }

    @Override
    public @NotNull LoggerEventType getLoggerEventType() {
        return LoggerEventType.ForgeEvent;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onAchievement(final AchievementEvent event) {
        if (!isEnabledByConfig) return;
        if (isClientSide()) return;
        if (event.isCanceled()) return;
        if (!(event.entityPlayer instanceof EntityPlayerMP player)) return;

        // Event fires before the achievement is saved, so these tell us if this is the time it's given
        StatisticsFile stats = player.func_147099_x();
        Achievement achievement = event.achievement;
        if (stats.hasAchievementUnlocked(achievement)) return;
        if (!stats.canUnlockAchievement(achievement)) return;

        AchievementEventInfo eventInfo = new AchievementEventInfo();
        eventInfo.playerUUID = player.getUniqueID()
            .toString();
        eventInfo.achievementID = achievement.statId;

        logEvent(eventInfo, player.dimension, player.posX, player.posY, player.posZ);
    }
}
