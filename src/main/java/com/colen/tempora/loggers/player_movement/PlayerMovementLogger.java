package com.colen.tempora.loggers.player_movement;

import static com.colen.tempora.TemporaUtils.isClientSide;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.config.Configuration;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.enums.LoggerEnum;
import com.colen.tempora.enums.LoggerEventType;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.PlayerTickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

// This class logs three items to the same database.
// 1. Player movement every n ticks. By default, n = 200 ticks.
// 2. Player teleportation between dimensions. Prevents users from evading the above detector by switching dims very
// quickly.
// 3. Player login, prevents the user from being logged into a dimension and quickly switching dims, this would
// cause the dimension to load, which we want to keep track of.
public class PlayerMovementLogger extends GenericPositionalLogger<PlayerMovementQueueElement> {

    @Override
    public @NotNull PlayerMovementQueueElement getQueueElementInstance() {
        return new PlayerMovementQueueElement();
    }

    @Override
    public LoggerEnum getLoggerType() {
        return LoggerEnum.PlayerMovementLogger;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderEventsInWorld(RenderWorldLastEvent e) {

    }

    private int playerMovementLoggingInterval;

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
        return LoggerEventType.ForgeEvent;
    }

    @SubscribeEvent
    @SuppressWarnings("unused")
    public void onPlayerTick(final @NotNull PlayerTickEvent event) {
        if (isClientSide()) return;
        if (event.isCanceled()) return;
        if (event.phase != TickEvent.Phase.START) return;
        if (!(event.player instanceof EntityPlayerMP player)) return;
        if (FMLCommonHandler.instance()
            .getMinecraftServerInstance()
            .getTickCounter() % playerMovementLoggingInterval != 0) return;

        PlayerMovementQueueElement queueElement = new PlayerMovementQueueElement();
        queueElement.eventID = UUID.randomUUID()
            .toString();
        queueElement.x = player.posX;
        queueElement.y = player.posY;
        queueElement.z = player.posZ;
        queueElement.dimensionID = player.worldObj.provider.dimensionId;
        queueElement.timestamp = System.currentTimeMillis();

        queueElement.playerUUID = player.getUniqueID()
            .toString();

        queueEvent(queueElement);
    }

}
