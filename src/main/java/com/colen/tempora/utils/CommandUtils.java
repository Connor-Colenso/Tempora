package com.colen.tempora.utils;

import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.TemporaLoggerManager;
import com.colen.tempora.commands.TemporaUndoCommand;
import com.gtnewhorizon.gtnhlib.chat.customcomponents.ChatComponentNumber;
import org.jetbrains.annotations.Nullable;

public class CommandUtils {

    public static final int OP_ONLY = 2;

    @NotNull
    public static List<String> completeLoggerNames(String[] args) {
        return CommandBase.getListOfStringsMatchingLastWord(
            args,
            TemporaLoggerManager.getAllLoggerNames()
                .toArray(new String[0]));
    }

    public static @Nullable String validateLoggerName(String input) {
        if (input == null) return null;

        for (String option : TemporaLoggerManager.getAllLoggerNames()) {
            if (option.equalsIgnoreCase(input)) {
                return option;
            }
        }
        return null;
    }

    public static void sendNewLine(ICommandSender sender) {
        sender.addChatMessage(new ChatComponentText(""));
    }

    public static IChatComponent wrongUsage(String commandUsage) {
        IChatComponent errorMessage = new ChatComponentTranslation("tempora.command.usage", commandUsage);
        errorMessage.getChatStyle()
            .setColor(EnumChatFormatting.RED);
        return errorMessage;
    }

    // Note this is not localised, because it may be called from terminal, which has no localisation functionality.
    public static final String ONLY_IN_GAME = "This command may only be executed by a player in-game.";

    public static IChatComponent playerOnly() {
        IChatComponent errorMessage = new ChatComponentText(ONLY_IN_GAME);
        errorMessage.getChatStyle()
            .setColor(EnumChatFormatting.RED);
        return errorMessage;
    }

    public static IChatComponent teleportChatComponent(double x, double y, double z, int dimID) {

        // Translation‑driven teleport options.
        IChatComponent display = new ChatComponentTranslation(
            "tempora.command.teleport.display",
            new ChatComponentNumber(x),
            new ChatComponentNumber(y),
            new ChatComponentNumber(z));

        String cmd = "/tempora_tp " + x + " " + y + " " + z + " " + dimID;

        IChatComponent hoverText = new ChatComponentTranslation(
            "tempora.command.teleport.hover",
            GenericUtils.getDimensionName(dimID),
            dimID);
        hoverText.getChatStyle()
            .setColor(EnumChatFormatting.GRAY);

        display.getChatStyle()
            .setColor(EnumChatFormatting.AQUA)
            .setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
            .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText));

        return display;
    }

    public static IChatComponent generateUndoCommand(String loggerName, String eventID) {

        // Translation‑driven teleport options.
        IChatComponent display = new ChatComponentTranslation("tempora.undo.query.display");

        String cmd = "/" + new TemporaUndoCommand().getCommandName() + " " + loggerName + " " + eventID;

        IChatComponent hoverText = new ChatComponentTranslation("tempora.undo.query.hover");
        hoverText.getChatStyle()
            .setColor(EnumChatFormatting.GRAY);

        display.getChatStyle()
            .setColor(EnumChatFormatting.AQUA)
            .setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
            .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText));

        return display;
    }

    public static IChatComponent tooFewArgs(int expected) {
        IChatComponent chatComponent = new ChatComponentTranslation("tempora.command.error.too.few.args", expected);
        chatComponent.getChatStyle().setColor(EnumChatFormatting.RED);
        return chatComponent;
    }

}
