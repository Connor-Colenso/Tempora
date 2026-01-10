package com.colen.tempora.utils;

import java.util.List;

import net.minecraft.command.CommandBase;

import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import org.jetbrains.annotations.NotNull;

import com.colen.tempora.TemporaLoggerManager;


public class CommandUtils {

    @NotNull
    public static List<String> completeLoggerNames(String[] args) {
        return CommandBase.getListOfStringsMatchingLastWord(
            args,
            TemporaLoggerManager.getAllLoggerNames()
                .toArray(new String[0]));
    }

    public static IChatComponent wrongUsage(String commandUsage) {
        IChatComponent errorMessage = new ChatComponentTranslation("tempora.command.usage", commandUsage);
        errorMessage.getChatStyle().setColor(EnumChatFormatting.RED);
        return errorMessage;
    }

    // Note this is not localised, because it may be called from terminal, which has no localisation functionality.
    public static final String ONLY_IN_GAME = "This command may only be executed by a player in-game.";
    public static IChatComponent playerOnly() {
        IChatComponent errorMessage = new ChatComponentText(ONLY_IN_GAME);
        errorMessage.getChatStyle().setColor(EnumChatFormatting.RED);
        return errorMessage;
    }
}
