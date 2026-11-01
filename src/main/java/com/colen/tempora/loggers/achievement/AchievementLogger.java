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

/**
 * Logs players earning achievements, where they stood.
 * <p>
 * Forge fires AchievementEvent every time an achievement is triggered, before vanilla checks anything: picking up a
 * log fires "Getting Wood" again for a player who has had it for years, and for one who has not yet opened their
 * inventory, whom vanilla will not give it to. Only the times vanilla actually gives it are logged.
 */
public class AchievementLogger extends GenericPositionalLogger<AchievementEventInfo> {

    // The player who earned it.
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
        // Server side only.
        if (!isEnabledByConfig) return;
        if (isClientSide()) return;
        if (event.isCanceled()) return;
        if (!(event.entityPlayer instanceof EntityPlayerMP player)) return;

        // The event comes before the achievement is saved, so these say whether this is the time it is given.
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
