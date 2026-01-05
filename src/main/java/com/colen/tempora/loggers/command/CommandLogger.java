package com.colen.tempora.loggers.command;

import static com.colen.tempora.TemporaUtils.isClientSide;
import static com.colen.tempora.rendering.RenderUtils.renderFloatingText;
import static com.colen.tempora.utils.DatabaseUtils.MISSING_STRING_DATA;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.command.ICommand;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.CommandEvent;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.chat.ChatComponentTimeRelative;
import com.colen.tempora.enums.LoggerEnum;
import com.colen.tempora.enums.LoggerEventType;
import com.colen.tempora.loggers.generic.ColumnDef;
import com.colen.tempora.loggers.generic.GenericPositionalLogger;
import com.colen.tempora.loggers.generic.GenericQueueElement;
import com.colen.tempora.utils.DatabaseUtils;
import com.colen.tempora.utils.PlayerUtils;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class CommandLogger extends GenericPositionalLogger<CommandQueueElement> {

    @Override
    public LoggerEnum getLoggerType() {
        return LoggerEnum.CommandLogger;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderEventsInWorld(RenderWorldLastEvent e) {
        RenderManager renderManager = RenderManager.instance;
        sortByDistanceDescending(transparentEventsToRenderInWorld, e);

        for (CommandQueueElement cqe : transparentEventsToRenderInWorld) {
            double x = cqe.x - renderManager.viewerPosX;
            double y = cqe.y - renderManager.viewerPosY + 1;
            double z = cqe.z - renderManager.viewerPosZ;

            List<String> toRender = new ArrayList<>();
            toRender.add(StatCollector.translateToLocalFormatted("event.command.executed", cqe.truePlayerName));
            toRender.add("/" + cqe.commandName + " " + cqe.arguments);

            toRender.add(new ChatComponentTimeRelative(cqe.timestamp).getFormattedText());

            renderFloatingText(toRender, x, y, z);
        }
    }

    @Override
    public List<ColumnDef> getCustomTableColumns() {
        return Arrays.asList(
            new ColumnDef("playerUUID", "TEXT", "NOT NULL DEFAULT " + MISSING_STRING_DATA),
            new ColumnDef("command", "TEXT", "NOT NULL DEFAULT " + MISSING_STRING_DATA),
            new ColumnDef("arguments", "TEXT", "NOT NULL DEFAULT " + MISSING_STRING_DATA));
    }

    @Override
    public @NotNull List<GenericQueueElement> generateQueryResults(ResultSet resultSet) throws SQLException {
        ArrayList<GenericQueueElement> eventList = new ArrayList<>();

        while (resultSet.next()) {
            CommandQueueElement queueElement = new CommandQueueElement();
            queueElement.populateDefaultFieldsFromResultSet(resultSet);

            queueElement.playerUUID = resultSet.getString("playerUUID");
            queueElement.commandName = resultSet.getString("command");
            queueElement.arguments = resultSet.getString("arguments");

            // Bit of a hack, but the client must have this info to render it properly.
            queueElement.truePlayerName = PlayerUtils.UUIDToName(queueElement.playerUUID);

            eventList.add(queueElement);
        }

        return eventList;
    }

    @Override
    public void threadedSaveEvents(List<CommandQueueElement> commandQueueElements) throws SQLException {
        if (commandQueueElements == null || commandQueueElements.isEmpty()) return;

        final String sql = "INSERT INTO " + getLoggerName()
            + " (playerUUID, command, arguments, eventID, x, y, z, dimensionID, timestamp, versionID) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        int index;
        try (PreparedStatement pstmt = db.getDBConn()
            .prepareStatement(sql)) {
            for (CommandQueueElement commandQueueElement : commandQueueElements) {
                index = 1;

                pstmt.setString(index++, commandQueueElement.playerUUID);
                pstmt.setString(index++, commandQueueElement.commandName);
                pstmt.setString(index++, commandQueueElement.arguments);

                DatabaseUtils.defaultColumnEntries(commandQueueElement, pstmt, index);
                pstmt.addBatch();
            }

            pstmt.executeBatch();
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

            CommandQueueElement queueElement = new CommandQueueElement();
            queueElement.eventID = UUID.randomUUID()
                .toString();
            queueElement.x = player.posX;
            queueElement.y = player.posY;
            queueElement.z = player.posZ;
            queueElement.dimensionId = player.dimension;
            queueElement.timestamp = System.currentTimeMillis();

            queueElement.playerUUID = player.getUniqueID()
                .toString();
            queueElement.commandName = command.getCommandName();
            queueElement.arguments = String.join(" ", args);

            queueEvent(queueElement);
        }
    }
}
