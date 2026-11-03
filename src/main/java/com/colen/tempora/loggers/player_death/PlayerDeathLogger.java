package com.colen.tempora.loggers.player_death;

import static com.colen.tempora.utils.GenericUtils.isClientSide;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.enums.LoggerEventType;
import com.colen.tempora.loggers.entity_death.DeathCause;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Logs players dying: where, what killed them, and what they stood to lose. What they dropped is logged by the
 * ItemPickupDropLogger.
 */
public class PlayerDeathLogger extends GenericPositionalLogger<PlayerDeathEventInfo> {

    // The player who died.
    @Override
    protected String playerColumn() {
        return "playerUUID";
    }

    @Override
    public @NotNull String getLoggerName() {
        return TemporaEvents.PLAYER_DEATH;
    }

    @Override
    public @NotNull String getShortName() {
        return "playerdeath";
    }

    @Override
    public @NotNull PlayerDeathEventInfo newEventInfo() {
        return new PlayerDeathEventInfo();
    }

    @Override
    public @NotNull LoggerEventType getLoggerEventType() {
        return LoggerEventType.ForgeEvent;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onPlayerDeath(LivingDeathEvent event) {
        if (!isEnabledByConfig) return;
        if (isClientSide()) return;
        if (event.isCanceled()) return;
        if (!(event.entityLiving instanceof EntityPlayerMP player)) return;

        logPlayerDeath(player, event.source);
    }

    // LivingDeathEvent fires before the player drops anything or loses their XP, so both are still as they were.
    private void logPlayerDeath(EntityPlayerMP player, @Nullable DamageSource source) {
        PlayerDeathEventInfo eventInfo = new PlayerDeathEventInfo();
        eventInfo.playerUUID = player.getUniqueID()
            .toString();

        DeathCause cause = DeathCause.of(source);
        eventInfo.killerPlayerUUID = cause.killerPlayerUUID;
        eventInfo.killedBy = cause.killedBy;

        eventInfo.xpLevel = player.experienceLevel;
        eventInfo.keepInventory = player.worldObj.getGameRules()
            .getGameRuleBooleanValue("keepInventory");

        eventInfo.rotationYaw = player.rotationYaw;
        eventInfo.rotationPitch = player.rotationPitch;

        logEvent(eventInfo, player.dimension, player.posX, player.posY, player.posZ);
    }
}
