package com.colen.tempora.utils;

import java.util.List;

import net.minecraft.command.CommandBase;

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

}
