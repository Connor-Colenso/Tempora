package com.colen.tempora.loggers.player_position;

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
// 1. Player positions every n ticks. By default, n = 200 ticks.
// 2. Player teleportation between dimensions. Prevents users from evading the above detector by switching dimensions
// very quickly.
// 3. Player login: This prevents the user from being logged into a dimension and quickly switching dimensions, this
// would cause the dimension to load, which we want to keep track of.
public class PlayerPositionLogger extends GenericPositionalLogger<PlayerPositionEventInfo> {

    // The player who was there.
    @Override
    protected String playerColumn() {
        return "playerUUID";
    }

    @Override
    public @NotNull String getLoggerName() {
        return TemporaEvents.PLAYER_POSITION;
    }

    @Override
    public @NotNull String getShortName() {
        return "playerpos";
    }

    @Override
    public @NotNull PlayerPositionEventInfo newEventInfo() {
        return new PlayerPositionEventInfo();
    }

    private int playerPositionLoggingInterval;

    @Override
    protected void addSettingsInfo(List<IChatComponent> lines) {
        super.addSettingsInfo(lines);
        lines.add(settingLine("tempora.logger.setting.interval", ticks(playerPositionLoggingInterval)));
    }

    @Override
    public void handleCustomLoggerConfig(Configuration config) {
        playerPositionLoggingInterval = config.getInt(
            "playerPositionLoggingInterval",
            getLoggerName(),
            200,
            1,
            Integer.MAX_VALUE,
            "How often player location is recorded by Tempora. Measured in ticks (20/second).");
    }

    // Also holds every column /tempora home reads, so it never touches the table: one player's rows are spread across
    // all of it, and reading them there one by one is slower than not having an index at all.
    @Override
    protected String playerIndexColumns() {
        return "playerUUID, timestamp, dimensionID, x, z, y";
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
            .getTickCounter() % playerPositionLoggingInterval != 0) return;

        logPlayerPosition(player);
    }

    /** Logs where a player is right now, e.g. straight after teleporting them. */
    public void logPlayerPosition(EntityPlayer player) {
        if (!isEnabledByConfig) return;

        PlayerPositionEventInfo eventInfo = new PlayerPositionEventInfo();
        eventInfo.playerUUID = player.getUniqueID()
            .toString();

        logEvent(eventInfo, player.worldObj.provider.dimensionId, player.posX, player.posY, player.posZ);
    }

}
