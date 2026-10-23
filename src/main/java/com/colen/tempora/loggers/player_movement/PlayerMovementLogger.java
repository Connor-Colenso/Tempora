package com.colen.tempora.loggers.player_movement;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.common.config.Configuration;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.enums.LoggerEventType;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.PlayerTickEvent;

// This class logs three items to the same database.
// 1. Player movement every n ticks. By default, n = 200 ticks.
// 2. Player teleportation between dimensions. Prevents users from evading the above detector by switching dimensions
// very quickly.
// 3. Player login: This prevents the user from being logged into a dimension and quickly switching dimensions, this
// would cause the dimension to load, which we want to keep track of.
public class PlayerMovementLogger extends GenericPositionalLogger<PlayerMovementEventInfo> {

    @Override
    public @NotNull String getLoggerName() {
        return TemporaEvents.PLAYER_MOVEMENT;
    }

    @Override
    public @NotNull String getShortName() {
        return "movement";
    }

    @Override
    public @NotNull PlayerMovementEventInfo newEventInfo() {
        return new PlayerMovementEventInfo();
    }

    private int playerMovementLoggingInterval;

    @Override
    protected void addSettingsInfo(List<IChatComponent> lines) {
        super.addSettingsInfo(lines);
        lines.add(settingLine("tempora.logger.setting.interval", ticks(playerMovementLoggingInterval)));
    }

    @Override
    public void handleCustomLoggerConfig(Configuration config) {
        playerMovementLoggingInterval = config.getInt(
            "playerMovementLoggingInterval",
            getLoggerName(),
            200,
            1,
            Integer.MAX_VALUE,
            "How often player location is recorded by Tempora. Measured in ticks (20/second).");
    }

    @Override
    public @NotNull LoggerEventType getLoggerEventType() {
        return LoggerEventType.MinecraftEvent;
    }

    @SubscribeEvent
    public void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!isEnabledByConfig) return;
        if (!(event.player instanceof EntityPlayerMP player)) return;

        logPlayerPosition(player);
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!isEnabledByConfig) return;
        if (!(event.player instanceof EntityPlayerMP player)) return;

        logPlayerPosition(player);
    }

    @SubscribeEvent
    @SuppressWarnings("unused")
    public void onPlayerTick(final @NotNull PlayerTickEvent event) {
        if (!isEnabledByConfig) return;
        if (event.phase != TickEvent.Phase.START) return;
        if (!(event.player instanceof EntityPlayerMP player)) return;
        if (FMLCommonHandler.instance()
            .getMinecraftServerInstance()
            .getTickCounter() % playerMovementLoggingInterval != 0) return;

        logPlayerPosition(player);
    }

    /** Logs where a player is right now, e.g. straight after teleporting them. */
    public void logPlayerPosition(EntityPlayer player) {
        if (!isEnabledByConfig) return;

        PlayerMovementEventInfo eventInfo = new PlayerMovementEventInfo();
        eventInfo.playerUUID = player.getUniqueID()
            .toString();

        logEvent(eventInfo, player.worldObj.provider.dimensionId, player.posX, player.posY, player.posZ);
    }

}
