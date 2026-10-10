package com.colen.tempora.loggers.command;

import static com.colen.tempora.rendering.RenderUtils.renderFloatingText;
import static com.colen.tempora.utils.ChatUtils.ONE_DP;
import static com.colen.tempora.utils.GenericUtils.isClientSide;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.CommandEvent;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.TemporaEvents;
import com.colen.tempora.enums.LoggerEventType;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.utils.PlayerUtils;
import com.colen.tempora.utils.TimeUtils;
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class CommandLogger extends GenericPositionalLogger<CommandEventInfo> {

    @Override
    public @NotNull String getLoggerName() {
        return TemporaEvents.COMMAND;
    }

    @Override
    public @NotNull String getShortName() {
        return "command";
    }

    @Override
    public @NotNull CommandEventInfo newEventInfo() {
        return new CommandEventInfo();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderEventTextInWorld(RenderWorldLastEvent renderEvent) {
        RenderManager renderManager = RenderManager.instance;
        sortByDistanceDescending(transparentEventsToRenderInWorld, renderEvent);

        for (CommandEventInfo cqe : transparentEventsToRenderInWorld) {
            double x = cqe.x - renderManager.viewerPosX;
            double y = cqe.y - renderManager.viewerPosY + 1;
            double z = cqe.z - renderManager.viewerPosZ;

            List<String> toRender = new ArrayList<>();

            toRender.add(
                StatCollector
                    .translateToLocalFormatted("event.command.executed", PlayerUtils.UUIDToName(cqe.playerUUID)));
            toRender.add("/" + cqe.commandName + " " + cqe.arguments);

            TimeUtils.DurationParts formattedTime = TimeUtils.relativeTimeAgoFormatter(cqe.timestamp);

            toRender.add(
                StatCollector.translateToLocalFormatted(
                    formattedTime.translationKey,
                    NumberFormatUtil.formatNumber(formattedTime.time, ONE_DP)));

            renderFloatingText(toRender, x, y, z);
        }
    }

    @Override
    public @NotNull LoggerEventType getLoggerEventType() {
        return LoggerEventType.ForgeEvent;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused")
    public void onCommand(final CommandEvent event) {
        // Server side only.
        if (!isEnabledByConfig) return;
        if (isClientSide()) return;
        if (event.isCanceled()) return;

        if (event.sender instanceof EntityPlayerMP player) {
            logCommand(player, event.command.getCommandName(), event.parameters);
        }
    }

    /** Logs a command a player ran, at the player's position. */
    public void logCommand(EntityPlayer player, String commandName, String[] arguments) {
        if (!isEnabledByConfig) return;

        CommandEventInfo eventInfo = new CommandEventInfo();
        eventInfo.playerUUID = player.getUniqueID()
            .toString();
        eventInfo.commandName = commandName;
        eventInfo.arguments = String.join(" ", arguments);

        logEvent(eventInfo, player.dimension, player.posX, player.posY, player.posZ);
    }
}
