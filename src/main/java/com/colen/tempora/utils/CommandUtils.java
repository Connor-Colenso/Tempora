package com.colen.tempora.utils;

import java.util.List;
import java.util.Locale;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.TemporaLoggerManager;
import com.colen.tempora.commands.TemporaCommand;

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

    // Note this is not localised, because it may be called from terminal, which has no localisation functionality.
    public static final String ONLY_IN_GAME = "This command may only be executed by a player in-game.";

    public static IChatComponent playerOnly() {
        IChatComponent errorMessage = new ChatComponentText(ONLY_IN_GAME);
        errorMessage.getChatStyle()
            .setColor(EnumChatFormatting.RED);
        return errorMessage;
    }

    public static IChatComponent regionNotFound(String label) {
        IChatComponent errorMessage = new ChatComponentTranslation("tempora.region.not_found", label);
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

        // Shown as the block it is in, like the F3 screen, as that is all a lookup needs and it keeps lines short.
        IChatComponent display = new ChatComponentTranslation(
            "tempora.command.teleport.display",
            MathHelper.floor_double(x),
            MathHelper.floor_double(y),
            MathHelper.floor_double(z));

        IChatComponent hoverText = new ChatComponentTranslation("tempora.command.teleport.hover");
        hoverText.appendText("\n")
            .appendSibling(
                new ChatComponentTranslation(
                    "tempora.command.teleport.dimension",
                    GenericUtils.getDimensionName(dimID),
                    dimID));
        if (teleportType == TeleportType.EXACT) {
            hoverText.appendText("\n")
                .appendSibling(
                    new ChatComponentTranslation(
                        "tempora.command.teleport.exact",
                        String.format(Locale.ROOT, "%.2f, %.2f, %.2f", x, y, z)));
        }
        hoverText.getChatStyle()
            .setColor(EnumChatFormatting.GRAY);

        // Offset the teleport to put the player in the middle of the block.
        if (teleportType == TeleportType.BLOCK) {
            x += 0.5;
            y += 0.5;
            z += 0.5;
        }

        String cmd = TemporaCommand.TELEPORT.command(x, y, z, dimID);

        display.getChatStyle()
            .setColor(EnumChatFormatting.AQUA)
            .setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
            .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText));

        return display;
    }
}
