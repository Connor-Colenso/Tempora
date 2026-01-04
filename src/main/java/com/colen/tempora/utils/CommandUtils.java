package com.colen.tempora.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.colen.tempora.TemporaLoggerManager;
import net.minecraft.command.CommandBase;

import org.jetbrains.annotations.NotNull;

import com.colen.tempora.loggers.generic.GenericPositionalLogger;

public class CommandUtils {

    @NotNull
    public static List<String> completeLoggerNames(String[] args) {
        return CommandBase.getListOfStringsMatchingLastWord(
            args,
            TemporaLoggerManager.getAllLoggerNames().toArray(new String[0])
        );
    }

}
