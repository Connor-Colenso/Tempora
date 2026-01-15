package com.colen.tempora.loggers.command;

import static com.colen.tempora.rendering.RenderUtils.renderFloatingText;
import static com.colen.tempora.utils.ChatUtils.ONE_DP;
import static com.colen.tempora.utils.GenericUtils.isClientSide;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.command.ICommand;
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
    public @NotNull CommandEventInfo newEventInfo() {
        return new CommandEventInfo();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderEventsInWorld(RenderWorldLastEvent renderEvent) {
        RenderManager renderManager = RenderManager.instance;
        sortByDistanceDescending(transparentEventsToRenderInWorld, renderEvent);

        for (CommandEventInfo cqe : transparentEventsToRenderInWorld) {
            double x = cqe.x - renderManager.viewerPosX;
            double y = cqe.y - renderManager.viewerPosY + 1;
            double z = cqe.z - renderManager.viewerPosZ;

            List<String> toRender = new ArrayList<>();

            toRender.add(
                StatCollector
                    .translateToLocalFormatted("event.command.executed", PlayerUtils.uuidForName(cqe.playerUUID)));
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
        if (isClientSide()) return;
        if (event.isCanceled()) return;

        if (event.sender instanceof EntityPlayerMP player) {
            ICommand command = event.command;
            String[] args = event.parameters;

            CommandEventInfo eventInfo = new CommandEventInfo();
            eventInfo.eventID = UUID.randomUUID()
                .toString();
            eventInfo.x = player.posX;
            eventInfo.y = player.posY;
            eventInfo.z = player.posZ;
            eventInfo.dimensionID = player.dimension;
            eventInfo.timestamp = System.currentTimeMillis();

            eventInfo.playerUUID = player.getUniqueID()
                .toString();
            eventInfo.commandName = command.getCommandName();
            eventInfo.arguments = String.join(" ", args);

            queueEventInfo(eventInfo);
        }
    }
}
