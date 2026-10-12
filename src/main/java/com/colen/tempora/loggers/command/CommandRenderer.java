package com.colen.tempora.loggers.command;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.EnumChatFormatting;

import com.colen.tempora.rendering.events.EventRenderHelpers;
import com.colen.tempora.rendering.events.PlayerGhostRenderer;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Shows the player where they typed each command, with the commands typed there above them. */
@SideOnly(Side.CLIENT)
public class CommandRenderer extends PlayerGhostRenderer<CommandEventInfo> {

    // Longer commands are cut short, so one long command does not make a label wider than the screen.
    private static final int MAX_COMMAND_LENGTH = 64;

    public CommandRenderer() {
        super(Color.YELLOW);
    }

    @Override
    protected String playerUUID(CommandEventInfo event) {
        return event.playerUUID;
    }

    @Override
    protected List<String> label(List<CommandEventInfo> eventsHere) {
        List<String> lines = new ArrayList<>();
        for (CommandEventInfo event : eventsHere) {
            String command = "/" + event.commandName + (event.arguments.isEmpty() ? "" : " " + event.arguments);
            if (command.length() > MAX_COMMAND_LENGTH) command = command.substring(0, MAX_COMMAND_LENGTH - 3) + "...";

            lines.add(
                EnumChatFormatting.YELLOW + command
                    + " "
                    + EnumChatFormatting.GRAY
                    + EventRenderHelpers.timeAgo(event.timestamp));
        }
        return lines;
    }
}
