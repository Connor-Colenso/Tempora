package com.colen.tempora.utils;

import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.TemporaLoggerManager;
import com.colen.tempora.chat.ChatComponentTeleport;
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

        IChatComponent hoverText = new ChatComponentTranslation("tempora.teleport.hover");
        hoverText.appendText("\n")
            .appendSibling(
                new ChatComponentTranslation(
                    "tempora.teleport.dimension",
                    GenericUtils.getDimensionName(dimID),
                    new ChatComponentNumber(dimID)));
        if (teleportType == TeleportType.EXACT) {
            hoverText.appendText("\n")
                .appendSibling(
                    new ChatComponentTranslation(
                        "tempora.teleport.exact",
                        new ChatComponentNumber(x),
                        new ChatComponentNumber(y),
                        new ChatComponentNumber(z)));
        }
        hoverText.getChatStyle()
            .setColor(EnumChatFormatting.GRAY);

        // Offset the teleport to put the player in the middle of the block. Still shown as that block, as the display
        // rounds down.
        if (teleportType == TeleportType.BLOCK) {
            x += 0.5;
            y += 0.5;
            z += 0.5;
        }

        IChatComponent display = new ChatComponentTeleport(x, y, z, dimID);
        display.getChatStyle()
            .setColor(EnumChatFormatting.AQUA)
            .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText));

        return display;
    }
}
