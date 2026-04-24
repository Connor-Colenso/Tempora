package com.colen.tempora.utils;

import static com.colen.tempora.commands.TemporaTp.TEMPORA_TP;

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
import com.colen.tempora.commands.TemporaUndo;
import com.gtnewhorizon.gtnhlib.chat.customcomponents.ChatComponentNumber;

public class CommandUtils {

    public static final int OP_ONLY = 2;

    @NotNull
    public static List<String> completeLoggerNames(String[] args) {
        return CommandBase.getListOfStringsMatchingLastWord(
            args,
            TemporaLoggerManager.getAllLoggerNames()
                .toArray(new String[0]));
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

    public enum TeleportType {
        BLOCK,
        EXACT
    }

    public static @NotNull IChatComponent teleportChatComponent(double x, double y, double z, final int dimID,
        final TeleportType teleportType) {

        // Translation‑driven teleport options.
        IChatComponent display = new ChatComponentTranslation(
            "tempora.command.teleport.display",
            new ChatComponentNumber(x),
            new ChatComponentNumber(y),
            new ChatComponentNumber(z));

        // Offset the teleport to put the player in the middle of the block, but show the
        // correct coordinate always (above logic).
        if (teleportType == TeleportType.BLOCK) {
            x += 0.5;
            y += 0.5;
            z += 0.5;
        }

        String cmd = "/" + TEMPORA_TP + " " + x + " " + y + " " + z + " " + dimID;

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

        String cmd = "/" + new TemporaUndo().getCommandName() + " " + loggerName + " " + eventID;

        IChatComponent hoverText = new ChatComponentTranslation("tempora.undo.query.hover");
        hoverText.getChatStyle()
            .setColor(EnumChatFormatting.GRAY);

        display.getChatStyle()
            .setColor(EnumChatFormatting.AQUA)
            .setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
            .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText));

        return display;
    }
}
